package com.example.discordlocation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Locations {
    public static class LocStamp {
        public int id;
        public int x;
        public int y;
        public int z;
        public String username;
        public String name;
        public String world;
        public String biome;
        public Timestamp created_at;

        public LocStamp(int id, int x, int y, int z, String username, String name, String world, String biome, Timestamp created_at) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.username = username;
            this.name = name;
            this.world = world;
            this.biome = biome;
            this.created_at = created_at;
        }
    }

    private static Connection conn;
    private DiscordLocationPlugin plugin;

    public Locations(DiscordLocationPlugin plugin) {
        this.plugin = plugin;
        try {
            // Connect to database in plugin's data folder
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/locations.db";
            SqliteDB.connect(dbPath);
            conn = SqliteDB.getConnection();
            createTable();
            plugin.getLogger().info("Database connected successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Locations couldn't connect to DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS locations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "x INTEGER NOT NULL," +
                "y INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "username TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "biome TEXT NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";
        SqliteDB.createNewTable(sql);
    }

    /**
     * Save a location to the database
     */
    public boolean saveLocation(String username, String name, int x, int y, int z, String world, String biome) {
        String sql = "INSERT INTO locations(username, name, x, y, z, world, biome) VALUES(?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, name);
            pstmt.setInt(3, x);
            pstmt.setInt(4, y);
            pstmt.setInt(5, z);
            pstmt.setString(6, world);
            pstmt.setString(7, biome);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving location: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get all locations for a specific username
     */
    public List<LocStamp> getLocationsByUsername(String username) {
        List<LocStamp> locations = new ArrayList<>();
        String sql = "SELECT * FROM locations WHERE username = ? ORDER BY created_at DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                locations.add(new LocStamp(
                        rs.getInt("id"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getString("biome"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving locations: " + e.getMessage());
            e.printStackTrace();
        }

        return locations;
    }

    /**
     * Delete a location by ID (only if it belongs to the username)
     */
    public boolean deleteLocation(int id, String username) {
        String sql = "DELETE FROM locations WHERE id = ? AND username = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting location: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get a specific location by ID and username
     */
    public LocStamp getLocation(int id, String username) {
        String sql = "SELECT * FROM locations WHERE id = ? AND username = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new LocStamp(
                        rs.getInt("id"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getString("biome"),
                        rs.getTimestamp("created_at")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving location: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Close database connection
     */
    public void close() {
        SqliteDB.closeConnection();
    }
}