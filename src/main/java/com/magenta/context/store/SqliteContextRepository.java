package com.magenta.context.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;
import com.magenta.data.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SqliteContextRepository implements ContextRepository {
    private final DatabaseService db;
    private final ObjectMapper mapper;

    public SqliteContextRepository(DatabaseService db) {
        this.db = db;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            ensureTable();
        } catch (SQLException e) {
            e.printStackTrace(); // Simple logging
        }
    }

    private void ensureTable() throws SQLException {
        Connection conn = db.getAgentConnection();
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS context_store (key TEXT PRIMARY KEY, data TEXT)");
        }
    }

    @Override
    public void save(String key, Context context) {
        try {
            String data = mapper.writeValueAsString(context);
            try (Connection conn = db.getAgentConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO context_store (key, data) VALUES (?, ?)")) {
                ps.setString(1, key);
                ps.setString(2, data);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Optional<Context> load(String key) {
        try (Connection conn = db.getAgentConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT data FROM context_store WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String data = rs.getString("data");
                    return Optional.of(mapper.readValue(data, Context.class));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public void delete(String key) {
        try (Connection conn = db.getAgentConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM context_store WHERE key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
