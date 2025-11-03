package com.example.discordlocation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteDB {

    // Static variable to hold the single database connection
    private static Connection conn = null;
    private static String dbUrl = null;

    /**
     * Connects to or creates a new SQLite database file.
     * This connection is stored statically.
     * @param fileName The name of the database file (e.g., "my_database.db")
     */
    public static void connect(String fileName) {
        // If the connection already exists, don't do anything
        if (conn != null) {
            System.out.println("Database connection is already established.");
            return;
        }

        // The connection string for SQLite.
        // This will create the file if it does not exist.
        dbUrl = "jdbc:sqlite:" + fileName;

        try {
            // Create the static connection
            conn = DriverManager.getConnection(dbUrl);
            if (conn != null) {
                System.out.println("A new database has been created (or connected to) at: " + fileName);
            }
        } catch (SQLException e) {
            System.err.println("Error connecting to database: " + e.getMessage());
        }
    }

    /**
     * Gets the static database connection.
     * @return The static Connection object, or null if connect() has not been called.
     */
    public static Connection getConnection() {
        if (conn == null) {
            System.err.println("Database is not connected. Call connect(fileName) first.");
        }
        return conn;
    }

    /**
     * Closes the static database connection.
     * Should be called when the application is shutting down.
     */
    public static void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                conn = null; // Set to null after closing
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }

    /**
     * Creates a new table in the database using the static connection.
     * @param sql The SQL statement for creating the table (e.g., "CREATE TABLE IF NOT EXISTS users (...)")
     */
    public static void createNewTable(String sql) {
        // Get the static connection
        Connection connection = getConnection();

        if (connection == null) {
            System.err.println("Cannot create table: No database connection.");
            return;
        }

        // Use try-with-resources for the Statement, but not the Connection
        // as we want to keep the Connection open.
        try (Statement stmt = connection.createStatement()) {
            // create a new table
            stmt.execute(sql);
            System.out.println("Table has been created successfully.");

        } catch (SQLException e) {
            System.err.println("Error creating table: " + e.getMessage());
        }
    }
}
