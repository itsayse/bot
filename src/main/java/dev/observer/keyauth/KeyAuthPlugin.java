package dev.observer.keyauth;

import dev.observer.keyauth.commands.ChangeUserCommand;
import dev.observer.keyauth.commands.KeyCommand;
import dev.observer.keyauth.commands.RenewKeyCommand;
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

   public void onEnable() {
      this.saveDefaultConfig();
      String baseUrl = this.getConfig().getString("api.base-url", "http://127.0.0.1:8787");
      String secret = this.getConfig().getString("api.secret", "");
      if (secret == null || secret.isBlank() || secret.equals("change-this-to-match-your-bot-env")) {
         this.getLogger().warning("api.secret in config.yml has not been set — key verification will fail! Set it to match MC_API_SECRET in the Discord bot's .env.");
      }

      this.playerData = new PlayerDataManager(this);
      this.sessions = new SessionManager();
      this.apiClient = new ApiClient(baseUrl, secret);
      this.getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
      this.getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
      this.getCommand("key").setExecutor(new KeyCommand(this));
      this.getCommand("changeuser").setExecutor(new ChangeUserCommand(this));
      this.getCommand("renewkey").setExecutor(new RenewKeyCommand(this));
      long intervalTicks = this.getConfig().getLong("auth.key-check-interval-minutes", 5L) * 60L * 20L;
      (new PeriodicKeyCheckTask(this)).runTaskTimer(this, intervalTicks, intervalTicks);
      this.getLogger().info("KeyAuth enabled.");
   }

   public void onDisable() {
      if (this.playerData != null) {
         this.playerData.save();
      }

   }

   public PlayerDataManager playerData() {
      return this.playerData;
   }

   public SessionManager sessions() {
      return this.sessions;
   }

   public ApiClient api() {
      return this.apiClient;
   }

   public String msg(String key) {
      String raw = this.getConfig().getString("messages." + key, key);
      return raw.replace('&', '§');
   }
}
