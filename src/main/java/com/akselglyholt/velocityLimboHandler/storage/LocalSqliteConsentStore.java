package com.akselglyholt.velocityLimboHandler.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

public class LocalSqliteConsentStore implements ConsentStore {
    private final Logger logger;
    private final Connection connection;
    private final Object lock = new Object();

    public LocalSqliteConsentStore(Path filePath, Logger logger) throws SQLException {
        this.logger = logger;
        try {
            Files.createDirectories(filePath.getParent());
            if (Files.notExists(filePath)) {
                Files.createFile(filePath);
            }
        } catch (Exception e) {
            throw new SQLException("Failed to create storage directory: " + e.getMessage(), e);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found: " + e.getMessage(), e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + filePath.toAbsolutePath());
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
        initSchema();
    }

    private void initSchema() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS consented (player_id TEXT PRIMARY KEY)";
        synchronized (lock) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    @Override
    public boolean hasConsent(UUID playerId) {
        String sql = "SELECT 1 FROM consented WHERE player_id = ? LIMIT 1";
        synchronized (lock) {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.warning("Failed to read consent from sqlite: " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public void setConsent(UUID playerId, boolean consented) {
        synchronized (lock) {
            if (consented) {
                String sql = "INSERT OR IGNORE INTO consented(player_id) VALUES (?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logger.warning("Failed to write consent to sqlite: " + e.getMessage());
                }
            } else {
                String sql = "DELETE FROM consented WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logger.warning("Failed to remove consent from sqlite: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warning("Failed to close sqlite connection: " + e.getMessage());
            }
        }
    }
}
