package com.magenta.data;

import com.magenta.config.ConfigManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.File;

public class DatabaseService {
    private Connection agentConnection;
    private Connection taskConnection;

    public void init() throws SQLException {
        String storagePath = ConfigManager.get().baseStoragePath();
        File dir = new File(storagePath);
        if (!dir.exists()) { dir.mkdirs(); }

        agentConnection = DriverManager.getConnection("jdbc:sqlite:" + storagePath + "/agent.db");
        taskConnection = DriverManager.getConnection("jdbc:sqlite:" + storagePath + "/tasks.db");

        initAgentDb();
        initTaskDb();
    }

    private void initAgentDb() throws SQLException {
        try (Statement stmt = agentConnection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS kv_store (key TEXT PRIMARY KEY, value TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS memory_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER, data BLOB)");
        }
    }

    private void initTaskDb() throws SQLException {
        try (Statement stmt = taskConnection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (id TEXT PRIMARY KEY, description TEXT, parent_id TEXT, completed BOOLEAN, created_at INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER, action TEXT, details TEXT)");
        }
    }

    public Connection getAgentConnection() { return agentConnection; }

    public Connection getTaskConnection() { return taskConnection; }

    public void close() {
        try { if (agentConnection != null) agentConnection.close(); } catch (SQLException e) { }
        try { if (taskConnection != null) taskConnection.close(); } catch (SQLException e) { }
    }
}
