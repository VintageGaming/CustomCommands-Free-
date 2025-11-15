package com.VintageGaming.customCommands;

import com.VintageGaming.customCommands.command.AdminCommand;
import com.VintageGaming.customCommands.execution.ActionExecutor;
import com.VintageGaming.customCommands.management.CommandManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public final class CustomCommands extends JavaPlugin {

    private CommandManager commandManager;
    private ActionExecutor actionExecutor;
    private Economy economy;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().info("Vault not found! Commands with Cost will not work.");
        }

        this.actionExecutor = new ActionExecutor(this);
        this.commandManager = new CommandManager(this, actionExecutor);
        commandManager.loadCommands();


        AdminCommand adminCommand = new AdminCommand(commandManager);
        getCommand("customcommands").setExecutor(adminCommand);
        getCommand("customcommands").setTabCompleter(adminCommand);
        getLogger().info("CustomCommands has been enabled!");
    }

    @Override
    public void onDisable() {
        if (commandManager != null) {
            commandManager.unregisterAllCommands();
        }
        getLogger().info("CustomCommands has been disabled.");
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static UUID getUUIDFromName(String name) {
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getUniqueId();
        }

        Path userCachePath = Paths.get("usercache.json");
        if (!userCachePath.toFile().exists()) {
            return null;
        }

        Gson gson = new Gson();
        try (FileReader reader = new FileReader(userCachePath.toFile())) {
            Type listType = new TypeToken<List<UserCacheEntry>>() {}.getType();
            List<UserCacheEntry> userCache = gson.fromJson(reader, listType);

            if (userCache != null) {
                for (UserCacheEntry entry : userCache) {
                    if (entry.getName().equalsIgnoreCase(name)) {
                        return UUID.fromString(entry.getUuid());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("CustomCommands: Could not read usercache.json.");
            e.printStackTrace();
        }

        return null;
    }

    public static class UserCacheEntry {
        private String name;
        private String uuid;
        private String expiresOn;

        public String getName() {
            return name;
        }

        public String getUuid() {
            return uuid;
        }

        public String getExpiresOn() {
            return expiresOn;
        }
    }
}
