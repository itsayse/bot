/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 */
package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.TeamData;
import java.util.Comparator;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LeaderboardCommand
implements CommandExecutor {
    private final TeamsPlugin plugin;

    public LeaderboardCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = this.plugin.getConfig().getInt("leaderboard.top-teams-shown", 10);
        List<TeamData> teams = this.plugin.getTeamManager().all().values().stream().sorted(Comparator.comparingDouble(TeamData::getScore).reversed()).limit(limit).toList();
        if (teams.isEmpty()) {
            sender.sendMessage(String.valueOf(ChatColor.YELLOW) + "No teams have scored anything yet.");
            return true;
        }
        sender.sendMessage(String.valueOf(ChatColor.GOLD) + "======= Team Leaderboard =======");
        int rank = 1;
        for (TeamData t : teams) {
            sender.sendMessage(String.valueOf(ChatColor.YELLOW) + "#" + rank + " " + this.plugin.getTeamManager().colorizedPrefix(t) + String.valueOf(ChatColor.GRAY) + " - " + String.valueOf(ChatColor.WHITE) + t.getScore() + String.valueOf(ChatColor.GRAY) + " pts");
            ++rank;
        }
        return true;
    }
}

