package com.perfectone.teams;

import com.perfectone.teams.bridge.DiscordBridge;
import com.perfectone.teams.commands.AfkCommand;
import com.perfectone.teams.commands.BountyCommand;
import com.perfectone.teams.commands.LeaderboardCommand;
import com.perfectone.teams.commands.TeamCommand;
import com.perfectone.teams.data.Database;
import com.perfectone.teams.integration.PlaceholderHook;
import com.perfectone.teams.listeners.ChatListener;
import com.perfectone.teams.listeners.CombatListener;
import com.perfectone.teams.listeners.PlayerDeathListener;
import com.perfectone.teams.listeners.PlayerJoinQuitListener;
import com.perfectone.teams.manager.AfkManager;
import com.perfectone.teams.manager.CombatTagManager;
import com.perfectone.teams.manager.PlayerDataManager;
import com.perfectone.teams.manager.TeamManager;
import com.perfectone.teams.util.BannedNameManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class TeamsPlugin extends JavaPlugin {

    private Database database;
    private TeamManager teamManager;
    private PlayerDataManager playerDataManager;
    private DiscordBridge discordBridge;
    private BannedNameManager bannedNameManager;
    private AfkManager afkManager;
    private CombatTagManager combatTagManager;
    private PlayerDeathListener playerDeathListener;

    public void onEnable() {
        this.saveDefaultConfig();
        String dbFile = this.getConfig().getString("storage.database-file", "teams.db");
        this.database = new Database(this.getDataFolder(), dbFile, this.getLogger());
        this.database.connect();
        this.teamManager = new TeamManager(this, this.database);
        this.playerDataManager = new PlayerDataManager(this.database);
        this.discordBridge = new DiscordBridge(this);
        this.bannedNameManager = new BannedNameManager(this);
        this.bannedNameManager.loadOrCreate();
        this.afkManager = new AfkManager();
        this.combatTagManager = new CombatTagManager();
        this.teamManager.loadAll();
        this.playerDataManager.loadAll();

        this.getCommand("team").setExecutor((CommandExecutor) new TeamCommand(this));
        this.getCommand("leaderboard").setExecutor((CommandExecutor) new LeaderboardCommand(this));
        this.getCommand("bounty").setExecutor((CommandExecutor) new BountyCommand(this));
        this.getCommand("afk").setExecutor((CommandExecutor) new AfkCommand(this));

        this.playerDeathListener = new PlayerDeathListener(this);
        Bukkit.getPluginManager().registerEvents((Listener) this.playerDeathListener, (Plugin) this);
        Bukkit.getPluginManager().registerEvents((Listener) new PlayerJoinQuitListener(this), (Plugin) this);
        Bukkit.getPluginManager().registerEvents((Listener) new ChatListener(this), (Plugin) this);
        Bukkit.getPluginManager().registerEvents((Listener) new CombatListener(this), (Plugin) this);

        if (this.discordBridge.isEnabled()) {
            long interval = this.getConfig().getLong("discord-bridge.sync-interval-seconds", 300L) * 20L;
            Bukkit.getScheduler().runTaskTimer((Plugin) this, () -> this.discordBridge.pushSnapshotAsync(), 100L, interval);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this).register();
            this.getLogger().info("PlaceholderAPI found - registered %perfectteams_*% placeholders (use these in your tab-list plugin's format if it doesn't read vanilla scoreboard teams).");
        }

        this.getLogger().info("PerfectTeams enabled - " + this.teamManager.all().size() + " teams loaded.");
    }

    public void onDisable() {
        if (this.database != null) {
            this.database.disconnect();
        }
    }

    public TeamManager getTeamManager() {
        return this.teamManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public Database getDatabase() {
        return this.database;
    }

    public DiscordBridge getDiscordBridge() {
        return this.discordBridge;
    }

    public BannedNameManager getBannedNameManager() {
        return this.bannedNameManager;
    }

    public AfkManager getAfkManager() {
        return this.afkManager;
    }

    public CombatTagManager getCombatTagManager() {
        return this.combatTagManager;
    }

    public PlayerDeathListener getPlayerDeathListener() {
        return this.playerDeathListener;
    }
}
