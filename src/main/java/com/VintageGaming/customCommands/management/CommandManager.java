package com.VintageGaming.customCommands.management;

import com.VintageGaming.customCommands.CustomCommands;
import com.VintageGaming.customCommands.command.DynamicCommand;
import com.VintageGaming.customCommands.execution.ActionExecutor;
import com.VintageGaming.customCommands.model.ArgumentNode;
import com.VintageGaming.customCommands.model.CustomCommandData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandManager {

    private final CustomCommands plugin;
    private final ActionExecutor actionExecutor;
    private CommandMap commandMap;
    private Map<String, Command> knownCommands;

    public CommandManager(CustomCommands plugin, ActionExecutor actionExecutor) {
        this.plugin = plugin;
        this.actionExecutor = actionExecutor;
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            this.commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            this.knownCommands = (Map<String, Command>) knownCommandsField.get(this.commandMap);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            plugin.getLogger().severe("Could not access CommandMap or knownCommands. Commands will not be registered!");
        }
    }

    public void loadCommands() {
        File commandsDir = new File(plugin.getDataFolder(), "commands");
        if (!commandsDir.exists()) {
            commandsDir.mkdirs();
        }

        File[] commandFiles = commandsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (commandFiles == null) return;

        for (File file : commandFiles) {
            String commandName = file.getName().replace(".yml", "");
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            CustomCommandData commandData = loadCommandData(commandName, config);
            registerCommand(commandData);
        }
    }

    private CustomCommandData loadCommandData(String name, FileConfiguration config) {
        String permission = config.getString("permission");
        String permissionMessage = config.getString("permission-message");
        String usageMessage = config.getString("usage-message");
        List<String> aliases = config.getStringList("aliases");
        double cost = config.getDouble("cost", 0);
        String cooldown = config.getString("cooldown");

        Map<String, Object> rootActions = null;
        if (config.isConfigurationSection("actions")) {
            rootActions = config.getConfigurationSection("actions").getValues(false);
        }

        Map<String, Object> rootDelay = null;
        if (config.isConfigurationSection("delay")) {
            rootDelay = config.getConfigurationSection("delay").getValues(false);
        }

        Map<String, ArgumentNode> arguments = new HashMap<>();
        if (config.isConfigurationSection("arguments")) {
            arguments = loadArgumentNodes(config.getConfigurationSection("arguments"));
        }

        return new CustomCommandData(name, permission, permissionMessage, usageMessage, aliases, cost, cooldown, rootActions, rootDelay, arguments);
    }

    private Map<String, ArgumentNode> loadArgumentNodes(ConfigurationSection section) {
        Map<String, ArgumentNode> nodes = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection argSection = section.getConfigurationSection(key);
            if (argSection != null) {
                nodes.put(key, loadArgumentNode(key, argSection));
            }
        }
        return nodes;
    }

    private ArgumentNode loadArgumentNode(String name, ConfigurationSection config) {
        String permission = config.getString("permission");
        String permissionMessage = config.getString("permission-message");
        String type = config.getString("type");
        String typeError = config.getString("type-error");

        Map<String, Object> actions = null;
        if (config.isConfigurationSection("actions")) {
            actions = config.getConfigurationSection("actions").getValues(false);
        }

        Map<String, Object> delay = null;
        if (config.isConfigurationSection("delay")) {
            delay = config.getConfigurationSection("delay").getValues(false);
        }

        Map<String, ArgumentNode> children = new HashMap<>();
        if (config.isConfigurationSection("arguments")) {
            children = loadArgumentNodes(config.getConfigurationSection("arguments"));
        }

        return new ArgumentNode(name, permission, permissionMessage, type, typeError, actions, delay, children);
    }

    private void registerCommand(CustomCommandData commandData) {
        if (commandMap == null) return;

        unregisterCommand(commandData.getName());

        DynamicCommand command = new DynamicCommand(commandData, actionExecutor, plugin);
        commandMap.register(plugin.getName(), command);
    }

    private void unregisterCommand(String commandName) {
        if (knownCommands != null && knownCommands.containsKey(commandName)) {
            Command existing = knownCommands.remove(commandName);
            if (existing != null && existing.getAliases() != null) {
                for (String alias : existing.getAliases()) {
                    knownCommands.remove(alias);
                }
            }
        }
    }

    public void unregisterAllCommands() {
        if (knownCommands != null) {
            List<String> commandsToRemove = knownCommands.values().stream()
                    .filter(cmd -> cmd instanceof DynamicCommand)
                    .map(Command::getName)
                    .collect(Collectors.toList());

            for (String cmdName : commandsToRemove) {
                unregisterCommand(cmdName);
            }
        }
    }

    public void reloadCommands() {
        plugin.getLogger().info("Reloading all custom commands...");
        unregisterAllCommands();
        loadCommands();
        plugin.getLogger().info("All custom commands have been reloaded.");
    }
}
