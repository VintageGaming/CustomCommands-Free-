package com.VintageGaming.customCommands.command;

import com.VintageGaming.customCommands.CustomCommands;
import com.VintageGaming.customCommands.execution.ActionExecutor;
import com.VintageGaming.customCommands.model.ArgumentNode;
import com.VintageGaming.customCommands.model.CustomCommandData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Represents a command dynamically created from a configuration file.
 * This class handles the parsing of arguments, permission checks, cooldowns, costs,
 * and execution of corresponding actions based on the command's configuration.
 */
public class DynamicCommand extends Command {

    private final CustomCommandData commandData;
    private final ActionExecutor actionExecutor;
    private final CustomCommands plugin;

    // A map to store cooldowns for each command on a per-player basis.
    private static final Map<String, Map<UUID, Long>> commandCooldowns = new HashMap<>();

    public DynamicCommand(CustomCommandData commandData, ActionExecutor actionExecutor, CustomCommands plugin) {
        super(commandData.getName());
        this.commandData = commandData;
        this.actionExecutor = actionExecutor;
        this.plugin = plugin;

        // Set command properties from the loaded data
        if (commandData.getAliases() != null) {
            this.setAliases(commandData.getAliases());
        }
        if (commandData.getPermission() != null) {
            this.setPermission(commandData.getPermission());
        }
        if (commandData.getPermissionMessage() != null) {
            this.setPermissionMessage(ChatColor.translateAlternateColorCodes('&', commandData.getPermissionMessage()));
        }
        if (commandData.getUsageMessage() != null) {
            this.setUsage(ChatColor.translateAlternateColorCodes('&', commandData.getUsageMessage()));
        }
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        // Check top-level command permission
        if (getPermission() != null && !sender.hasPermission(getPermission())) {
            sender.sendMessage(getPermissionMessage());
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{sender}", sender.getName()); // Placeholder for command sender's name

        Map<String, Object> actions = null;
        Map<String, Object> delay = null;

        // Determine the correct actions and node based on arguments
        if (args.length == 0) {
            // No arguments provided, check for root-level actions
            if (commandData.hasRootActions() || commandData.hasRootDelay()) {
                actions = commandData.getRootActions();
                delay = commandData.getRootDelay();
            } else if (!commandData.getArguments().isEmpty()) {
                // No root actions, but sub-arguments exist, so usage is incorrect.
                sender.sendMessage(this.getUsage());
                return true;
            }
        } else {
            // Arguments provided, traverse the argument tree
            ArgumentNode currentNode = null;
            Map<String, ArgumentNode> children = commandData.getArguments();

            for (String arg : args) {
                ArgumentNode matchedNode = findMatchingNode(arg, children);

                if (matchedNode == null) {
                    // No matching node found for the current argument.
                    // Check if a placeholder was expected and provide a specific error.
                    List<ArgumentNode> placeholderChildren = children.values().stream()
                            .filter(ArgumentNode::isPlaceholder)
                            .collect(Collectors.toList());

                    if (placeholderChildren.size() == 1) {
                        ArgumentNode expectedNode = placeholderChildren.get(0);
                        String errorMsg = expectedNode.getTypeError() != null ? expectedNode.getTypeError() : "&cInvalid input for <" + expectedNode.getType() + ">.";
                        errorMsg = errorMsg.replace("%input%", arg);
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', errorMsg));
                    } else {
                        sender.sendMessage(this.getUsage());
                    }
                    return true;
                }

                // Check permission for this specific argument path
                if (matchedNode.getPermission() != null && !sender.hasPermission(matchedNode.getPermission())) {
                    String permMsg = matchedNode.getPermissionMessage() != null ?
                            ChatColor.translateAlternateColorCodes('&', matchedNode.getPermissionMessage()) :
                            getPermissionMessage(); // Fallback to main permission message
                    sender.sendMessage(permMsg);
                    return true;
                }

                // If the node is a placeholder (e.g., [player] ), store the provided value.
                if (matchedNode.isPlaceholder()) {
                    placeholders.put(matchedNode.getName(), arg);
                }

                currentNode = matchedNode;
                children = currentNode.getChildren();
            }

            // After iterating through all args, the final node is the target.
            if (currentNode != null) {
                if (currentNode.hasActions() || currentNode.hasDelay()) {
                    actions = currentNode.getActions();
                    delay = currentNode.getDelay();
                } else if (!currentNode.getChildren().isEmpty()) {
                    sender.sendMessage(this.getUsage());
                    return true;
                }
            }
        }

        // If no valid actions were found after parsing, it's an invalid command.
        // This can happen if the root command is executed without args and has no root actions.
        if (actions == null && delay == null) {
            sender.sendMessage(this.getUsage());
            return true;
        }

        // Perform player-specific checks (cost, cooldown) if a valid action path was found.
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // Check Cooldown
            if (commandData.getCooldown() != null && !commandData.getCooldown().isEmpty()) {
                if (isOnCooldown(player.getUniqueId(), commandData.getName())) {
                    player.sendMessage(ChatColor.RED + "You are on cooldown for this command.");
                    return true;
                }
            }

            // Check Cost
            if (commandData.getCost() > 0) {
                if (plugin.getEconomy() == null) {
                    player.sendMessage(ChatColor.RED + "Commands with a cost are currently disabled.");
                    return true;
                }
                if (!plugin.getEconomy().has(player, commandData.getCost())) {
                    player.sendMessage(ChatColor.RED + "You do not have enough money to use this command.");
                    return true;
                }
                plugin.getEconomy().withdrawPlayer(player, commandData.getCost());
            }

            // Set cooldown ONLY after all checks have passed.
            setCooldown(player.getUniqueId(), commandData.getName());
        }

        // 5. Execute the actions.
        if (delay != null) {
            actionExecutor.executeDelayedActions(sender, delay, actions, placeholders);
        } else {
            actionExecutor.executeActions(sender, actions, placeholders);
        }

        return true;
    }

