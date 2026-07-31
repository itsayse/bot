package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Lets an already-authenticated player swap their currently-bound key for a
 * different one — e.g. their old key expired and staff handed them a fresh
 * code. Carries the Discord link over automatically. If they were in the
 * post-expiry grace window, a successful renewal clears it and they keep
 * playing without ever being kicked.
 */
public class RenewKeyCommand implements CommandExecutor {

    private final KeyAuthPlugin plugin;

    public RenewKeyCommand(KeyAuthPlugin plugin) {
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
            player.sendMessage("§eUsage: /renewkey <newCode>");
            return true;
        }
        String newKey = args[0].trim();

        player.sendMessage("§7Checking new key...");
        plugin.api().renew(player.getUniqueId().toString(), newKey, player.getName())
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
                            rec.keyUsed = newKey;
                            plugin.playerData().save(rec);
                        }
                        plugin.sessions().clearKeyGrace(player.getUniqueId());
                        player.sendMessage(plugin.msg("renewkey-success"));
                    } else {
                        switch (result.reason) {
                            case "expired" -> player.sendMessage(plugin.msg("key-expired"));
                            case "revoked" -> player.sendMessage(plugin.msg("key-revoked"));
                            case "already_used" -> player.sendMessage(plugin.msg("key-used"));
                            default -> player.sendMessage(plugin.msg("key-invalid"));
                        }
                    }
                }));
        return true;
    }
}
