package dev.qilletni.lib.spotify.database;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DriverManager;
import java.sql.SQLException;

public class HibernateUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(HibernateUtil.class);

    private static SessionFactory sessionFactory;

    public static void initializeSessionFactory(String url, String username, String password) {
        if (sessionFactory != null) {
            return;
        }

        try {
            sessionFactory = new Configuration()
                    .configure()
                    .setProperty("hibernate.connection.url", url)
                    .setProperty("hibernate.connection.username", username)
                    .setProperty("hibernate.connection.password", password)
                    .buildSessionFactory();
        } catch (Throwable ex) {
            LOGGER.error("Initial SessionFactory creation failed", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static boolean createDatabaseIfNotExists(String url, String username, String password) {
        // Extract database name from JDBC URL (e.g., jdbc:postgresql://localhost:5432/qilletni_spotify)
        var databaseName = url.substring(url.lastIndexOf('/') + 1);

        // Remove any query parameters
        int queryParamIndex = databaseName.indexOf('?');
        if (queryParamIndex != -1) {
            databaseName = databaseName.substring(0, queryParamIndex);
        }

        // Connect to postgres database to check/create target database
        var postgresUrl = url.substring(0, url.lastIndexOf('/') + 1) + "postgres";
        if (queryParamIndex != -1) {
            postgresUrl += url.substring(url.indexOf('?'));
        }

        try (var conn = DriverManager.getConnection(postgresUrl, username, password);
             var stmt = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?;")) {

            stmt.setString(1, databaseName);

            // Check if database exists
            var rs = stmt.executeQuery();

            if (!rs.next()) {
                // Database doesn't exist, create it
                LOGGER.info("Database '{}' does not exist. Creating it...", databaseName);

                // Validate database name to prevent SQL injection
                if (!databaseName.matches("^[a-zA-Z0-9_]+$")) {
                    throw new SQLException("Invalid database name: " + databaseName);
                }

                // Use regular statement for DDL - cannot use prepared statement for identifiers
                try (var createStmt = conn.createStatement()) {
                    createStmt.executeUpdate("CREATE DATABASE \"" + databaseName + "\";");
                }
                LOGGER.info("Database '{}' created successfully", databaseName);
            } else {
                LOGGER.debug("Database '{}' already exists", databaseName);
            }

            return true;
        } catch (SQLException e) {
            LOGGER.error("Could not create database '{}': {}", databaseName, e.getMessage(), e);
            return false;
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}
