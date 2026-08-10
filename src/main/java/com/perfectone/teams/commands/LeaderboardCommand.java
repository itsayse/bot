package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.util.MoneyUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LeaderboardCommand implements CommandExecutor {

    private final TeamsPlugin plugin;

    public LeaderboardCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = this.plugin.getConfig().getInt("leaderboard.top-teams-shown", 10);

        // Sort by score, then fall back to team bounty total for ties.
        List<TeamData> teams = this.plugin.getTeamManager().all().values().stream()
                .sorted(Comparator.comparingDouble(TeamData::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        sender.sendMessage(ChatColor.GOLD + "══════ 🏆 Team Leaderboard 🏆 ══════");

        if (teams.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No teams have scored anything yet.");
            return true;
        }

        int rank = 1;
        for (TeamData t : teams) {
            String teamKey = t.getKey();

            // Team bounty = sum of all members' bounties.
            double teamBounty = this.plugin.getPlayerDataManager().all().values().stream()
                    .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                    .mapToDouble(PlayerData::getBounty)
                    .sum();

            long memberCount = this.plugin.getPlayerDataManager().all().values().stream()
                    .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                    .count();

            int score = (int) t.getScore(); // Display as integer

            sender.sendMessage(
                    ChatColor.YELLOW + "#" + rank + " "
                    + this.plugin.getTeamManager().colorizedPrefix(t)
                    + ChatColor.GRAY + " - "
                    + ChatColor.WHITE + score + ChatColor.GRAY + " pts"
                    + ChatColor.GRAY + " | Bounty: " + ChatColor.GOLD + MoneyUtil.format(teamBounty)
                    + ChatColor.GRAY + " | Members: " + ChatColor.WHITE + memberCount);
            rank++;
        }
        return true;
    }
}
