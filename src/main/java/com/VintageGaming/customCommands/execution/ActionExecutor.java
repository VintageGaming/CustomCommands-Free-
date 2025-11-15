package com.VintageGaming.customCommands.execution;

import com.VintageGaming.customCommands.CustomCommands;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionExecutor {

    private final CustomCommands plugin;

    public ActionExecutor(CustomCommands plugin) {
        this.plugin = plugin;
    }

    private Map<String, Object> ensureIsMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        } else if (value instanceof ConfigurationSection) {
            return ((ConfigurationSection) value).getValues(false);
        }
        return new HashMap<>();
    }

    private Object replacePlaceholdersInObject(Object obj, Map<String, String> placeholders) {
        if (obj instanceof String) {
            return replacePlaceholders((String) obj, placeholders);
        }
        if (obj instanceof List) {
            List<Object> newList = new ArrayList<>();
            for (Object item : (List<?>) obj) {
                newList.add(replacePlaceholdersInObject(item, placeholders));
            }
            return newList;
        }
        if (obj instanceof ConfigurationSection) {
            Map<String, Object> map = ((ConfigurationSection) obj).getValues(false);
            return replacePlaceholdersInObject(map, placeholders);
        }
        if (obj instanceof Map) {
            Map<String, Object> newMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                newMap.put((String) entry.getKey(), replacePlaceholdersInObject(entry.getValue(), placeholders));
            }
            return newMap;
        }
        return obj;
    }

    public void executeActions(CommandSender sender, Map<String, Object> actions, Map<String, String> placeholders) {
        if (actions == null) return;

        for (Map.Entry<String, Object> entry : actions.entrySet()) {
            String actionType = entry.getKey();
            Object processedValue = replacePlaceholdersInObject(entry.getValue(), placeholders);
            execute(sender, actionType, processedValue);
        }
    }

    private void execute(CommandSender sender, String type, Object value) {
        switch (type.toLowerCase()) {
            case "console":
            case "player":
            case "broadcast":
            case "message":
            case "sound":
                if (value instanceof List) {
                    executeStringListAction(sender, type.toLowerCase(), (List<String>) value);
                } else {
                    plugin.getLogger().warning("Invalid value type for action '" + type + "'. Expected a List.");
                }
                break;
            case "teleport":
                executeTeleport(sender, ensureIsMap(value));
                break;
            case "big_text":
                executeBigText(sender, ensureIsMap(value));
                break;
            case "small_text":
                if (value instanceof String) {
                    executeSmallText(sender, (String) value);
                } else {
                    plugin.getLogger().warning("Invalid value type for action 'small_text'. Expected a String.");
                }
                break;
            default:
                plugin.getLogger().warning("Unknown action type: " + type);
        }
    }

    private void executeStringListAction(CommandSender sender, String type, List<String> values) {
        if (!(sender instanceof Player) && (type.equals("player") || type.equals("sound"))) {
            sender.sendMessage(ChatColor.RED + "This command action can only be run by a player.");
            return;
        }

        for (String value : values) {
            value = ChatColor.translateAlternateColorCodes('&', value);
            switch (type) {
                case "console":
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                    break;
                case "player":
                    ((Player) sender).chat(value);
                    break;
                case "broadcast":
                    Bukkit.broadcastMessage(value);
                    break;
                case "message":
                    sender.sendMessage(value);
                    break;
                case "sound":
                    try {
                        ((Player) sender).playSound(((Player) sender).getLocation(), Sound.valueOf(value.toUpperCase()), 1.0f, 1.0f);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound name: " + value);
                    }
                    break;
            }
        }
    }

    private void executeTeleport(CommandSender sender, Map<String, Object> teleportData) {
        String whoName = (String) teleportData.get("who");
        if (whoName == null) {
            plugin.getLogger().warning("Teleport action is missing the 'who' field.");
            return;
        }

        Player target = Bukkit.getPlayer(whoName);
        if (target == null) {
            plugin.getLogger().warning("Teleport target not found: " + whoName);
            return;
        }

        String toPlayerName = (String) teleportData.get("toPlayer");
        if (toPlayerName != null) {
            Player destinationPlayer = Bukkit.getPlayer(toPlayerName);
            if (destinationPlayer != null) {
                target.teleport(destinationPlayer);
            } else {
                plugin.getLogger().warning("Teleport destination player not found: " + toPlayerName);
            }
            return;
        }

        String worldName = (String) teleportData.get("world") != null ? (String) teleportData.get("world") : target.getWorld().getName();
        Object xObj = teleportData.get("x");
        Object yObj = teleportData.get("y");
        Object zObj = teleportData.get("z");

        if (worldName != null && xObj != null && yObj != null && zObj != null) {
            try {
                double x = Double.parseDouble(String.valueOf(xObj));
                double y = Double.parseDouble(String.valueOf(yObj));
                double z = Double.parseDouble(String.valueOf(zObj));
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Invalid world for teleport: " + worldName);
                    return;
                }
                Location location = new Location(world, x, y, z);
                if (!isSafeLocation(location) && target.getGameMode() != GameMode.CREATIVE && target.getGameMode() != GameMode.SPECTATOR) {
                    location = world.getHighestBlockAt(location).getLocation().add(0, 1, 0);
                }
                target.teleport(location);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid teleport coordinates.");
            }
        }
    }

    private void executeBigText(CommandSender sender, Map<String, Object> textData) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;
        String title = ChatColor.translateAlternateColorCodes('&', (String) textData.getOrDefault("title", ""));
        String subtitle = ChatColor.translateAlternateColorCodes('&', (String) textData.getOrDefault("subtitle", ""));
        player.sendTitle(title, subtitle, 10, 70, 20);
    }

    private void executeSmallText(CommandSender sender, String message) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
    }

    public void executeDelayedActions(CommandSender sender, Map<String, Object> delayData, Map<String, Object> mainActions, Map<String, String> placeholders) {
        String length = (String) delayData.get("length");
        long ticks = parseTime(length);

        Map<String, Object> perSecondActions = ensureIsMap(delayData.get("per_second-actions"));

        new BukkitRunnable() {
            long remainingTicks = ticks;

            @Override
            public void run() {
                if (remainingTicks <= 0) {
                    if (mainActions != null) {
                        executeActions(sender, mainActions, placeholders);
                    }
                    this.cancel();
                    return;
                }

                if (!perSecondActions.isEmpty()) {
                    Map<String, String> delayPlaceholders = new HashMap<>(placeholders);
                    delayPlaceholders.put("{seconds_remaining}", String.valueOf(remainingTicks / 20));
                    executeActions(sender, perSecondActions, delayPlaceholders);
                }

                remainingTicks -= 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private long parseTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) return 0;
        String[] parts = timeString.split(" ");
        if (parts.length != 2) return 0;
        try {
            long amount = Long.parseLong(parts[0]);
            String unit = parts[1].toLowerCase();
            if (unit.startsWith("second")) return amount * 20;
            if (unit.startsWith("minute")) return amount * 1200;
            if (unit.startsWith("hour")) return amount * 72000;
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isSafeLocation(Location location) {
        if (location == null) return false;
        try {
            Block feet = location.getBlock();
            if (!feet.getType().isTransparent() && !feet.getLocation().add(0, 1, 0).getBlock().getType().isTransparent()) {
                return false; // not transparent (will suffocate)
            }
            Block head = feet.getRelative(BlockFace.UP);
            if (!head.getType().isTransparent()) {
                return false; // not transparent (will suffocate)
            }
            Block ground = feet.getRelative(BlockFace.DOWN);
            // returns if the ground is solid or not.
            return ground.getType().isSolid();
        } catch (Exception err) {
            err.printStackTrace();
        }
        return false;
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replaceAll(Pattern.quote(entry.getKey()), Matcher.quoteReplacement(entry.getValue()));
        }
        return result;
    }
}
