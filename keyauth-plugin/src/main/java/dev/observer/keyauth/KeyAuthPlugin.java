package dev.observer.keyauth;

import dev.observer.keyauth.commands.ChangeUserCommand;
import dev.observer.keyauth.commands.KeyCommand;
import dev.observer.keyauth.commands.LoginCommand;
import dev.observer.keyauth.commands.RegisterCommand;
import dev.observer.keyauth.data.PlayerDataManager;
import dev.observer.keyauth.listeners.JoinQuitListener;
import dev.observer.keyauth.listeners.ProtectionListener;
import dev.observer.keyauth.net.ApiClient;
import dev.observer.keyauth.session.SessionManager;
import dev.observer.keyauth.tasks.PeriodicKeyCheckTask;
import org.bukkit.plugin.java.JavaPlugin;

public class KeyAuthPlugin extends JavaPlugin {
    private PlayerDataManager playerData;
    private SessionManager sessions;
    private ApiClient apiClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String baseUrl = getConfig().getString("api.base-url", "http://127.0.0.1:8787");
        String secret = getConfig().getString("api.secret", "");
        if (secret == null || secret.isBlank() || secret.equals("change-this-to-match-your-bot-env")) {
            getLogger().warning("api.secret in config.yml has not been set — key verification will fail! "
                    + "Set it to match MC_API_SECRET in the Discord bot's .env.");
        }

        this.playerData = new PlayerDataManager(this);
        this.sessions = new SessionManager();
        this.apiClient = new ApiClient(baseUrl, secret);

        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);

        getCommand("key").setExecutor(new KeyCommand(this));
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("changeuser").setExecutor(new ChangeUserCommand(this));

        long intervalTicks = getConfig().getLong("auth.key-check-interval-minutes", 5) * 60L * 20L;
        new PeriodicKeyCheckTask(this).runTaskTimer(this, intervalTicks, intervalTicks);

        getLogger().info("KeyAuth enabled.");
    }

    @Override
    public void onDisable() {
        if (playerData != null) {
            playerData.save();
        }
    }

    public PlayerDataManager playerData() {
        return playerData;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public ApiClient api() {
        return apiClient;
    }

    public String msg(String key) {
        String raw = getConfig().getString("messages." + key, key);
        return raw.replace('&', '§');
    }
}
