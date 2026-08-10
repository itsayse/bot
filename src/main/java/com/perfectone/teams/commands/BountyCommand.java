package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.util.MoneyUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BountyCommand implements CommandExecutor {

    private final TeamsPlugin plugin;

    public BountyCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("hunt")) {
            return this.handleHunt(sender, args);
        }
        this.showBountyLeaderboard(sender);
        return true;
    }

    // ---- Bounty leaderboard ---------------------------------------------------

    private void showBountyLeaderboard(CommandSender sender) {
        int limit = this.plugin.getConfig().getInt("leaderboard.top-bounty-shown", 10);

        List<PlayerData> players = this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> p.getBounty() > 0.0)
                .sorted(Comparator.comparingDouble(PlayerData::getBounty).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        sender.sendMessage(ChatColor.GOLD + "══════ ☠ Top Bounties ☠ ══════");

        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No bounties have been earned yet.");
            return;
        }

        int rank = 1;
        for (PlayerData p : players) {
            String teamLabel;
            if (!p.isCivilian()) {
                Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(p.getTeamKey());
                teamLabel = teamOpt.map(t -> this.plugin.getTeamManager().colorizedPrefix(t))
                                   .orElse(ChatColor.GRAY + "(no team)");
            } else {
                teamLabel = ChatColor.GRAY + "(Civilian)";
            }

            String kdDisplay = ChatColor.GRAY + " " + p.getKills() + "K/" + p.getDeaths() + "D";

            sender.sendMessage(
                    ChatColor.YELLOW + "#" + rank + " "
                    + ChatColor.WHITE + p.getUsername()
                    + ChatColor.GRAY + " - "
                    + ChatColor.GOLD + MoneyUtil.format(p.getBounty())
                    + kdDisplay + " "
                    + teamLabel);
            rank++;
        }
    }

    // ---- /bounty hunt <player> ------------------------------------------------

    private boolean handleHunt(CommandSender sender, String[] args) {
        if (!(sender instanceof Player hunter)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        if (args.length < 2) {
            hunter.sendMessage(ChatColor.RED + "Usage: /bounty hunt <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            hunter.sendMessage(ChatColor.RED + "Player '" + args[1] + "' isn't online.");
            return true;
        }
        if (target.getUniqueId().equals(hunter.getUniqueId())) {
            hunter.sendMessage(ChatColor.RED + "You can't take a contract on yourself.");
            return true;
        }

        long durationSeconds = this.plugin.getConfig().getLong("bounty-hunter.contract-duration-seconds", 600L);
        double bonus = this.plugin.getConfig().getDouble("bounty-hunter.bonus-bounty", 200.0);

        this.plugin.getBountyContractManager().issue(
                hunter.getUniqueId(), target.getUniqueId(), target.getName(), durationSeconds);

        long displayMinutes = durationSeconds / 60;

        // Private confirmation to the hunter.
        hunter.sendMessage(
                ChatColor.GOLD + "Contract accepted: eliminate "
                + ChatColor.WHITE + target.getName()
                + ChatColor.GOLD + " within " + displayMinutes + " minutes for a bonus +"
                + MoneyUtil.format(bonus) + " bounty.");

        // Server-wide announcement.
        Bukkit.broadcastMessage(
                ChatColor.RED + "☠ BOUNTY CONTRACT "
                + ChatColor.GOLD + hunter.getName()
                + ChatColor.GRAY + " has accepted a contract on "
                + ChatColor.GOLD + target.getName()
                + ChatColor.GRAY + "! +" + ChatColor.YELLOW + MoneyUtil.format(bonus)
                + ChatColor.GRAY + " if they succeed.");

        return true;
    }
}
