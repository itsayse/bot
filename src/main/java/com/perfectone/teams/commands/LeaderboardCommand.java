package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.TeamData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;

public class LeaderboardCommand implements CommandExecutor {

    private final TeamsPlugin plugin;

    public LeaderboardCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = plugin.getConfig().getInt("leaderboard.top-teams-shown", 10);

        List<TeamData> teams = plugin.getTeamManager().all().values().stream()
                .sorted(Comparator.comparingDouble(TeamData::getScore).reversed())
                .limit(limit)
                .toList();

        if (teams.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No teams have scored anything yet.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "======= Team Leaderboard =======");
        int rank = 1;
        for (TeamData t : teams) {
            sender.sendMessage(ChatColor.YELLOW + "#" + rank + " " + plugin.getTeamManager().colorizedPrefix(t)
                    + ChatColor.GRAY + " - " + ChatColor.WHITE + t.getScore() + ChatColor.GRAY + " pts");
            rank++;
        }
        return true;
    }
}
