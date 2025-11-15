package com.VintageGaming.customCommands.model;

import java.util.List;
import java.util.Map;

public class CustomCommandData {

    private final String name;
    private final String permission;
    private final String permissionMessage;
    private final String usageMessage;
    private final List<String> aliases;
    private final double cost;
    private final String cooldown;
    private final Map<String, Object> rootActions;
    private final Map<String, Object> rootDelay;
    private final Map<String, ArgumentNode> arguments;

    public CustomCommandData(String name, String permission, String permissionMessage, String usageMessage, List<String> aliases, double cost, String cooldown, Map<String, Object> rootActions, Map<String, Object> rootDelay, Map<String, ArgumentNode> arguments) {
        this.name = name;
        this.permission = permission;
        this.permissionMessage = permissionMessage;
        this.usageMessage = usageMessage;
        this.aliases = aliases;
        this.cost = cost;
        this.cooldown = cooldown;
        this.rootActions = rootActions;
        this.rootDelay = rootDelay;
        this.arguments = arguments;
    }

    // --- Getters ---

    public String getName() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public String getPermissionMessage() {
        return permissionMessage;
    }

    public String getUsageMessage() {
        return usageMessage;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public double getCost() {
        return cost;
    }

    public String getCooldown() {
        return cooldown;
    }

    public Map<String, Object> getRootActions() {
        return rootActions;
    }

    public Map<String, Object> getRootDelay() {
        return rootDelay;
    }

    public Map<String, ArgumentNode> getArguments() {
        return arguments;
    }

    public boolean hasRootActions() {
        return rootActions != null && !rootActions.isEmpty();
    }

    public boolean hasRootDelay() {
        return rootDelay != null && !rootDelay.isEmpty();
    }
}