    private void setCooldown(UUID playerUUID, String commandName) {
        long cooldownMillis = parseTime(commandData.getCooldown());
        if (cooldownMillis <= 0) return;

        long expirationTime = System.currentTimeMillis() + cooldownMillis;
        commandCooldowns.computeIfAbsent(commandName, k -> new HashMap<>()).put(playerUUID, expirationTime);

        // Schedule a task to remove the player from the cooldown map when it expires.
        // This is a cleanup to prevent the map from growing indefinitely over time.
        long cooldownTicks = cooldownMillis / 50;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Map<UUID, Long> cooldowns = commandCooldowns.get(commandName);
            if (cooldowns != null) {
                // Only remove if the expiration time matches, to avoid race conditions if the command is run again.
                if (cooldowns.getOrDefault(playerUUID, 0L) == expirationTime) {
                    cooldowns.remove(playerUUID);
                }
                if (cooldowns.isEmpty()) {
                    commandCooldowns.remove(commandName);
                }
            }
        }, cooldownTicks);
    }

    /**
     * Finds a matching argument node from a map of possible children.
     * It prioritizes direct matches over placeholders.
     * @param arg The command argument string.
     * @param children The map of possible child nodes.
     * @return The matched ArgumentNode, or null if no match is found.
     */
    private ArgumentNode findMatchingNode(String arg, Map<String, ArgumentNode> children) {
        // Priority 1: Direct match (e.g., "reload")
        if (children.containsKey(arg)) {
            return children.get(arg);
        }

        // Priority 2: Static placeholder match (e.g., "{some_value}")
        for (ArgumentNode node : children.values()) {
            if (node.isStaticPlaceholder()) {
                String staticName = node.getName().substring(1, node.getName().length() - 1);
                if (staticName.equalsIgnoreCase(arg)) {
                    return node;
                }
            }
        }

        // Priority 3: Dynamic placeholder with type validation (e.g., [online_player] )
        for (ArgumentNode node : children.values()) {
            if (node.isPlaceholder()) {
                if (validateType(arg, node.getType())) {
                    return node;
                }
            }
        }

        return null; // No match found
    }

    /**
     * Validates if the given input string matches the expected argument type.
     * @param input The user's input.
     * @param type The expected type (e.g., "integer", "online_player").
     * @return True if the input is valid for the type, otherwise false.
     */
    private boolean validateType(String input, String type) {
        if (type == null) return true; // No type defined, so any input is valid.

        switch (type.toLowerCase()) {
            case "online_player":
                return Bukkit.getPlayerExact(input) != null;
            case "integer":
                try {
                    Integer.parseInt(input);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case "double":
                try {
                    Double.parseDouble(input);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case "player":
                return Bukkit.getOfflinePlayer(input).hasPlayedBefore();
            case "world":
                return Bukkit.getWorld(input) != null;
            case "text":
            default:
                return true; // "text" or any unrecognized type accepts any input.
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        // Traverse the argument tree to find the current context for tab-completion.
        Map<String, ArgumentNode> children = commandData.getArguments();
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];
            // Note: Using a new HashMap for placeholders as we don't need them for tab-complete logic.
            ArgumentNode matchedNode = findMatchingNode(arg, children);
            if (matchedNode == null || matchedNode.getChildren().isEmpty()) {
                return new ArrayList<>();
            }
            // Don't suggest sub-arguments if the user doesn't have permission for the parent.
            if (matchedNode.getPermission() != null && !sender.hasPermission(matchedNode.getPermission())) {
                return new ArrayList<>();
            }
            children = matchedNode.getChildren();
        }

        String currentArg = args[args.length - 1].toLowerCase();
        List<String> suggestions = new ArrayList<>();

        // Generate suggestions based on the possible next arguments.
        for (Map.Entry<String, ArgumentNode> entry : children.entrySet()) {
            String key = entry.getKey();
            ArgumentNode node = entry.getValue();

            // Don't suggest arguments the user doesn't have permission for.
            if (node.getPermission() != null && !sender.hasPermission(node.getPermission())) {
                continue;
            }

            if (node.isPlaceholder()) {
                // Provide dynamic suggestions for certain placeholder types.
                if ("online_player".equalsIgnoreCase(node.getType())) {
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(currentArg))
                            .forEach(suggestions::add);
                } else if ("world".equalsIgnoreCase(node.getType())) {
                    Bukkit.getWorlds().stream()
                            .map(org.bukkit.World::getName)
                            .filter(name -> name.toLowerCase().startsWith(currentArg))
                            .forEach(suggestions::add);
                }
            } else if (key.toLowerCase().startsWith(currentArg)) {
                // Suggest literal sub-commands.
                suggestions.add(key);
            }
        }
        return suggestions;
    }

    /**
     * Checks if a player is currently on cooldown for this command.
     * @param playerUUID The UUID of the player to check.
     * @param commandName The name of the command.
     * @return True if the player is on cooldown, otherwise false.
     */
    private boolean isOnCooldown(UUID playerUUID, String commandName) {
        Map<UUID, Long> cooldowns = commandCooldowns.get(commandName);
        if (cooldowns == null) {
            return false;
        }
        return cooldowns.getOrDefault(playerUUID, 0L) > System.currentTimeMillis();
    }

    /**
     * Parses a time string (e.g., "10 seconds") into milliseconds.
     * @param timeString The string to parse.
     * @return The time in milliseconds, or 0 if parsing fails.
     */
    private long parseTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return 0;
        }
        // Expects format like "10 seconds", "5 minutes", etc.
        String[] parts = timeString.split(" ");
        if (parts.length != 2) {
            return 0;
        }
        try {
            long amount = Long.parseLong(parts[0]);
            switch (parts[1].toLowerCase()) {
                case "second":
                case "seconds":
                    return TimeUnit.SECONDS.toMillis(amount);
                case "minute":
                case "minutes":
                    return TimeUnit.MINUTES.toMillis(amount);
                case "hour":
                case "hours":
                    return TimeUnit.HOURS.toMillis(amount);
                default:
                    return 0; // Unsupported time unit
            }
        } catch (NumberFormatException e) {
            return 0; // Invalid number
        }
    }
}
