package com.example.freeze;

import org.bukkit.plugin.java.JavaPlugin;

public class FreezePlugin extends JavaPlugin {

    private FreezeManager freezeManager;

    @Override
    public void onEnable() {
        this.freezeManager = new FreezeManager();

        getServer().getPluginManager().registerEvents(new FreezeListener(this, freezeManager), this);

        FreezeCommand freezeCommand = new FreezeCommand(this, freezeManager);
        getCommand("freeze").setExecutor(freezeCommand);
        getCommand("unfreeze").setExecutor(freezeCommand);

        getLogger().info("FreezePlugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("FreezePlugin disabled.");
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }
}
