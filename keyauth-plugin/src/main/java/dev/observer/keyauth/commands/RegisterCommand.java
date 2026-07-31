package dev.observer.keyauth.commands;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import dev.observer.keyauth.util.PasswordUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {
    private final KeyAuthPlugin plugin;

    public RegisterCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.sessions().get(player.getUniqueId()) != AuthState.AWAITING_REGISTER) {
            player.sendMessage(plugin.msg("not-now"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("§eUsage: /register <password> <confirmPassword>");
            return true;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage("§cPasswords don't match.");
            return true;
        }
        if (args[0].length() < 6) {
            player.sendMessage("§cPassword must be at least 6 characters.");
            return true;
        }

        String salt = PasswordUtil.randomSalt();
        String hash = PasswordUtil.hash(args[0], salt);
        String key = plugin.sessions().getPendingKey(player.getUniqueId());
        String discordId = plugin.sessions().getPendingDiscordId(player.getUniqueId());

        PlayerRecord rec = new PlayerRecord(player.getUniqueId().toString(), hash, salt, key, System.currentTimeMillis(), discordId);
        plugin.playerData().save(rec);

        plugin.sessions().set(player.getUniqueId(), AuthState.AUTHENTICATED);
        player.sendMessage(plugin.msg("login-success"));
        return true;
    }
}
