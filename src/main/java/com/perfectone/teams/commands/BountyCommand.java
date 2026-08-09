package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class BountyCommand implements CommandExecutor {

    private final TeamsPlugin plugin;

    public BountyCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = plugin.getConfig().getInt("leaderboard.top-bounty-shown", 10);

        List<PlayerData> players = plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> p.getBounty() > 0)
                .sorted(Comparator.comparingDouble(PlayerData::getBounty).reversed())
                .limit(limit)
                .toList();

        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No bounties have been earned yet.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "======= Top Bounty Hunters =======");
        int rank = 1;
        for (PlayerData p : players) {
            String teamLabel = ChatColor.GRAY + "(Civilian)";
            if (!p.isCivilian()) {
                Optional<TeamData> team = plugin.getTeamManager().getTeam(p.getTeamKey());
                if (team.isPresent()) {
                    teamLabel = plugin.getTeamManager().colorizedPrefix(team.get());
                }
            }
            sender.sendMessage(ChatColor.YELLOW + "#" + rank + " " + ChatColor.WHITE + p.getUsername()
                    + ChatColor.GRAY + " - " + ChatColor.GOLD + p.getBounty() + " bounty "
                    + ChatColor.GRAY + "| " + p.getKills() + " kills " + teamLabel);
            rank++;
        }
        return true;
    }
}
