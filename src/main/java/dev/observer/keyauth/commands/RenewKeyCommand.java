package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RenewKeyCommand implements CommandExecutor {
   private final KeyAuthPlugin plugin;

   public RenewKeyCommand(KeyAuthPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         AuthState state = this.plugin.sessions().get(player.getUniqueId());
         if (state != AuthState.AUTHENTICATED && state != AuthState.KEY_LOCKED) {
            player.sendMessage(this.plugin.msg("still-locked"));
            return true;
         } else if (args.length != 1) {
            player.sendMessage("§eUsage: /renewkey <newCode>");
            return true;
         } else {
            String newKey = args[0].trim();
            player.sendMessage("§7Checking new key...");
            this.plugin.api().renew(player.getUniqueId().toString(), newKey, player.getName()).thenAccept((result) -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                  if (player.isOnline()) {
                     if (result.networkError) {
                        player.sendMessage(this.plugin.msg("api-unreachable"));
                     } else {
                        if (result.ok) {
                           PlayerRecord rec = this.plugin.playerData().get(player.getUniqueId());
                           if (rec != null) {
                              rec.keyUsed = newKey;
                              this.plugin.playerData().save(rec);
                           }

                           this.plugin.sessions().clearKeyGrace(player.getUniqueId());
                           this.plugin.sessions().set(player.getUniqueId(), AuthState.AUTHENTICATED);
                           player.sendMessage(this.plugin.msg("renewkey-success"));
                        } else {
                           switch (result.reason) {
                              case "expired" -> player.sendMessage(this.plugin.msg("key-expired"));
                              case "revoked" -> player.sendMessage(this.plugin.msg("key-revoked"));
                              case "already_used" -> player.sendMessage(this.plugin.msg("key-used"));
                              default -> player.sendMessage(this.plugin.msg("key-invalid"));
                           }
                        }

                     }
                  }
               }));
            return true;
         }
      } else {
         sender.sendMessage("Players only.");
         return true;
      }
   }
}
