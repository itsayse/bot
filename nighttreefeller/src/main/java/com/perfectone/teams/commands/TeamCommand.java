package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.manager.TeamManager;
import com.perfectone.teams.util.ColorUtil;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class TeamCommand implements CommandExecutor {

    private final TeamsPlugin plugin;

    public TeamCommand(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "color", "colour" -> handleColor(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player);
            case "invite" -> handleInvite(player, args);
            case "uninvite" -> handleUninvite(player, args);
            case "setmode", "mode" -> handleSetMode(player, args);
            case "reloadbanlist" -> handleReloadBanList(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleReloadBanList(Player player) {
        if (!player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        plugin.getBannedNameManager().reload();
        player.sendMessage(ChatColor.GREEN + "Reloaded bad.txt - " + plugin.getBannedNameManager().size() + " banned words loaded.");
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Team Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/team create <name>");
        player.sendMessage(ChatColor.YELLOW + "/team join <name>");
        player.sendMessage(ChatColor.YELLOW + "/team leave");
        player.sendMessage(ChatColor.YELLOW + "/team disband");
        player.sendMessage(ChatColor.YELLOW + "/team color <color>");
        player.sendMessage(ChatColor.YELLOW + "/team info [name]");
        player.sendMessage(ChatColor.YELLOW + "/team list");
        player.sendMessage(ChatColor.YELLOW + "/team setmode <open|invite>" + ChatColor.GRAY + " - owner only");
        player.sendMessage(ChatColor.YELLOW + "/team invite <player>" + ChatColor.GRAY + " - owner only, invite-only teams");
        player.sendMessage(ChatColor.YELLOW + "/team uninvite <player>" + ChatColor.GRAY + " - owner only");
        if (player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/team reloadbanlist" + ChatColor.GRAY + " - reload bad.txt");
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team create <name>");
            return;
        }
        String name = args[1];

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (!data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're already in a team. Leave it first with /team leave.");
            return;
        }

        int min = plugin.getConfig().getInt("team.min-name-length", 3);
        int max = plugin.getConfig().getInt("team.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            player.sendMessage(ChatColor.RED + "Team name must be between " + min + " and " + max + " characters.");
            return;
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            player.sendMessage(ChatColor.RED + "Team names can only contain letters, numbers, and underscores.");
            return;
        }

        if (plugin.getBannedNameManager().isBanned(name)) {
            player.sendMessage(ChatColor.RED + "That team name isn't allowed. Pick a different name.");
            return;
        }

        if (plugin.getTeamManager().exists(name)) {
            player.sendMessage(ChatColor.RED + "A team named '" + name + "' already exists. Pick a different name.");
            return;
        }

        TeamData team = plugin.getTeamManager().createTeam(name, player.getUniqueId());
        data.setTeamKey(team.getKey());
        plugin.getPlayerDataManager().save(data);
        plugin.getTeamManager().addPlayerToScoreboardTeam(team, player.getName());

        player.sendMessage(ChatColor.GREEN + "Team '" + team.getDisplayName() + "' created! You are the owner.");
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team join <name>");
            return;
        }
        String name = args[1];

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (!data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're already in a team. Leave it first with /team leave.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(name);
        if (teamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + name + "' exists.");
            return;
        }

        TeamData team = teamOpt.get();

        if (team.isInviteOnly() && !player.hasPermission("teams.admin")) {
            if (!plugin.getTeamManager().isInvited(team, player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + plugin.getTeamManager().colorizedPrefix(team)
                        + ChatColor.RED + " is invite-only. Ask the owner for an invite.");
                return;
            }
            plugin.getTeamManager().clearInvite(team, player.getUniqueId());
        }

        data.setTeamKey(team.getKey());
        plugin.getPlayerDataManager().save(data);
        plugin.getTeamManager().addPlayerToScoreboardTeam(team, player.getName());

        player.sendMessage(ChatColor.GREEN + "You joined " + plugin.getTeamManager().colorizedPrefix(team));
    }

    private void handleLeave(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(data.getTeamKey());
        String teamKey = data.getTeamKey();
        data.setTeamKey(null);
        plugin.getPlayerDataManager().save(data);
        plugin.getTeamManager().removePlayerFromAllScoreboardTeams(player.getName());

        // If the owner leaves, transfer ownership to another member, or disband if now empty.
        teamOpt.ifPresent(team -> {
            if (team.getOwner().equals(player.getUniqueId())) {
                Optional<PlayerData> nextMember = plugin.getPlayerDataManager().all().values().stream()
                        .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                        .findFirst();
                if (nextMember.isPresent()) {
                    team.setOwner(nextMember.get().getUuid());
                    plugin.getTeamManager().saveTeam(team);
                    player.sendMessage(ChatColor.YELLOW + "Ownership transferred to " + nextMember.get().getUsername());
                } else {
                    plugin.getTeamManager().disbandTeam(team);
                    player.sendMessage(ChatColor.YELLOW + "You were the last member - the team was disbanded.");
                }
            }
        });

        player.sendMessage(ChatColor.GREEN + "You left your team.");
    }

    private void handleDisband(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Team not found.");
            return;
        }
        TeamData team = teamOpt.get();

        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can disband the team.");
            return;
        }

        String teamKey = team.getKey();
        plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                .forEach(p -> {
                    p.setTeamKey(null);
                    plugin.getPlayerDataManager().save(p);
                    plugin.getTeamManager().removePlayerFromAllScoreboardTeams(p.getUsername());
                });

        plugin.getTeamManager().disbandTeam(team);
        player.sendMessage(ChatColor.GREEN + "Team '" + team.getDisplayName() + "' has been disbanded.");
    }

    private void handleColor(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team color <color>");
            player.sendMessage(ChatColor.GRAY + "Available: " + ColorUtil.namesList());
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();

        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can change the team color.");
            return;
        }

        ChatColor color = ColorUtil.parse(args[1]);
        if (color == null) {
            player.sendMessage(ChatColor.RED + "Invalid color. Available: " + ColorUtil.namesList());
            return;
        }

        team.setPrefixColor(color.name());
        plugin.getTeamManager().saveTeam(team);
        player.sendMessage(ChatColor.GREEN + "Team color updated: " + plugin.getTeamManager().colorizedPrefix(team));
    }

    private void handleInfo(Player player, String[] args) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        String targetName = args.length >= 2 ? args[1] : data.getTeamKey();

        if (targetName == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team. Use /team info <name> to check another team.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(targetName);
        if (teamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No such team.");
            return;
        }
        TeamData team = teamOpt.get();

        long memberCount = plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> team.getKey().equalsIgnoreCase(p.getTeamKey()))
                .count();

        player.sendMessage(ChatColor.GOLD + "--- " + plugin.getTeamManager().colorizedPrefix(team) + ChatColor.GOLD + " ---");
        player.sendMessage(ChatColor.YELLOW + "Score: " + ChatColor.WHITE + team.getScore());
        player.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.WHITE + memberCount);
        player.sendMessage(ChatColor.YELLOW + "Join mode: " + ChatColor.WHITE + team.getJoinMode().name().toLowerCase());
    }

    private void handleList(Player player) {
        List<TeamData> teams = plugin.getTeamManager().all().values().stream().toList();
        if (teams.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No teams exist yet. Create one with /team create <name>!");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Teams (" + teams.size() + ") ---");
        for (TeamData t : teams) {
            String modeTag = t.isInviteOnly() ? ChatColor.GRAY + " [invite-only]" : "";
            player.sendMessage(plugin.getTeamManager().colorizedPrefix(t) + ChatColor.GRAY + " - score: " + t.getScore() + modeTag);
        }
    }

    private void handleSetMode(Player player, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("open") && !args[1].equalsIgnoreCase("invite"))) {
            player.sendMessage(ChatColor.RED + "Usage: /team setmode <open|invite>");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();

        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can change the join mode.");
            return;
        }

        TeamData.JoinMode mode = args[1].equalsIgnoreCase("invite") ? TeamData.JoinMode.INVITE : TeamData.JoinMode.OPEN;
        team.setJoinMode(mode);
        plugin.getTeamManager().saveTeam(team);

        player.sendMessage(ChatColor.GREEN + plugin.getTeamManager().colorizedPrefix(team) + ChatColor.GREEN
                + " is now " + (mode == TeamData.JoinMode.OPEN
                        ? "open - anyone can /team join it."
                        : "invite-only - players need /team invite from you first."));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team invite <player>");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();

        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can send invites.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player '" + args[1] + "' isn't online.");
            return;
        }

        PlayerData targetData = plugin.getPlayerDataManager().getOrCreate(target);
        if (!targetData.isCivilian()) {
            player.sendMessage(ChatColor.RED + target.getName() + " is already in a team.");
            return;
        }

        plugin.getTeamManager().invite(team, target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to "
                + plugin.getTeamManager().colorizedPrefix(team));
        target.sendMessage(ChatColor.GREEN + "You've been invited to join " + plugin.getTeamManager().colorizedPrefix(team)
                + ChatColor.GREEN + "! Use " + ChatColor.YELLOW + "/team join " + team.getDisplayName()
                + ChatColor.GREEN + " to accept.");
    }

    private void handleUninvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team uninvite <player>");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }

        Optional<TeamData> teamOpt = plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();

        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can revoke invites.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        java.util.UUID targetId = target != null ? target.getUniqueId() : null;

        if (targetId == null) {
            player.sendMessage(ChatColor.RED + "Player '" + args[1] + "' isn't online, so their invite can't be looked up right now.");
            return;
        }

        plugin.getTeamManager().uninvite(team, targetId);
        player.sendMessage(ChatColor.GREEN + "Revoked " + args[1] + "'s invite.");
    }
}
