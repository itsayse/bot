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
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BountyCommand
implements CommandExecutor {
    private final TeamsPlugin plugin;

    public BountyCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = this.plugin.getConfig().getInt("leaderboard.top-bounty-shown", 10);
        List<PlayerData> players = this.plugin.getPlayerDataManager().all().values().stream().filter(p -> p.getBounty() > 0.0).sorted(Comparator.comparingDouble(PlayerData::getBounty).reversed()).limit(limit).toList();
        if (players.isEmpty()) {
            sender.sendMessage(String.valueOf(ChatColor.YELLOW) + "No bounties have been earned yet.");
            return true;
        }
        sender.sendMessage(String.valueOf(ChatColor.GOLD) + "======= Top Bounty Hunters =======");
        int rank = 1;
        for (PlayerData p2 : players) {
            Optional<TeamData> team;
            Object teamLabel = String.valueOf(ChatColor.GRAY) + "(Civilian)";
            if (!p2.isCivilian() && (team = this.plugin.getTeamManager().getTeam(p2.getTeamKey())).isPresent()) {
                teamLabel = this.plugin.getTeamManager().colorizedPrefix(team.get());
            }
            sender.sendMessage(String.valueOf(ChatColor.YELLOW) + "#" + rank + " " + String.valueOf(ChatColor.WHITE) + p2.getUsername() + String.valueOf(ChatColor.GRAY) + " - " + String.valueOf(ChatColor.GOLD) + p2.getBounty() + " bounty " + String.valueOf(ChatColor.GRAY) + "| " + p2.getKills() + " kills " + (String)teamLabel);
            ++rank;
        }
        return true;
    }
}

