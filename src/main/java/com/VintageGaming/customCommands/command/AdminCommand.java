package com.VintageGaming.customCommands.command;

import com.VintageGaming.customCommands.management.CommandManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminCommand implements CommandExecutor, TabCompleter {

    // This Class is for Reloading the Plugin

    private final CommandManager commandManager;


    public AdminCommand(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        // Handle Reload subcommand
        if (args[0].equalsIgnoreCase("reload")) {
            commandManager.reloadCommands();
            sender.sendMessage(ChatColor.GREEN + "CustomCommands configuration and commands have been reloaded!");
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "--- CustomCommands Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - Reloads all custom commands from files.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("customcommands.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Collections.singletonList("reload"), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
