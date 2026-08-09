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
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.manager.TeamManager;
import com.perfectone.teams.util.ColorUtil;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamCommand
implements CommandExecutor {
    private final TeamsPlugin plugin;

    public TeamCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0) {
            this.sendUsage(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create": {
                this.handleCreate(player, args);
                break;
            }
            case "join": {
                this.handleJoin(player, args);
                break;
            }
            case "leave": {
                this.handleLeave(player);
                break;
            }
            case "disband": {
                this.handleDisband(player);
                break;
            }
            case "color": 
            case "colour": {
                this.handleColor(player, args);
                break;
            }
            case "info": {
                this.handleInfo(player, args);
                break;
            }
            case "list": {
                this.handleList(player);
                break;
            }
            case "invite": {
                this.handleInvite(player, args);
                break;
            }
            case "uninvite": {
                this.handleUninvite(player, args);
                break;
            }
            case "setmode": 
            case "mode": {
                this.handleSetMode(player, args);
                break;
            }
            case "reloadbanlist": {
                this.handleReloadBanList(player);
                break;
            }
            case "ally": {
                this.handleAlly(player, args);
                break;
            }
            case "unally": {
                this.handleUnally(player, args);
                break;
            }
            case "allies": {
                this.handleAllies(player);
                break;
            }
            default: {
                this.sendUsage(player);
            }
        }
        return true;
    }

    private void handleReloadBanList(Player player) {
        if (!player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You don't have permission to do that.");
            return;
        }
        this.plugin.getBannedNameManager().reload();
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "Reloaded bad.txt - " + this.plugin.getBannedNameManager().size() + " banned words loaded.");
    }

    private void sendUsage(Player player) {
        player.sendMessage(String.valueOf(ChatColor.GOLD) + "--- Team Commands ---");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team create <name>");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team join <name>");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team leave");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team disband");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team color <color>");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team info [name]");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team list");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team setmode <open|invite>" + String.valueOf(ChatColor.GRAY) + " - owner only");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team invite <player>" + String.valueOf(ChatColor.GRAY) + " - owner only, invite-only teams");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team uninvite <player>" + String.valueOf(ChatColor.GRAY) + " - owner only");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team ally <team>" + String.valueOf(ChatColor.GRAY) + " - owner only, propose/accept an alliance");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team unally <team>" + String.valueOf(ChatColor.GRAY) + " - owner only, breaks an alliance");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team allies" + String.valueOf(ChatColor.GRAY) + " - list your team's allies");
        if (player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.YELLOW) + "/team reloadbanlist" + String.valueOf(ChatColor.GRAY) + " - reload bad.txt");
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /team create <name>");
            return;
        }
        String name = args[1];
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (!data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're already in a team. Leave it first with /team leave.");
            return;
        }
        int min = this.plugin.getConfig().getInt("team.min-name-length", 3);
        int max = this.plugin.getConfig().getInt("team.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Team name must be between " + min + " and " + max + " characters.");
            return;
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Team names can only contain letters, numbers, and underscores.");
            return;
        }
        if (this.plugin.getBannedNameManager().isBanned(name)) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "That team name isn't allowed. Pick a different name.");
            return;
        }
        if (this.plugin.getTeamManager().exists(name)) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "A team named '" + name + "' already exists. Pick a different name.");
            return;
        }
        TeamData team = this.plugin.getTeamManager().createTeam(name, player.getUniqueId());
        data.setTeamKey(team.getKey());
        this.plugin.getPlayerDataManager().save(data);
        this.plugin.getTeamManager().addPlayerToScoreboardTeam(team, player.getName());
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "Team '" + team.getDisplayName() + "' created! You are the owner.");
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /team join <name>");
            return;
        }
        String name = args[1];
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (!data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're already in a team. Leave it first with /team leave.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(name);
        if (teamOpt.isEmpty()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "No team named '" + name + "' exists.");
            return;
        }
        TeamData team = teamOpt.get();
        if (team.isInviteOnly() && !player.hasPermission("teams.admin")) {
            if (!this.plugin.getTeamManager().isInvited(team, player.getUniqueId())) {
                player.sendMessage(String.valueOf(ChatColor.RED) + this.plugin.getTeamManager().colorizedPrefix(team) + String.valueOf(ChatColor.RED) + " is invite-only. Ask the owner for an invite.");
                return;
            }
            this.plugin.getTeamManager().clearInvite(team, player.getUniqueId());
        }
        data.setTeamKey(team.getKey());
        this.plugin.getPlayerDataManager().save(data);
        this.plugin.getTeamManager().addPlayerToScoreboardTeam(team, player.getName());
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "You joined " + this.plugin.getTeamManager().colorizedPrefix(team));
    }

    private void handleLeave(Player player) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        String teamKey = data.getTeamKey();
        data.setTeamKey(null);
        this.plugin.getPlayerDataManager().save(data);
        this.plugin.getTeamManager().removePlayerFromAllScoreboardTeams(player.getName());
        teamOpt.ifPresent(team -> {
            if (team.getOwner().equals(player.getUniqueId())) {
                Optional<PlayerData> nextMember = this.plugin.getPlayerDataManager().all().values().stream().filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey())).findFirst();
                if (nextMember.isPresent()) {
                    team.setOwner(nextMember.get().getUuid());
                    this.plugin.getTeamManager().saveTeam((TeamData)team);
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Ownership transferred to " + nextMember.get().getUsername());
                } else {
                    this.plugin.getTeamManager().disbandTeam((TeamData)team);
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "You were the last member - the team was disbanded.");
                }
            }
        });
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "You left your team.");
    }

    private void handleDisband(Player player) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Team not found.");
            return;
        }
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Only the team owner can disband the team.");
            return;
        }
        String teamKey = team.getKey();
        this.plugin.getPlayerDataManager().all().values().stream().filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey())).forEach(p -> {
            p.setTeamKey(null);
            this.plugin.getPlayerDataManager().save((PlayerData)p);
            this.plugin.getTeamManager().removePlayerFromAllScoreboardTeams(p.getUsername());
        });
        this.plugin.getTeamManager().disbandTeam(team);
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "Team '" + team.getDisplayName() + "' has been disbanded.");
    }

    private void handleColor(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /team color <color>");
            player.sendMessage(String.valueOf(ChatColor.GRAY) + "Available: " + ColorUtil.namesList());
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            return;
        }
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Only the team owner can change the team color.");
            return;
        }
        ChatColor color = ColorUtil.parse(args[1]);
        if (color == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Invalid color. Available: " + ColorUtil.namesList());
            return;
        }
        team.setPrefixColor(color.name());
        this.plugin.getTeamManager().saveTeam(team);
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "Team color updated: " + this.plugin.getTeamManager().colorizedPrefix(team));
    }

    private void handleInfo(Player player, String[] args) {
        String targetName;
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        String string = targetName = args.length >= 2 ? args[1] : data.getTeamKey();
        if (targetName == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team. Use /team info <name> to check another team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(targetName);
        if (teamOpt.isEmpty()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "No such team.");
            return;
        }
        TeamData team = teamOpt.get();
        long memberCount = this.plugin.getPlayerDataManager().all().values().stream().filter(p -> team.getKey().equalsIgnoreCase(p.getTeamKey())).count();
        player.sendMessage(String.valueOf(ChatColor.GOLD) + "--- " + this.plugin.getTeamManager().colorizedPrefix(team) + String.valueOf(ChatColor.GOLD) + " ---");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Score: " + String.valueOf(ChatColor.WHITE) + team.getScore());
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Members: " + String.valueOf(ChatColor.WHITE) + memberCount);
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Join mode: " + String.valueOf(ChatColor.WHITE) + team.getJoinMode().name().toLowerCase());
    }

    private void handleList(Player player) {
        List<TeamData> teams = this.plugin.getTeamManager().all().values().stream().toList();
        if (teams.isEmpty()) {
            player.sendMessage(String.valueOf(ChatColor.YELLOW) + "No teams exist yet. Create one with /team create <name>!");
            return;
        }
        player.sendMessage(String.valueOf(ChatColor.GOLD) + "--- Teams (" + teams.size() + ") ---");
        for (TeamData t : teams) {
            String modeTag = t.isInviteOnly() ? String.valueOf(ChatColor.GRAY) + " [invite-only]" : "";
            player.sendMessage(this.plugin.getTeamManager().colorizedPrefix(t) + String.valueOf(ChatColor.GRAY) + " - score: " + t.getScore() + modeTag);
        }
    }

    private void handleSetMode(Player player, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("open") && !args[1].equalsIgnoreCase("invite")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /team setmode <open|invite>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            return;
        }
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Only the team owner can change the join mode.");
            return;
        }
        TeamData.JoinMode mode = args[1].equalsIgnoreCase("invite") ? TeamData.JoinMode.INVITE : TeamData.JoinMode.OPEN;
        team.setJoinMode(mode);
        this.plugin.getTeamManager().saveTeam(team);
        player.sendMessage(String.valueOf(ChatColor.GREEN) + this.plugin.getTeamManager().colorizedPrefix(team) + String.valueOf(ChatColor.GREEN) + " is now " + (mode == TeamData.JoinMode.OPEN ? "open - anyone can /team join it." : "invite-only - players need /team invite from you first."));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /team invite <player>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            return;
        }
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Only the team owner can send invites.");
            return;
        }
        Player target = Bukkit.getPlayerExact((String)args[1]);
        if (target == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Player '" + args[1] + "' isn't online.");
            return;
        }
        PlayerData targetData = this.plugin.getPlayerDataManager().getOrCreate(target);
        if (!targetData.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + target.getName() + " is already in a team.");
            return;
        }
        this.plugin.getTeamManager().invite(team, target.getUniqueId());
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "Invited " + target.getName() + " to " + this.plugin.getTeamManager().colorizedPrefix(team));
        target.sendMessage(String.valueOf(ChatColor.GREEN) + "You've been invited to join " + this.plugin.getTeamManager().colorizedPrefix(team) + String.valueOf(ChatColor.GREEN) + "! Use " + String.valueOf(ChatColor.YELLOW) + "/team join " + team.getDisplayName() + String.valueOf(ChatColor.GREEN) + " to accept.");
    }

    private void handleUninvite(Player player, String[] args) {
        UUID targetId;
        if (args.length < 2) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /team uninvite <player>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            return;
        }
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Only the team owner can revoke invites.");
            return;
        }
        Player target = Bukkit.getPlayerExact((String)args[1]);
        UUID uUID = targetId = target != null ? target.getUniqueId() : null;
        if (targetId == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Player '" + args[1] + "' isn't online, so their invite can't be looked up right now.");
            return;
        }
        this.plugin.getTeamManager().uninvite(team, targetId);
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "Revoked " + args[1] + "'s invite.");
    }

    private TeamData requireOwnedTeamAndTarget(Player player, String[] args, String usage) {
        if (args.length < 2) {
            player.sendMessage(String.valueOf(ChatColor.RED) + usage);
            return null;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return null;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            return null;
        }
        TeamData ourTeam = teamOpt.get();
        if (!ourTeam.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Only the team owner can manage alliances.");
            return null;
        }
        return ourTeam;
    }

    private void handleAlly(Player player, String[] args) {
        TeamData ourTeam = this.requireOwnedTeamAndTarget(player, args, "Usage: /team ally <team>");
        if (ourTeam == null) {
            return;
        }
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[1]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "No team named '" + args[1] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        TeamManager.AllyResult result = this.plugin.getTeamManager().proposeAlly(ourTeam, targetTeam);
        switch (result) {
            case CANNOT_ALLY_SELF: {
                player.sendMessage(String.valueOf(ChatColor.RED) + "You can't ally your own team.");
                break;
            }
            case ALREADY_ALLIED: {
                player.sendMessage(String.valueOf(ChatColor.YELLOW) + "You're already allied with " + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + String.valueOf(ChatColor.YELLOW) + ".");
                break;
            }
            case NOW_ALLIED: {
                Bukkit.broadcastMessage((String)(String.valueOf(ChatColor.AQUA) + this.plugin.getTeamManager().colorizedPrefix(ourTeam) + String.valueOf(ChatColor.AQUA) + " and " + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + String.valueOf(ChatColor.AQUA) + " are now allied - no points for kills between them."));
                break;
            }
            case REQUEST_SENT: {
                player.sendMessage(String.valueOf(ChatColor.GREEN) + "Alliance request sent to " + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + String.valueOf(ChatColor.GREEN) + ". They need to run " + String.valueOf(ChatColor.YELLOW) + "/team ally " + ourTeam.getDisplayName() + String.valueOf(ChatColor.GREEN) + " to accept.");
                Player targetOwner = Bukkit.getPlayer((UUID)targetTeam.getOwner());
                if (targetOwner == null) break;
                targetOwner.sendMessage(String.valueOf(ChatColor.GREEN) + this.plugin.getTeamManager().colorizedPrefix(ourTeam) + String.valueOf(ChatColor.GREEN) + " has proposed an alliance! Run " + String.valueOf(ChatColor.YELLOW) + "/team ally " + ourTeam.getDisplayName() + String.valueOf(ChatColor.GREEN) + " to accept.");
            }
        }
    }

    private void handleUnally(Player player, String[] args) {
        TeamData ourTeam = this.requireOwnedTeamAndTarget(player, args, "Usage: /team unally <team>");
        if (ourTeam == null) {
            return;
        }
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[1]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "No team named '" + args[1] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        boolean wasAllied = this.plugin.getTeamManager().areAllied(ourTeam.getKey(), targetTeam.getKey());
        this.plugin.getTeamManager().removeAlly(ourTeam, targetTeam);
        if (wasAllied) {
            Bukkit.broadcastMessage((String)(String.valueOf(ChatColor.RED) + this.plugin.getTeamManager().colorizedPrefix(ourTeam) + String.valueOf(ChatColor.RED) + " has broken their alliance with " + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + String.valueOf(ChatColor.RED) + "."));
        } else {
            player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Cancelled any pending alliance with " + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + String.valueOf(ChatColor.YELLOW) + ".");
        }
    }

    private void handleAllies(Player player) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            return;
        }
        TeamData team = teamOpt.get();
        Set<String> allyKeys = this.plugin.getTeamManager().getAllies(team.getKey());
        if (allyKeys.isEmpty()) {
            player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Your team has no allies yet.");
            return;
        }
        player.sendMessage(String.valueOf(ChatColor.GOLD) + "--- Allies of " + this.plugin.getTeamManager().colorizedPrefix(team) + String.valueOf(ChatColor.GOLD) + " ---");
        for (String allyKey : allyKeys) {
            this.plugin.getTeamManager().getTeam(allyKey).ifPresent(ally -> player.sendMessage(this.plugin.getTeamManager().colorizedPrefix((TeamData)ally)));
        }
    }
}

