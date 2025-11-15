package com.VintageGaming.customCommands.parser;

import com.VintageGaming.customCommands.model.ArgumentNode;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CommandParser {

    private Map<String, ArgumentNode> parseArguments(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<String, ArgumentNode> nodes = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection subSection = section.getConfigurationSection(key);
            if (subSection != null) {
                nodes.put(key, parseNode(key, subSection));
            }
        }
        return nodes;
    }

    private ArgumentNode parseNode(String key, ConfigurationSection section) {
        String permission = section.getString("permission");
        String permissionMessage = section.getString("permission-message");
        String type = section.getString("type");
        String typeError = section.getString("type-error");

        Map<String, Object> actions = new HashMap<>();
        if (section.isConfigurationSection("actions")) {
            actions = section.getConfigurationSection("actions").getValues(true);
        }

        Map<String, Object> delay = new HashMap<>();
        if (section.isConfigurationSection("delay")) {
            delay = section.getConfigurationSection("delay").getValues(true);
        }

        Map<String, ArgumentNode> children = parseArguments(section.getConfigurationSection("arguments"));

        return new ArgumentNode(key, permission, permissionMessage, type, typeError, actions, delay, children);
    }
}
