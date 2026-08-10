package com.perfectone.teams;

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
import com.perfectone.teams.manager.BountyContractManager;
import com.perfectone.teams.manager.CombatTagManager;
import com.perfectone.teams.manager.PlayerDataManager;
import com.perfectone.teams.manager.SecretMissionManager;
import com.perfectone.teams.manager.TeamManager;
import com.perfectone.teams.manager.TopRoleManager;
import com.perfectone.teams.util.BannedNameManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class TeamsPlugin extends JavaPlugin {

    private Database database;
    private TeamManager teamManager;
    private PlayerDataManager playerDataManager;
    private BannedNameManager bannedNameManager;
    private AfkManager afkManager;
    private CombatTagManager combatTagManager;
    private PlayerDeathListener playerDeathListener;
    private CombatListener combatListener;
    private BountyContractManager bountyContractManager;
    private SecretMissionManager secretMissionManager;
    private TopRoleManager topRoleManager;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        // Database
        String dbFile = this.getConfig().getString("storage.database-file", "teams.db");
        this.database = new Database(this.getDataFolder(), dbFile, this.getLogger());
        this.database.connect();

        // Managers
        this.teamManager = new TeamManager(this, this.database);
        this.playerDataManager = new PlayerDataManager(this.database);
        this.bannedNameManager = new BannedNameManager(this);
        this.bannedNameManager.loadOrCreate();
        this.afkManager = new AfkManager();
        this.combatTagManager = new CombatTagManager();
        this.bountyContractManager = new BountyContractManager();

        // Load persisted data
        this.teamManager.loadAll();
        this.playerDataManager.loadAll();

        // Commands
        this.getCommand("team").setExecutor(new TeamCommand(this));
        this.getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
        this.getCommand("bounty").setExecutor(new BountyCommand(this));
        this.getCommand("afk").setExecutor(new AfkCommand(this));

        // Listeners
        this.playerDeathListener = new PlayerDeathListener(this);
        this.combatListener = new CombatListener(this);
        Bukkit.getPluginManager().registerEvents(this.playerDeathListener, this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(this.combatListener, this);

        // Secret missions
        this.secretMissionManager = new SecretMissionManager(this);
        this.secretMissionManager.start();

        // Vault top-roles (optional)
        this.topRoleManager = new TopRoleManager(this);

        // PlaceholderAPI (optional)
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this).register();
            this.getLogger().info("PlaceholderAPI found - registered %perfectteams_*% placeholders.");
        }

        this.getLogger().info("PerfectTeams enabled - " + this.teamManager.all().size() + " teams loaded.");
    }

    @Override
    public void onDisable() {
        if (this.secretMissionManager != null) {
            this.secretMissionManager.stop();
        }
        if (this.database != null) {
            this.database.disconnect();
        }
    }

    // ---- Getters --------------------------------------------------------------

    public TeamManager getTeamManager() {
        return this.teamManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public Database getDatabase() {
        return this.database;
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

    public CombatListener getCombatListener() {
        return this.combatListener;
    }

    public BountyContractManager getBountyContractManager() {
        return this.bountyContractManager;
    }

    public SecretMissionManager getSecretMissionManager() {
        return this.secretMissionManager;
    }

    public TopRoleManager getTopRoleManager() {
        return this.topRoleManager;
    }
}
