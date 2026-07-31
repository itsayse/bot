package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import dev.observer.keyauth.util.PasswordUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {
    private final KeyAuthPlugin plugin;

    public LoginCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.sessions().get(player.getUniqueId()) != AuthState.AWAITING_LOGIN) {
            player.sendMessage(plugin.msg("not-now"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§eUsage: /login <password>");
            return true;
        }

        PlayerRecord rec = plugin.playerData().get(player.getUniqueId());
        if (rec == null) {
            // shouldn't happen, but fail safe back to the key flow
            plugin.sessions().set(player.getUniqueId(), AuthState.AWAITING_KEY);
            player.sendMessage(plugin.msg("need-key"));
            return true;
        }

        if (!PasswordUtil.matches(args[0], rec.salt, rec.passwordHash)) {
            int attempts = plugin.sessions().incrementLoginAttempts(player.getUniqueId());
            int max = plugin.getConfig().getInt("auth.max-login-attempts", 5);
            if (attempts >= max) {
                player.kickPlayer(plugin.msg("too-many-attempts"));
            } else {
                player.sendMessage(plugin.msg("wrong-password").replace("%attempts%", String.valueOf(max - attempts)));
            }
            return true;
        }

        plugin.sessions().resetLoginAttempts(player.getUniqueId());
        plugin.sessions().set(player.getUniqueId(), AuthState.AUTHENTICATED);
        player.sendMessage(plugin.msg("login-success"));

        // now that they're in, confirm their key hasn't expired/been revoked since last time
        plugin.api().check(player.getUniqueId().toString())
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result.networkError) {
                        return; // don't punish players for the API being briefly unreachable
                    }
                    if (!result.ok) {
                        if ("revoked".equals(result.reason)) {
                            player.kickPlayer(plugin.msg("key-check-failed-revoked"));
                            return;
                        }
                        // Expired (or record missing) — don't kick. Start a grace
                        // window and let them self-renew with /renewkey <code>.
                        long graceMs = plugin.getConfig().getLong("auth.expired-grace-minutes", 10) * 60_000L;
                        plugin.sessions().startKeyGrace(player.getUniqueId(), System.currentTimeMillis() + graceMs);
                        player.sendMessage(plugin.msg("key-expired-grace"));
                    } else {
                        plugin.sessions().clearKeyGrace(player.getUniqueId());
                    }
                }));
        return true;
    }
}
