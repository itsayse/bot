package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.session.AuthState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class KeyCommand implements CommandExecutor {
    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d{15,20}$");

    private final KeyAuthPlugin plugin;

    public KeyCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.sessions().get(player.getUniqueId()) != AuthState.AWAITING_KEY) {
            player.sendMessage(plugin.msg("not-now"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("§eUsage: /key <code> <your_discord_user_id>");
            player.sendMessage("§7Need your Discord ID? Enable Developer Mode in Discord, then right-click your profile → Copy User ID.");
            return true;
        }
        String key = args[0].trim();
        String discordId = args[1].trim();

        if (!DISCORD_ID_PATTERN.matcher(discordId).matches()) {
            player.sendMessage(plugin.msg("discord-id-invalid"));
            return true;
        }

        player.sendMessage("§7Checking key...");
        plugin.api().redeem(key, player.getUniqueId().toString(), player.getName(), discordId)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result.networkError) {
                        player.sendMessage(plugin.msg("api-unreachable"));
                        return;
                    }
                    if (result.ok) {
                        plugin.sessions().setPendingKey(player.getUniqueId(), key);
                        plugin.sessions().setPendingDiscordId(player.getUniqueId(), discordId);
                        plugin.sessions().set(player.getUniqueId(), AuthState.AWAITING_REGISTER);
                        player.sendMessage(plugin.msg("need-register"));
                    } else {
                        switch (result.reason) {
                            case "expired" -> player.sendMessage(plugin.msg("key-expired"));
                            case "revoked" -> player.sendMessage(plugin.msg("key-revoked"));
                            case "already_used" -> player.sendMessage(plugin.msg("key-used"));
                            case "bad_discord_id" -> player.sendMessage(plugin.msg("discord-id-invalid"));
                            default -> player.sendMessage(plugin.msg("key-invalid"));
                        }
                    }
                }));
        return true;
    }
}
