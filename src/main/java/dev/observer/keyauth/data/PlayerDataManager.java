package dev.observer.keyauth.data;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerDataManager {
   private final JavaPlugin plugin;
   private final File file;
   private FileConfiguration data;

   public PlayerDataManager(JavaPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "players.yml");
      this.load();
   }

   private void load() {
      if (!this.file.exists()) {
         this.plugin.getDataFolder().mkdirs();

         try {
            this.file.createNewFile();
         } catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not create players.yml", e);
         }
      }

      this.data = YamlConfiguration.loadConfiguration(this.file);
   }

   public synchronized void save() {
      try {
         this.data.save(this.file);
      } catch (IOException e) {
         this.plugin.getLogger().log(Level.SEVERE, "Could not save players.yml", e);
      }

   }

   public synchronized boolean hasKeyOnFile(UUID uuid) {
      return this.data.contains(uuid.toString());
   }

   public synchronized PlayerRecord get(UUID uuid) {
      String path = uuid.toString();
      if (!this.data.contains(path)) {
         return null;
      } else {
         PlayerRecord rec = new PlayerRecord();
         rec.uuid = uuid.toString();
         rec.keyUsed = this.data.getString(path + ".keyUsed");
         rec.registeredAt = this.data.getLong(path + ".registeredAt");
         rec.discordId = this.data.getString(path + ".discordId");
         return rec;
      }
   }

   public synchronized void save(PlayerRecord rec) {
      String path = rec.uuid;
      this.data.set(path + ".keyUsed", rec.keyUsed);
      this.data.set(path + ".registeredAt", rec.registeredAt);
      this.data.set(path + ".discordId", rec.discordId);
      this.save();
   }
}
