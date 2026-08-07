package com.example.nighttreefeller;

import org.bukkit.plugin.java.JavaPlugin;

public class NightTreeFellerPlugin extends JavaPlugin {

    private SleepListener sleepListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        sleepListener = new SleepListener(this);
        getServer().getPluginManager().registerEvents(sleepListener, this);
        getServer().getPluginManager().registerEvents(new TreeFellerListener(this), this);

        getLogger().info("NightTreeFeller enabled - sleep-to-skip and tree felling are active.");
    }

    @Override
    public void onDisable() {
        if (sleepListener != null) {
            sleepListener.cancelAllTransitions();
        }
        getLogger().info("NightTreeFeller disabled.");
    }
}
