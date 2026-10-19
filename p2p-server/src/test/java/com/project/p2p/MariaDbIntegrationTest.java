package com.project.p2p;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class MariaDbIntegrationTest {

    @Test
    void testMariaDbConnectionAndCatalog() {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String dbName = System.getenv().getOrDefault("DB_NAME", "test");
        String user = System.getenv().getOrDefault("DB_USERNAME", "root");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "1234");

        String jdbcUrl = String.format("jdbc:mariadb://%s:%s/%s?createDatabaseIfNotExist=true", host, port, dbName);

        try {
            Class.forName("org.mariadb.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT DATABASE(), VERSION()")) {

                assertTrue(rs.next(), "Database query should return at least one row");
                String currentDb = rs.getString(1);
                String dbVersion = rs.getString(2);

                System.out.println("=== MariaDB Connection Verified ===");
                System.out.println("Connected to Database: " + currentDb);
                System.out.println("MariaDB Version: " + dbVersion);

                assertEquals("test", currentDb, "Connected database MUST be named 'test'");
            }
        } catch (Exception e) {
            fail("Failed to connect to MariaDB 'test' database: " + e.getMessage(), e);
        }
    }
}
