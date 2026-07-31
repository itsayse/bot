package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

/**
 * Lets an already-authenticated player point their account at a different
 * Discord user id. The bot removes the key role from the old id and grants
 * it to the new one (if the key is still valid).
 */
public class ChangeUserCommand implements CommandExecutor {
    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d{15,20}$");

    private final KeyAuthPlugin plugin;

    public ChangeUserCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.sessions().get(player.getUniqueId()) != AuthState.AUTHENTICATED) {
            player.sendMessage(plugin.msg("still-locked"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§eUsage: /changeuser <your_new_discord_user_id>");
            return true;
        }
        String newDiscordId = args[0].trim();
        if (!DISCORD_ID_PATTERN.matcher(newDiscordId).matches()) {
            player.sendMessage(plugin.msg("discord-id-invalid"));
            return true;
        }

        player.sendMessage("§7Updating linked Discord account...");
        plugin.api().changeUser(player.getUniqueId().toString(), newDiscordId)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result.networkError) {
                        player.sendMessage(plugin.msg("api-unreachable"));
                        return;
                    }
                    if (result.ok) {
                        PlayerRecord rec = plugin.playerData().get(player.getUniqueId());
                        if (rec != null) {
                            rec.discordId = newDiscordId;
                            plugin.playerData().save(rec);
                        }
                        player.sendMessage(plugin.msg("changeuser-success"));
                    } else {
                        player.sendMessage(plugin.msg("changeuser-failed"));
                    }
                }));
        return true;
    }
}
