package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import java.util.regex.Pattern;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChangeUserCommand implements CommandExecutor {
   private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d{15,20}$");
   private final KeyAuthPlugin plugin;

   public ChangeUserCommand(KeyAuthPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (this.plugin.sessions().get(player.getUniqueId()) != AuthState.AUTHENTICATED) {
            player.sendMessage(this.plugin.msg("still-locked"));
            return true;
         } else if (args.length != 1) {
            player.sendMessage("§eUsage: /changeuser <your_new_discord_user_id>");
            return true;
         } else {
            String newDiscordId = args[0].trim();
            if (!DISCORD_ID_PATTERN.matcher(newDiscordId).matches()) {
               player.sendMessage(this.plugin.msg("discord-id-invalid"));
               return true;
            } else {
               player.sendMessage("§7Updating linked Discord account...");
               this.plugin.api().changeUser(player.getUniqueId().toString(), newDiscordId).thenAccept((result) -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                     if (player.isOnline()) {
                        if (result.networkError) {
                           player.sendMessage(this.plugin.msg("api-unreachable"));
                        } else {
                           if (result.ok) {
                              PlayerRecord rec = this.plugin.playerData().get(player.getUniqueId());
                              if (rec != null) {
                                 rec.discordId = newDiscordId;
                                 this.plugin.playerData().save(rec);
                              }

                              player.sendMessage(this.plugin.msg("changeuser-success"));
                           } else {
                              player.sendMessage(this.plugin.msg("changeuser-failed"));
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
