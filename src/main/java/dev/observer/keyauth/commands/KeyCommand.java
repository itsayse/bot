package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import java.util.regex.Pattern;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KeyCommand implements CommandExecutor {
   private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d{15,20}$");
   private final KeyAuthPlugin plugin;

   public KeyCommand(KeyAuthPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         AuthState state = this.plugin.sessions().get(player.getUniqueId());
         if (state != AuthState.AWAITING_KEY && state != AuthState.KEY_LOCKED) {
            player.sendMessage(this.plugin.msg("not-now"));
            return true;
         } else if (args.length != 2) {
            player.sendMessage("§eUsage: /key <code> <your_discord_user_id>");
            player.sendMessage("§7Need your Discord ID? Enable Developer Mode in Discord, then right-click your profile → Copy User ID.");
            return true;
         } else {
            String key = args[0].trim();
            String discordId = args[1].trim();
            if (!DISCORD_ID_PATTERN.matcher(discordId).matches()) {
               player.sendMessage(this.plugin.msg("discord-id-invalid"));
               return true;
            } else {
               player.sendMessage("§7Checking key...");
               this.plugin.api().redeem(key, player.getUniqueId().toString(), player.getName(), discordId).thenAccept((result) -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                     if (player.isOnline()) {
                        if (result.networkError) {
                           player.sendMessage(this.plugin.msg("api-unreachable"));
                        } else {
                           if (result.ok) {
                              PlayerRecord rec = new PlayerRecord(player.getUniqueId().toString(), key, System.currentTimeMillis(), discordId);
                              this.plugin.playerData().save(rec);
                              this.plugin.sessions().clearKeyGrace(player.getUniqueId());
                              this.plugin.sessions().set(player.getUniqueId(), AuthState.AUTHENTICATED);
                              player.sendMessage(this.plugin.msg("key-success"));
                           } else {
                              switch (result.reason) {
                                 case "expired" -> player.sendMessage(this.plugin.msg("key-expired"));
                                 case "revoked" -> player.sendMessage(this.plugin.msg("key-revoked"));
                                 case "already_used" -> player.sendMessage(this.plugin.msg("key-used"));
                                 case "bad_discord_id" -> player.sendMessage(this.plugin.msg("discord-id-invalid"));
                                 default -> player.sendMessage(this.plugin.msg("key-invalid"));
                              }
                           }

                        }
                     }
                  }));
               return true;
            }
         }
      } else {
         sender.sendMessage("Players only.");
         return true;
      }
   }
}
