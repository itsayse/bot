package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /afk - toggles the player's AFK status and announces it. Nothing else:
 * no effect on scoring, chat prefix, or protection from PvP.
 */
public class AfkCommand implements CommandExecutor {

    private final TeamsPlugin plugin;

    public AfkCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        boolean nowAfk = plugin.getAfkManager().toggle(player.getUniqueId());

        if (nowAfk) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " is now AFK.");
        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " is back.");
        }
        return true;
    }
}
