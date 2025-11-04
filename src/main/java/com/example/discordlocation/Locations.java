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
                "id INTEGER NOT NULL," +
                "x INTEGER NOT NULL," +
                "y INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "username TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "biome TEXT NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (id, username)" +
                ");";
        SqliteDB.createNewTable(sql);
    }

    /**
     * Save a location to the database
     */
    public boolean saveLocation(String username, String name, int x, int y, int z, String world, String biome) {
        String sql = "INSERT INTO locations(id, username, name, x, y, z, world, biome) VALUES(?,?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, getNextId(username));
            pstmt.setString(2, username);
            pstmt.setString(3, name);
            pstmt.setInt(4, x);
            pstmt.setInt(5, y);
            pstmt.setInt(6, z);
            pstmt.setString(7, world);
            pstmt.setString(8, biome);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving location: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private int getNextId(String username) {
        String sql = "SELECT MAX(id) FROM locations WHERE username = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting next id: " + e.getMessage());
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * Get all locations for a specific username
     */
    public List<LocStamp> getLocationsByUsername(String username) {
        List<LocStamp> locations = new ArrayList<>();
        String sql = "SELECT * FROM locations WHERE username = ? ORDER BY id ASC";

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
            if (rowsAffected > 0) {
                rearrangeIds(username);
                return true;
            }
            return false;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting location: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void rearrangeIds(String username) {
        List<LocStamp> locations = getLocationsByUsername(username);
        String sql = "UPDATE locations SET id = ? WHERE id = ? AND username = ?";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < locations.size(); i++) {
                    if (locations.get(i).id != i + 1) {
                        pstmt.setInt(1, i + 1);
                        pstmt.setInt(2, locations.get(i).id);
                        pstmt.setString(3, username);
                        pstmt.addBatch();
                    }
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Error rearranging ids: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error starting transaction: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error setting auto-commit: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Get a specific location by ID or name and username
     */
    public LocStamp getLocation(String identifier, String username) {
        String sql;
        boolean isId = false;
        int locationId = -1;

        try {
            locationId = Integer.parseInt(identifier);
            isId = true;
        } catch (NumberFormatException e) {
            // Identifier is a name
        }

        if (isId) {
            sql = "SELECT * FROM locations WHERE id = ? AND username = ?";
        } else {
            sql = "SELECT * FROM locations WHERE name = ? AND username = ?";
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (isId) {
                pstmt.setInt(1, locationId);
            } else {
                pstmt.setString(1, identifier);
            }
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
     * Rename a location by ID or name
     */
    public boolean renameLocation(String username, String identifier, String newName) {
        String sql;
        boolean isId = false;
        int locationId = -1;

        try {
            locationId = Integer.parseInt(identifier);
            isId = true;
        } catch (NumberFormatException e) {
            // Identifier is a name
        }

        if (isId) {
            sql = "UPDATE locations SET name = ? WHERE id = ? AND username = ?";
        } else {
            sql = "UPDATE locations SET name = ? WHERE name = ? AND username = ?";
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            if (isId) {
                pstmt.setInt(2, locationId);
            } else {
                pstmt.setString(2, identifier);
            }
            pstmt.setString(3, username);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error renaming location: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Close database connection
     */
    public void close() {
        SqliteDB.closeConnection();
    }
}