package com.VintageGaming.customCommands.model;

import java.util.Map;

public class ArgumentNode {

    private final String name;
    private final String permission;
    private final String permissionMessage;
    private final String type;
    private final String typeError;
    private final Map<String, Object> actions;
    private final Map<String, Object> delay;
    private final Map<String, ArgumentNode> children;

    public ArgumentNode(String name, String permission, String permissionMessage, String type, String typeError, Map<String, Object> actions, Map<String, Object> delay, Map<String, ArgumentNode> children) {
        this.name = name;
        this.permission = permission;
        this.permissionMessage = permissionMessage;
        this.type = type;
        this.typeError = typeError;
        this.actions = actions;
        this.delay = delay;
        this.children = children;
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

    public String getType() {
        return type;
    }

    public String getTypeError() {
        return typeError;
    }

    public Map<String, Object> getActions() {
        return actions;
    }

    public Map<String, Object> getDelay() {
        return delay;
    }

    public Map<String, ArgumentNode> getChildren() {
        return children;
    }

    public boolean hasActions() {
        return actions != null && !actions.isEmpty();
    }

    public boolean hasDelay() {
        return delay != null && !delay.isEmpty();
    }

    public boolean isPlaceholder() {
        return name != null && name.startsWith("[") && name.endsWith("]");
    }

    public boolean isStaticPlaceholder() {
        return name != null && name.startsWith("{") && name.endsWith("}");
    }
}
