package com.example.discordlocation;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordLocationPlugin extends JavaPlugin {

    private String webhookUrl;
    private String webhookName;
    private String webhookAvatarUrl;
    private int embedColor;
    private String embedTitle;
    private String embedFooter;
    private String embedThumbnail;

    @Override
    public void onEnable() {
        // Create plugin folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Save default config if it doesn't exist
        saveDefaultConfig();

        // Load configuration values
        webhookUrl = getConfig().getString("webhook-url", "");
        webhookName = getConfig().getString("webhook-name", "Minecraft Location Bot");
        webhookAvatarUrl = getConfig().getString("webhook-avatar-url", "");

        // Load embed customization
        String embedColorHex = getConfig().getString("embed.color", "#58A5F0");
        embedColor = hexToDecimal(embedColorHex);
        embedTitle = getConfig().getString("embed.title", "📍 Player Location");
        embedFooter = getConfig().getString("embed.footer", "");
        embedThumbnail = getConfig().getString("embed.thumbnail", "");

        if (webhookUrl.isEmpty()) {
            getLogger().warning("No webhook URL configured! Please set it in config.yml");
        }

        getLogger().info("DiscordLocationPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DiscordLocationPlugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sendlocation")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            Player player = (Player) sender;

            // Check permission nodes , defaults to op if not assigned
            if (!player.hasPermission("discordlocation.send")) {
                player.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            if (webhookUrl.isEmpty()) {
                player.sendMessage("§cWebhook URL is not configured!");
                return true;
            }

            // Get player location and biome
            Location loc = player.getLocation();
            String biome = loc.getBlock().getBiome().toString();

            // Format coordinates
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            String world = loc.getWorld().getName();

            // Send to Discord in async to avoid blocking server
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    sendToDiscord(player.getName(), world, x, y, z, biome);
                    getServer().getScheduler().runTask(this, () ->
                            player.sendMessage("§aLocation sent to Discord!")
                    );
                } catch (Exception e) {
                    getLogger().severe("Failed to send webhook: " + e.getMessage());
                    e.printStackTrace();
                    getServer().getScheduler().runTask(this, () ->
                            player.sendMessage("§cFailed to send location to Discord!")
                    );
                }
            });

            return true;
        }
        return false;
    }

    private void sendToDiscord(String playerName, String world, int x, int y, int z, String biome) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // optional username and avatar_url
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");

        // Add webhook customization if provided
        if (!webhookName.isEmpty()) {
            jsonBuilder.append("\"username\": \"").append(escapeJson(webhookName)).append("\",");
        }
        if (!webhookAvatarUrl.isEmpty()) {
            jsonBuilder.append("\"avatar_url\": \"").append(escapeJson(webhookAvatarUrl)).append("\",");
        }

        // Add embed
        jsonBuilder.append("\"embeds\": [{");
        jsonBuilder.append("\"title\": \"").append(escapeJson(embedTitle)).append("\",");
        jsonBuilder.append("\"color\": ").append(embedColor).append(",");

        // Add thumbnail if given
        if (!embedThumbnail.isEmpty()) {
            jsonBuilder.append("\"thumbnail\": {\"url\": \"").append(escapeJson(embedThumbnail)).append("\"},");
        }

        jsonBuilder.append("\"fields\": [");
        jsonBuilder.append("{\"name\": \"Player\", \"value\": \"").append(escapeJson(playerName)).append("\", \"inline\": true},");
        jsonBuilder.append("{\"name\": \"World\", \"value\": \"").append(escapeJson(world)).append("\", \"inline\": true},");
        jsonBuilder.append("{\"name\": \"Coordinates\", \"value\": \"X: ").append(x).append("\\nY: ").append(y).append("\\nZ: ").append(z).append("\", \"inline\": false},");
        jsonBuilder.append("{\"name\": \"Biome\", \"value\": \"").append(escapeJson(biome)).append("\", \"inline\": false}");
        jsonBuilder.append("],");

        // Add footer and feet WHAAA
        if (!embedFooter.isEmpty()) {
            jsonBuilder.append("\"footer\": {\"text\": \"").append(escapeJson(embedFooter)).append("\"},");
        }

        jsonBuilder.append("\"timestamp\": \"").append(java.time.Instant.now().toString()).append("\"");
        jsonBuilder.append("}]}");

        String json = jsonBuilder.toString();

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 204 && responseCode != 200) {
            throw new Exception("Discord webhook returned code: " + responseCode);
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private int hexToDecimal(String hex) {
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            // Convert hex to decimal because decimal gay
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid hex color format: " + hex + ". Using default color.");
            return 5814783; // Default blue ishh? ig
        }
    }
}