package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Lets a player swap their currently-bound key for a different one — e.g.
 * their old key expired/was revoked and staff handed them a fresh code.
 * Carries the Discord link over automatically. Works both for a still-active
 * player renewing proactively, and for a player who's been frozen
 * (KEY_LOCKED) after a periodic check caught an expired/revoked key — a
 * successful renewal here is what unfreezes them.
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
        AuthState state = plugin.sessions().get(player.getUniqueId());
        if (state != AuthState.AUTHENTICATED && state != AuthState.KEY_LOCKED) {
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
                        // Un-freezes the player if they were KEY_LOCKED — this is what
                        // lets them move/break/interact again.
                        plugin.sessions().set(player.getUniqueId(), AuthState.AUTHENTICATED);
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
