package com.magenta.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.session.SessionId;
import io.mindspice.sjbdc.SimplyJDBC;
import io.mindspice.sjbdc.SjColumn;
import io.mindspice.sjbdc.SjOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simplified SQLite database for context storage.
 * Just basic CRUD operations with JSON blobs.
 */
public final class Database implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private final Connection connection;
    private final SimplyJDBC db;
    private final ObjectMapper json;

    public Database(String dbPath) throws SQLException {
        this.db = new SimplyJDBC(SjOptions.builder().strictNamedParameters(true).build());
        this.json = new ObjectMapper();

        // Create database file
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) { parentDir.mkdirs(); }

        // Connect
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        // Enable foreign keys
        db.executeUpdate(connection, "PRAGMA foreign_keys = ON", SimplyJDBC.NO_PARAMS);

        // Agents table
        db.executeUpdate(connection, """
            CREATE TABLE IF NOT EXISTS agents (
                agent_name TEXT PRIMARY KEY,
                agent_alias TEXT NOT NULL,
                UNIQUE(agent_alias)
            )
        """, SimplyJDBC.NO_PARAMS);

        // Networks table
        db.executeUpdate(connection, """
            CREATE TABLE IF NOT EXISTS networks (
                network_id TEXT PRIMARY KEY,
                network_alias TEXT NOT NULL,
                UNIQUE(network_alias)
            )
        """, SimplyJDBC.NO_PARAMS);

        // Agent-Network link table (many-to-many)
        db.executeUpdate(connection, """
            CREATE TABLE IF NOT EXISTS agent_networks (
                agent_name TEXT NOT NULL,
                network_id TEXT NOT NULL,
                PRIMARY KEY (agent_name, network_id),
                FOREIGN KEY (agent_name) REFERENCES agents(agent_name) ON DELETE CASCADE,
                FOREIGN KEY (network_id) REFERENCES networks(network_id) ON DELETE CASCADE
            )
        """, SimplyJDBC.NO_PARAMS);

        // Sessions table (references network)
        db.executeUpdate(connection, """
            CREATE TABLE IF NOT EXISTS sessions (
                session_id TEXT PRIMARY KEY,
                network_id TEXT NOT NULL,
                session_alias TEXT NOT NULL,
                UNIQUE(session_alias),
                FOREIGN KEY (network_id) REFERENCES networks(network_id) ON DELETE CASCADE
            )
        """, SimplyJDBC.NO_PARAMS);

        // Context elements table (append-only log)
        // Note: No foreign key to sessions - contexts can exist independently
        db.executeUpdate(connection, """
            CREATE TABLE IF NOT EXISTS context_elements (
                session_id TEXT NOT NULL,
                sequence_num INTEGER NOT NULL,
                element_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (session_id, sequence_num)
            )
        """, SimplyJDBC.NO_PARAMS);

        // Index for efficient loading by session_id
        db.executeUpdate(connection, """
            CREATE INDEX IF NOT EXISTS idx_context_elements_session
            ON context_elements(session_id, sequence_num)
        """, SimplyJDBC.NO_PARAMS);

        logger.info("Database initialized at: {}", dbPath);
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

        for (ContextElement element : elements) {
            final int currentSequence = sequence;
            trySerialize(element).ifPresent(elementJson -> {
                db.executeUpdate(connection, """
                    INSERT INTO context_elements (session_id, sequence_num, element_json, created_at)
                    VALUES (:session_id, :sequence_num, :element_json, :created_at)
                    ON CONFLICT(session_id, sequence_num) DO UPDATE SET
                        element_json = excluded.element_json,
                        created_at = excluded.created_at
                """, Map.of(
                    "session_id", sessionId.toString(),
                    "sequence_num", currentSequence,
                    "element_json", elementJson,
                    "created_at", now
                )).onError(e -> logger.error("Failed to append element: {}", e.getMessage()));
            });
            sequence++;
        }

        logger.debug("Appended {} elements for session: {} (starting at sequence {})",
                     elements.size(), sessionId, startSequence);
    }

    /**
     * Load context by session ID.
     * Reconstructs context from all stored elements in sequence order.
     */
    public Optional<Context> load(SessionId sessionId) {
        List<ElementRecord> records = db.query(
            connection,
            "SELECT element_json FROM context_elements WHERE session_id = :session_id ORDER BY sequence_num ASC",
            Map.of("session_id", sessionId.toString()),
            ElementRecord.class
        ).onError(e -> logger.error("Failed to load context: {}", e.getMessage()))
         .rows();

        if (records.isEmpty()) {
            return Optional.empty();
        }

        // Reconstruct context from elements
        List<ContextElement> elements = new java.util.ArrayList<>();
        for (ElementRecord record : records) {
            tryDeserialize(record.elementJson(), ContextElement.class, "context element")
                .ifPresent(elements::add);
        }

        if (elements.isEmpty()) {
            logger.warn("Failed to deserialize any elements for session: {}", sessionId);
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
        db.executeUpdate(
            connection,
            "DELETE FROM sessions WHERE session_id = :session_id",
            Map.of("session_id", sessionId.toString())
        ).onError(e -> logger.error("Failed to delete session: {}", e.getMessage()));
        logger.debug("Deleted session and context: {}", sessionId);
    }

    /**
     * List all session IDs.
     */
    public List<SessionId> listSessions() {
        return db.query(
            connection,
            "SELECT session_id FROM sessions",
            SimplyJDBC.NO_PARAMS,
            SessionIdRecord.class
        ).onError(e -> logger.error("Failed to list sessions: {}", e.getMessage()))
         .rows()
         .stream()
         .map(r -> SessionId.of(r.sessionId()))
         .toList();
    }

    // === Agent Operations ===

    /**
     * Save (create or update) agent metadata.
     */
    public void saveAgent(String agentName, String agentAlias) {
        db.executeUpdate(connection, """
            INSERT INTO agents (agent_name, agent_alias)
            VALUES (:agent_name, :agent_alias)
            ON CONFLICT(agent_name) DO UPDATE SET
                agent_alias = excluded.agent_alias
        """, Map.of(
            "agent_name", agentName,
            "agent_alias", agentAlias
        )).onError(e -> logger.error("Failed to save agent: {}", e.getMessage()));
        logger.debug("Saved agent: {} ({})", agentAlias, agentName);
    }

    /**
     * Load agent metadata by agent name.
     */
    public Optional<AgentMetadata> loadAgent(String agentName) {
        List<AgentMetadata> records = db.query(
            connection,
            "SELECT agent_name, agent_alias FROM agents WHERE agent_name = :agent_name",
            Map.of("agent_name", agentName),
            AgentMetadata.class
        ).onError(e -> logger.error("Failed to load agent: {}", e.getMessage()))
         .rows();

        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    /**
     * Load agent metadata by alias.
     */
    public Optional<AgentMetadata> loadAgentByAlias(String alias) {
        List<AgentMetadata> records = db.query(
            connection,
            "SELECT agent_name, agent_alias FROM agents WHERE agent_alias = :alias",
            Map.of("alias", alias),
            AgentMetadata.class
        ).onError(e -> logger.error("Failed to load agent by alias: {}", e.getMessage()))
         .rows();

        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    /**
     * Delete agent (cascades to agent_networks link).
     */
    public void deleteAgent(String agentName) {
        db.executeUpdate(
            connection,
            "DELETE FROM agents WHERE agent_name = :agent_name",
            Map.of("agent_name", agentName)
        ).onError(e -> logger.error("Failed to delete agent: {}", e.getMessage()));
        logger.debug("Deleted agent: {}", agentName);
    }

    // === Network Operations ===

    /**
     * Save (create or update) network metadata.
     */
    public void saveNetwork(String networkId, String networkAlias) {
        db.executeUpdate(connection, """
            INSERT INTO networks (network_id, network_alias)
            VALUES (:network_id, :network_alias)
            ON CONFLICT(network_id) DO UPDATE SET
                network_alias = excluded.network_alias
        """, Map.of(
            "network_id", networkId,
            "network_alias", networkAlias
        )).onError(e -> logger.error("Failed to save network: {}", e.getMessage()));
        logger.debug("Saved network: {} ({})", networkAlias, networkId);
    }

    /**
     * Load network metadata by network ID.
     */
    public Optional<NetworkMetadata> loadNetwork(String networkId) {
        List<NetworkMetadata> records = db.query(
            connection,
            "SELECT network_id, network_alias FROM networks WHERE network_id = :network_id",
            Map.of("network_id", networkId),
            NetworkMetadata.class
        ).onError(e -> logger.error("Failed to load network: {}", e.getMessage()))
         .rows();

        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    /**
     * Load network metadata by alias.
     */
    public Optional<NetworkMetadata> loadNetworkByAlias(String alias) {
        List<NetworkMetadata> records = db.query(
            connection,
            "SELECT network_id, network_alias FROM networks WHERE network_alias = :alias",
            Map.of("alias", alias),
            NetworkMetadata.class
        ).onError(e -> logger.error("Failed to load network by alias: {}", e.getMessage()))
         .rows();

        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    /**
     * Delete network (cascades to agent_networks, sessions, and contexts).
     */
    public void deleteNetwork(String networkId) {
        db.executeUpdate(
            connection,
            "DELETE FROM networks WHERE network_id = :network_id",
            Map.of("network_id", networkId)
        ).onError(e -> logger.error("Failed to delete network: {}", e.getMessage()));
        logger.debug("Deleted network: {}", networkId);
    }

    // === Agent-Network Link Operations ===

    /**
     * Link an agent to a network.
     */
    public void linkAgentToNetwork(String agentName, String networkId) {
        db.executeUpdate(connection, """
            INSERT INTO agent_networks (agent_name, network_id)
            VALUES (:agent_name, :network_id)
            ON CONFLICT DO NOTHING
        """, Map.of(
            "agent_name", agentName,
            "network_id", networkId
        )).onError(e -> logger.error("Failed to link agent to network: {}", e.getMessage()));
        logger.debug("Linked agent {} to network {}", agentName, networkId);
    }

    /**
     * Unlink an agent from a network.
     */
    public void unlinkAgentFromNetwork(String agentName, String networkId) {
        db.executeUpdate(connection, """
            DELETE FROM agent_networks
            WHERE agent_name = :agent_name AND network_id = :network_id
        """, Map.of(
            "agent_name", agentName,
            "network_id", networkId
        )).onError(e -> logger.error("Failed to unlink agent from network: {}", e.getMessage()));
        logger.debug("Unlinked agent {} from network {}", agentName, networkId);
    }

    /**
     * Get all networks for an agent.
     */
    public List<NetworkMetadata> getNetworksForAgent(String agentName) {
        return db.query(connection, """
            SELECT n.network_id, n.network_alias
            FROM networks n
            JOIN agent_networks an ON n.network_id = an.network_id
            WHERE an.agent_name = :agent_name
        """, Map.of("agent_name", agentName), NetworkMetadata.class)
            .onError(e -> logger.error("Failed to get networks for agent: {}", e.getMessage()))
            .rows();
    }

    /**
     * Get all agents for a network.
     */
    public List<AgentMetadata> getAgentsForNetwork(String networkId) {
        return db.query(connection, """
            SELECT a.agent_name, a.agent_alias
            FROM agents a
            JOIN agent_networks an ON a.agent_name = an.agent_name
            WHERE an.network_id = :network_id
        """, Map.of("network_id", networkId), AgentMetadata.class)
            .onError(e -> logger.error("Failed to get agents for network: {}", e.getMessage()))
            .rows();
    }

    // === Session Operations ===

    /**
     * Save (create or update) session metadata.
     */
    public void saveSession(SessionId sessionId, String networkId, String sessionAlias) {
        db.executeUpdate(connection, """
            INSERT INTO sessions (session_id, network_id, session_alias)
            VALUES (:session_id, :network_id, :session_alias)
            ON CONFLICT(session_id) DO UPDATE SET
                network_id = excluded.network_id,
                session_alias = excluded.session_alias
        """, Map.of(
            "session_id", sessionId.toString(),
            "network_id", networkId,
            "session_alias", sessionAlias
        )).onError(e -> logger.error("Failed to save session: {}", e.getMessage()));
        logger.debug("Saved session: {} ({})", sessionAlias, sessionId);
    }

    /**
     * Load session metadata by session ID.
     */
    public Optional<SessionMetadata> loadSession(SessionId sessionId) {
        List<SessionMetadata> records = db.query(
            connection,
            "SELECT session_id, network_id, session_alias FROM sessions WHERE session_id = :session_id",
            Map.of("session_id", sessionId.toString()),
            SessionMetadata.class
        ).onError(e -> logger.error("Failed to load session: {}", e.getMessage()))
         .rows();

        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    /**
     * Load session metadata by alias.
     */
    public Optional<SessionMetadata> loadSessionByAlias(String alias) {
        List<SessionMetadata> records = db.query(
            connection,
            "SELECT session_id, network_id, session_alias FROM sessions WHERE session_alias = :alias",
            Map.of("alias", alias),
            SessionMetadata.class
        ).onError(e -> logger.error("Failed to load session by alias: {}", e.getMessage()))
         .rows();

        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            logger.info("Database closed");
        }
    }

    // === Records ===

    private record ElementRecord(@SjColumn("element_json") String elementJson) {}
    private record SessionIdRecord(@SjColumn("session_id") String sessionId) {}

    public record AgentMetadata(
        @SjColumn("agent_name") String agentName,
        @SjColumn("agent_alias") String agentAlias
    ) {}

    public record NetworkMetadata(
        @SjColumn("network_id") String networkId,
        @SjColumn("network_alias") String networkAlias
    ) {}

    public record SessionMetadata(
        @SjColumn("session_id") String sessionId,
        @SjColumn("network_id") String networkId,
        @SjColumn("session_alias") String sessionAlias
    ) {}
}
