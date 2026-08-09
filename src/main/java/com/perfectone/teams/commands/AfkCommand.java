/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AfkCommand
implements CommandExecutor {
    private final TeamsPlugin plugin;

    public AfkCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        Player player = (Player)sender;
        boolean nowAfk = this.plugin.getAfkManager().toggle(player.getUniqueId());
        if (nowAfk) {
            Bukkit.broadcastMessage((String)(String.valueOf(ChatColor.YELLOW) + player.getName() + " is now AFK."));
        } else {
            Bukkit.broadcastMessage((String)(String.valueOf(ChatColor.YELLOW) + player.getName() + " is back."));
        }
        return true;
    }
}

