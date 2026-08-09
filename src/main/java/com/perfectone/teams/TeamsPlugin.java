package com.perfectone.teams;

import com.perfectone.teams.bridge.DiscordBridge;
import com.perfectone.teams.commands.AfkCommand;
import com.perfectone.teams.commands.BountyCommand;
import com.perfectone.teams.commands.LeaderboardCommand;
import com.perfectone.teams.commands.TeamCommand;
import com.perfectone.teams.data.Database;
import com.perfectone.teams.listeners.ChatListener;
import com.perfectone.teams.listeners.PlayerDeathListener;
import com.perfectone.teams.listeners.PlayerJoinQuitListener;
import com.perfectone.teams.manager.AfkManager;
import com.perfectone.teams.manager.PlayerDataManager;
import com.perfectone.teams.manager.TeamManager;
import com.perfectone.teams.util.BannedNameManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class TeamsPlugin extends JavaPlugin {

    private Database database;
    private TeamManager teamManager;
    private PlayerDataManager playerDataManager;
    private DiscordBridge discordBridge;
    private BannedNameManager bannedNameManager;
    private AfkManager afkManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String dbFile = getConfig().getString("storage.database-file", "teams.db");
        database = new Database(getDataFolder(), dbFile, getLogger());
        database.connect();

        teamManager = new TeamManager(this, database);
        playerDataManager = new PlayerDataManager(database);
        discordBridge = new DiscordBridge(this);
        bannedNameManager = new BannedNameManager(this);
        bannedNameManager.loadOrCreate();
        afkManager = new AfkManager();

        // Load synchronously at startup - fast for SQLite on a local file.
        teamManager.loadAll();
        playerDataManager.loadAll();

        getCommand("team").setExecutor(new TeamCommand(this));
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
        getCommand("bounty").setExecutor(new BountyCommand(this));
        getCommand("afk").setExecutor(new AfkCommand(this));

        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

        if (discordBridge.isEnabled()) {
            long interval = getConfig().getLong("discord-bridge.sync-interval-seconds", 60) * 20L;
            Bukkit.getScheduler().runTaskTimer(this, () -> discordBridge.pushSnapshotAsync(), 100L, interval);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new com.perfectone.teams.integration.PlaceholderHook(this).register();
            getLogger().info("PlaceholderAPI found - registered %perfectteams_*% placeholders "
                    + "(use these in your tab-list plugin's format if it doesn't read vanilla scoreboard teams).");
        }

        getLogger().info("PerfectTeams enabled - " + teamManager.all().size() + " teams loaded.");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.disconnect();
        }
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public Database getDatabase() {
        return database;
    }

    public DiscordBridge getDiscordBridge() {
        return discordBridge;
    }

    public BannedNameManager getBannedNameManager() {
        return bannedNameManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }
}
