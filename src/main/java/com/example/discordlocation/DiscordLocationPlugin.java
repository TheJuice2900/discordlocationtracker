package com.example.discordlocation;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DiscordLocationPlugin extends JavaPlugin {

    private String webhookUrl;
    private String webhookName;
    private String webhookAvatarUrl;
    private int embedColor;
    private String embedTitle;
    private String embedFooter;
    private String embedThumbnail;
    private boolean webhookConfigured;
    public static DiscordLocationPlugin instance;
    private Locations locationsDB;

    // Store pending location data for Discord confirmation
    private Map<UUID, PendingLocation> pendingLocations = new HashMap<>();

    // Inner class to store location data
    private class PendingLocation {
        String playerName;
        String world;
        int x, y, z;
        String biome;
        String note;
        long timestamp;

        PendingLocation(String playerName, String world, int x, int y, int z, String biome, String note) {
            this.playerName = playerName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.biome = biome;
            this.note = note;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        // Create plugin folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Save default config if it doesn't exist
        saveDefaultConfig();

        // Load configuration
        loadConfig();

        // Initialize database
        locationsDB = new Locations(this);

        if (!webhookConfigured) {
            getLogger().warning("No webhook URL configured! Discord integration disabled. Locations will still be saved to database.");
        }

        // Start cleanup task for expired pending locations (after 5 minutes)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            long currentTime = System.currentTimeMillis();
            pendingLocations.entrySet().removeIf(entry ->
                    currentTime - entry.getValue().timestamp > 300000); // 5 minutes
        }, 6000L, 6000L); // Run every 5 minutes

        getLogger().info("DiscordLocationPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        pendingLocations.clear();
        if (locationsDB != null) {
            locationsDB.close();
        }
        getLogger().info("DiscordLocationPlugin has been disabled!");
    }

    private void loadConfig() {
        // Load configuration values
        webhookUrl = getConfig().getString("webhook-url", "");
        webhookName = getConfig().getString("webhook-name", "Minecraft Location Bot");
        webhookAvatarUrl = getConfig().getString("webhook-avatar-url", "");

        // Check if webhook is properly configured (not empty and not the default placeholder)
        webhookConfigured = !webhookUrl.isEmpty() &&
                !webhookUrl.contains("YOUR_WEBHOOK_ID") &&
                !webhookUrl.contains("YOUR_WEBHOOK_TOKEN");

        // Load embed customization
        String embedColorHex = getConfig().getString("embed.color", "#58A5F0");
        embedColor = hexToDecimal(embedColorHex);
        embedTitle = getConfig().getString("embed.title", "📍 Player Location");
        embedFooter = getConfig().getString("embed.footer", "");
        embedThumbnail = getConfig().getString("embed.thumbnail", "");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle reload command
        if (command.getName().equalsIgnoreCase("discordlocationreload")) {
            if (!sender.hasPermission("discordlocation.reload")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            reloadConfig();
            loadConfig();
            sender.sendMessage("§aDiscordLocationPlugin configuration reloaded!");
            if (!webhookConfigured) {
                sender.sendMessage("§eWarning: Webhook not configured. Discord integration is disabled.");
            }
            return true;
        }

        // Handle listlocations command
        if (command.getName().equalsIgnoreCase("listlocations")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("discordlocation.list")) {
                player.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            // Run database query async
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                List<Locations.LocStamp> locations = locationsDB.getLocationsByUsername(player.getName());

                getServer().getScheduler().runTask(this, () -> {
                    if (locations.isEmpty()) {
                        player.sendMessage("§eYou have no saved locations.");
                        return;
                    }

                    player.sendMessage("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§b§l  Your Saved Locations");
                    player.sendMessage("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");

                    for (Locations.LocStamp loc : locations) {
                        String date = dateFormat.format(loc.created_at);

                        // Create clickable location entry
                        TextComponent entry = new TextComponent("§7[§a#" + loc.id + "§7] §f" + loc.name);
                        player.spigot().sendMessage(entry);

                        TextComponent coords = new TextComponent("  §7World: §f" + loc.world + " §7| §f" + loc.x + ", " + loc.y + ", " + loc.z);
                        player.spigot().sendMessage(coords);

                        TextComponent info = new TextComponent("  §7Biome: §f" + loc.biome + " §7| §8" + date);
                        player.spigot().sendMessage(info);

                        // Add delete button
                        TextComponent deleteBtn = new TextComponent("  §c§l[Delete]");
                        deleteBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deletelocation " + loc.id));
                        deleteBtn.setHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                new ComponentBuilder("§cClick to delete this location").create()
                        ));
                        player.spigot().sendMessage(deleteBtn);

                        player.sendMessage(""); // Empty line for spacing
                    }

                    player.sendMessage("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§7Total: §f" + locations.size() + " §7location(s)");
                });
            });

            return true;
        }

        // Handle deletelocation command
        if (command.getName().equalsIgnoreCase("deletelocation")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("discordlocation.delete")) {
                player.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            if (args.length < 1) {
                player.sendMessage("§cUsage: /deletelocation <id>");
                return true;
            }

            int locationId;
            try {
                locationId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid location ID!");
                return true;
            }

            // Delete location async
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                boolean success = locationsDB.deleteLocation(locationId, player.getName());

                getServer().getScheduler().runTask(this, () -> {
                    if (success) {
                        player.sendMessage("§a✓ Location #" + locationId + " deleted successfully!");
                    } else {
                        player.sendMessage("§c✗ Failed to delete location. Make sure the ID is correct.");
                    }
                });
            });

            return true;
        }

        // Handle sendlocation command
        if (command.getName().equalsIgnoreCase("sendlocation")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            Player player = (Player) sender;

            // Check permission nodes, defaults to op if not assigned
            if (!player.hasPermission("discordlocation.send")) {
                player.sendMessage("§cYou don't have permission to use this command!");
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

            // Get optional note from args
            String note = null;
            if (args.length > 0) {
                note = String.join(" ", args);
            }

            // Use note as name, or generate default name
            String locationName = (note != null && !note.isEmpty())
                    ? note
                    : "Location at " + x + ", " + y + ", " + z;

            // Save to database immediately (async)
            String finalNote = note;
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                boolean savedToDB = locationsDB.saveLocation(
                        player.getName(),
                        locationName,
                        x, y, z,
                        world,
                        biome
                );

                getServer().getScheduler().runTask(this, () -> {
                    if (savedToDB) {
                        player.sendMessage("§a✓ Location saved to database!");

                        // If webhook is configured, offer to send to Discord
                        if (webhookConfigured) {
                            UUID playerId = player.getUniqueId();
                            pendingLocations.put(playerId, new PendingLocation(
                                    player.getName(), world, x, y, z, biome, finalNote
                            ));
                            sendInteractivePrompt(player, world, x, y, z, finalNote);
                        } else {
                            player.sendMessage("§7(Discord integration disabled - no webhook configured)");
                        }
                    } else {
                        player.sendMessage("§c✗ Failed to save location to database!");
                    }
                });
            });

            return true;
        } else if (command.getName().equalsIgnoreCase("confirmsend")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();

            // Check if webhook is configured
            if (!webhookConfigured) {
                player.sendMessage("§cDiscord integration is disabled - no webhook configured!");
                return true;
            }

            // Check if there's a pending location
            if (!pendingLocations.containsKey(playerId)) {
                player.sendMessage("§cNo pending location to send! Use /sendlocation first.");
                return true;
            }

            // Get the stored location data
            PendingLocation loc = pendingLocations.get(playerId);

            // Send to Discord
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    sendToDiscord(loc.playerName, loc.world, loc.x, loc.y, loc.z, loc.biome, loc.note);
                    getServer().getScheduler().runTask(this, () -> {
                        player.sendMessage("§a✓ Location sent to Discord!");
                        pendingLocations.remove(playerId);
                    });
                } catch (Exception e) {
                    getLogger().severe("Failed to send webhook: " + e.getMessage());
                    e.printStackTrace();
                    getServer().getScheduler().runTask(this, () ->
                            player.sendMessage("§c✗ Failed to send location to Discord!")
                    );
                }
            });

            return true;
        } else if (command.getName().equalsIgnoreCase("cancelsend")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();

            if (pendingLocations.containsKey(playerId)) {
                pendingLocations.remove(playerId);
                player.sendMessage("§cCancelled sending location to Discord.");
            } else {
                player.sendMessage("§cNo pending location to cancel.");
            }

            return true;
        }
        return false;
    }

    private void sendInteractivePrompt(Player player, String world, int x, int y, int z, String note) {
        // Create the main message
        TextComponent message = new TextComponent("§7Send this location to Discord?");
        player.spigot().sendMessage(message);

        TextComponent locationInfo = new TextComponent("§7Location: §f" + world + " §7(§f" + x + "§7, §f" + y + "§7, §f" + z + "§7)");
        player.spigot().sendMessage(locationInfo);

        // If there's a note, display it
        if (note != null && !note.isEmpty()) {
            TextComponent noteMsg = new TextComponent("§7Note: §f" + note);
            player.spigot().sendMessage(noteMsg);
        }

        // Create clickable button
        TextComponent button = new TextComponent("§b§l[✓ Send to Discord]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/confirmsend"));
        button.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§aClick to send your location to Discord").create()
        ));

        // Create cancel option
        TextComponent cancel = new TextComponent(" §c§l[✗ Cancel]");
        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cancelsend"));
        cancel.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§cClick to cancel").create()
        ));

        // Combine button and cancel
        TextComponent combined = new TextComponent("");
        combined.addExtra(button);
        combined.addExtra(cancel);

        player.spigot().sendMessage(combined);
    }

    private void sendToDiscord(String playerName, String world, int x, int y, int z, String biome, String note) throws Exception {
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

        // Add note field if provided
        if (note != null && !note.isEmpty()) {
            jsonBuilder.append(",{\"name\": \"Note\", \"value\": \"").append(escapeJson(note)).append("\", \"inline\": false}");
        }

        jsonBuilder.append("],");

        // Add footer
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
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid hex color format: " + hex + ". Using default color.");
            return 5814783; // Default blue
        }
    }

    public static DiscordLocationPlugin getInstance(){
        return instance;
    }
}