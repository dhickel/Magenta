package com.magenta.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.session.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simplified SQLite database for context storage.
 * Just basic CRUD operations with JSON blobs.
 */
public final class Database implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private final Connection connection;
    private final ObjectMapper json;

    public Database(String dbPath) throws SQLException {
        this.json = new ObjectMapper();

        // Create database file
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) { parentDir.mkdirs(); }

        // Connect
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        // Enable foreign keys
        try (PreparedStatement stmt = connection.prepareStatement("PRAGMA foreign_keys = ON")) {
            stmt.execute();
        }

        // Agents table
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS agents (
                agent_name TEXT PRIMARY KEY,
                agent_alias TEXT NOT NULL,
                UNIQUE(agent_alias)
            )
        """);

        // Networks table
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS networks (
                network_id TEXT PRIMARY KEY,
                network_alias TEXT NOT NULL,
                UNIQUE(network_alias)
            )
        """);

        // Agent-Network link table (many-to-many)
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS agent_networks (
                agent_name TEXT NOT NULL,
                network_id TEXT NOT NULL,
                PRIMARY KEY (agent_name, network_id),
                FOREIGN KEY (agent_name) REFERENCES agents(agent_name) ON DELETE CASCADE,
                FOREIGN KEY (network_id) REFERENCES networks(network_id) ON DELETE CASCADE
            )
        """);

        // Sessions table (references network)
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS sessions (
                session_id TEXT PRIMARY KEY,
                network_id TEXT NOT NULL,
                session_alias TEXT NOT NULL,
                UNIQUE(session_alias),
                FOREIGN KEY (network_id) REFERENCES networks(network_id) ON DELETE CASCADE
            )
        """);

        // Context elements table (append-only log)
        // Note: No foreign key to sessions - contexts can exist independently
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS context_elements (
                session_id TEXT NOT NULL,
                sequence_num INTEGER NOT NULL,
                element_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (session_id, sequence_num)
            )
        """);

        // Index for efficient loading by session_id
        executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_context_elements_session
            ON context_elements(session_id, sequence_num)
        """);

        logger.info("Database initialized at: {}", dbPath);
    }

    /**
     * Execute update statement without parameters.
     */
    private void executeUpdate(String sql) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to execute update: {}", e.getMessage());
        }
    }

    // === Helper Methods ===

    /**
     * Try to deserialize JSON, return Optional.empty() on error.
     */
    private <T> Optional<T> tryDeserialize(String jsonString, Class<T> type, String errorContext) {
        try {
            return Optional.of(json.readValue(jsonString, type));
        } catch (Exception e) {
            logger.error("Failed to deserialize {}: {}", errorContext, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Try to serialize object to JSON, return Optional.empty() on error.
     */
    private Optional<String> trySerialize(Object obj) {
        try {
            return Optional.of(json.writeValueAsString(obj));
        } catch (Exception e) {
            logger.error("Failed to serialize {}: {}", obj.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    // === CRUD Operations ===

    /**
     * Append new elements to context (append-only log).
     * Elements are stored individually with sequence numbers.
     *
     * @param sessionId Session ID
     * @param elements New elements to append
     * @param startSequence Sequence number for first element (0-based)
     */
    public void appendElements(SessionId sessionId, List<ContextElement> elements, int startSequence) {
        if (elements.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int sequence = startSequence;

        String sql = """
            INSERT INTO context_elements (session_id, sequence_num, element_json, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(session_id, sequence_num) DO UPDATE SET
                element_json = excluded.element_json,
                created_at = excluded.created_at
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (ContextElement element : elements) {
                Optional<String> elementJson = trySerialize(element);
                if (elementJson.isPresent()) {
                    stmt.setString(1, sessionId.toString());
                    stmt.setInt(2, sequence);
                    stmt.setString(3, elementJson.get());
                    stmt.setLong(4, now);
                    stmt.executeUpdate();
                }
                sequence++;
            }
        } catch (SQLException e) {
            logger.error("Failed to append elements: {}", e.getMessage());
        }

        logger.debug("Appended {} elements for session: {} (starting at sequence {})",
                     elements.size(), sessionId, startSequence);
    }

    /**
     * Load context by session ID.
     * Reconstructs context from all stored elements in sequence order.
     */
    public Optional<Context> load(SessionId sessionId) {
        String sql = "SELECT element_json FROM context_elements WHERE session_id = ? ORDER BY sequence_num ASC";

        List<ContextElement> elements = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String elementJson = rs.getString("element_json");
                    tryDeserialize(elementJson, ContextElement.class, "context element")
                        .ifPresent(elements::add);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load context: {}", e.getMessage());
            return Optional.empty();
        }

        if (elements.isEmpty()) {
            return Optional.empty();
        }

        Context context = new Context(sessionId, elements);
        logger.debug("Loaded context for session: {} ({} elements)", sessionId, elements.size());
        return Optional.of(context);
    }

    /**
     * Delete session and context (cascades from sessions to contexts).
     */
    public void delete(SessionId sessionId) {
        String sql = "DELETE FROM sessions WHERE session_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete session: {}", e.getMessage());
        }

        logger.debug("Deleted session and context: {}", sessionId);
    }

    /**
     * List all session IDs.
     */
    public List<SessionId> listSessions() {
        String sql = "SELECT session_id FROM sessions";
        List<SessionId> sessions = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                sessions.add(SessionId.of(rs.getString("session_id")));
            }
        } catch (SQLException e) {
            logger.error("Failed to list sessions: {}", e.getMessage());
        }

        return sessions;
    }

    // === Agent Operations ===

    /**
     * Save (create or update) agent metadata.
     */
    public void saveAgent(String agentName, String agentAlias) {
        String sql = """
            INSERT INTO agents (agent_name, agent_alias)
            VALUES (?, ?)
            ON CONFLICT(agent_name) DO UPDATE SET
                agent_alias = excluded.agent_alias
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, agentName);
            stmt.setString(2, agentAlias);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save agent: {}", e.getMessage());
        }

        logger.debug("Saved agent: {} ({})", agentAlias, agentName);
    }

    /**
     * Load agent metadata by agent name.
     */
    public Optional<AgentMetadata> loadAgent(String agentName) {
        String sql = "SELECT agent_name, agent_alias FROM agents WHERE agent_name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, agentName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new AgentMetadata(
                        rs.getString("agent_name"),
                        rs.getString("agent_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load agent: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Load agent metadata by alias.
     */
    public Optional<AgentMetadata> loadAgentByAlias(String alias) {
        String sql = "SELECT agent_name, agent_alias FROM agents WHERE agent_alias = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, alias);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new AgentMetadata(
                        rs.getString("agent_name"),
                        rs.getString("agent_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load agent by alias: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Delete agent (cascades to agent_networks link).
     */
    public void deleteAgent(String agentName) {
        String sql = "DELETE FROM agents WHERE agent_name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, agentName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete agent: {}", e.getMessage());
        }

        logger.debug("Deleted agent: {}", agentName);
    }

    // === Network Operations ===

    /**
     * Save (create or update) network metadata.
     */
    public void saveNetwork(String networkId, String networkAlias) {
        String sql = """
            INSERT INTO networks (network_id, network_alias)
            VALUES (?, ?)
            ON CONFLICT(network_id) DO UPDATE SET
                network_alias = excluded.network_alias
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, networkId);
            stmt.setString(2, networkAlias);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save network: {}", e.getMessage());
        }

        logger.debug("Saved network: {} ({})", networkAlias, networkId);
    }

    /**
     * Load network metadata by network ID.
     */
    public Optional<NetworkMetadata> loadNetwork(String networkId) {
        String sql = "SELECT network_id, network_alias FROM networks WHERE network_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, networkId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new NetworkMetadata(
                        rs.getString("network_id"),
                        rs.getString("network_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load network: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Load network metadata by alias.
     */
    public Optional<NetworkMetadata> loadNetworkByAlias(String alias) {
        String sql = "SELECT network_id, network_alias FROM networks WHERE network_alias = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, alias);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new NetworkMetadata(
                        rs.getString("network_id"),
                        rs.getString("network_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load network by alias: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Delete network (cascades to agent_networks, sessions, and contexts).
     */
    public void deleteNetwork(String networkId) {
        String sql = "DELETE FROM networks WHERE network_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, networkId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete network: {}", e.getMessage());
        }

        logger.debug("Deleted network: {}", networkId);
    }

    // === Agent-Network Link Operations ===

    /**
     * Link an agent to a network.
     */
    public void linkAgentToNetwork(String agentName, String networkId) {
        String sql = """
            INSERT INTO agent_networks (agent_name, network_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, agentName);
            stmt.setString(2, networkId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to link agent to network: {}", e.getMessage());
        }

        logger.debug("Linked agent {} to network {}", agentName, networkId);
    }

    /**
     * Unlink an agent from a network.
     */
    public void unlinkAgentFromNetwork(String agentName, String networkId) {
        String sql = """
            DELETE FROM agent_networks
            WHERE agent_name = ? AND network_id = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, agentName);
            stmt.setString(2, networkId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to unlink agent from network: {}", e.getMessage());
        }

        logger.debug("Unlinked agent {} from network {}", agentName, networkId);
    }

    /**
     * Get all networks for an agent.
     */
    public List<NetworkMetadata> getNetworksForAgent(String agentName) {
        String sql = """
            SELECT n.network_id, n.network_alias
            FROM networks n
            JOIN agent_networks an ON n.network_id = an.network_id
            WHERE an.agent_name = ?
        """;

        List<NetworkMetadata> networks = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, agentName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    networks.add(new NetworkMetadata(
                        rs.getString("network_id"),
                        rs.getString("network_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get networks for agent: {}", e.getMessage());
        }

        return networks;
    }

    /**
     * Get all agents for a network.
     */
    public List<AgentMetadata> getAgentsForNetwork(String networkId) {
        String sql = """
            SELECT a.agent_name, a.agent_alias
            FROM agents a
            JOIN agent_networks an ON a.agent_name = an.agent_name
            WHERE an.network_id = ?
        """;

        List<AgentMetadata> agents = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, networkId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    agents.add(new AgentMetadata(
                        rs.getString("agent_name"),
                        rs.getString("agent_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get agents for network: {}", e.getMessage());
        }

        return agents;
    }

    // === Session Operations ===

    /**
     * Save (create or update) session metadata.
     */
    public void saveSession(SessionId sessionId, String networkId, String sessionAlias) {
        String sql = """
            INSERT INTO sessions (session_id, network_id, session_alias)
            VALUES (?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
                network_id = excluded.network_id,
                session_alias = excluded.session_alias
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId.toString());
            stmt.setString(2, networkId);
            stmt.setString(3, sessionAlias);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save session: {}", e.getMessage());
        }

        logger.debug("Saved session: {} ({})", sessionAlias, sessionId);
    }

    /**
     * Load session metadata by session ID.
     */
    public Optional<SessionMetadata> loadSession(SessionId sessionId) {
        String sql = "SELECT session_id, network_id, session_alias FROM sessions WHERE session_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new SessionMetadata(
                        rs.getString("session_id"),
                        rs.getString("network_id"),
                        rs.getString("session_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load session: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Load session metadata by alias.
     */
    public Optional<SessionMetadata> loadSessionByAlias(String alias) {
        String sql = "SELECT session_id, network_id, session_alias FROM sessions WHERE session_alias = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, alias);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new SessionMetadata(
                        rs.getString("session_id"),
                        rs.getString("network_id"),
                        rs.getString("session_alias")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load session by alias: {}", e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            logger.info("Database closed");
        }
    }

    // === Records ===

    public record AgentMetadata(
        String agentName,
        String agentAlias
    ) {}

    public record NetworkMetadata(
        String networkId,
        String networkAlias
    ) {}

    public record SessionMetadata(
        String sessionId,
        String networkId,
        String sessionAlias
    ) {}
}
