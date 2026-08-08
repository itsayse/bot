package com.example.nighttreefeller;

import org.bukkit.plugin.java.JavaPlugin;

public class NightTreeFellerPlugin extends JavaPlugin {

    private SleepListener sleepListener;
    private AnnouncementScheduler announcementScheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        sleepListener = new SleepListener(this);
        getServer().getPluginManager().registerEvents(sleepListener, this);
        getServer().getPluginManager().registerEvents(new TreeFellerListener(this), this);

        announcementScheduler = new AnnouncementScheduler(this);
        announcementScheduler.start();

        getLogger().info("NightTreeFeller enabled - sleep-to-skip, tree felling, and announcements are active.");
    }

    @Override
    public void onDisable() {
        if (sleepListener != null) {
            sleepListener.cancelAllTransitions();
        }
        if (announcementScheduler != null) {
            announcementScheduler.stop();
        }
        getLogger().info("NightTreeFeller disabled.");
    }
}
