package com.perfectone.teams.commands;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.manager.TeamManager;
import com.perfectone.teams.util.ColorUtil;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
            this.sendUsage(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create"        -> this.handleCreate(player, args);
            case "join"          -> this.handleJoin(player, args);
            case "leave"         -> this.handleLeave(player);
            case "disband"       -> this.handleDisband(player);
            case "color", "colour" -> this.handleColor(player, args);
            case "info"          -> this.handleInfo(player, args);
            case "stats"         -> this.handleStats(player, args);
            case "list"          -> this.handleList(player);
            case "invite"        -> this.handleInvite(player, args);
            case "uninvite"      -> this.handleUninvite(player, args);
            case "kick"          -> this.handleKick(player, args);
            case "setmode", "mode" -> this.handleSetMode(player, args);
            case "reloadbanlist" -> this.handleReloadBanList(player);
            case "ally"          -> this.handleAlly(player, args);
            case "unally"        -> this.handleUnally(player, args);
            case "allies"        -> this.handleAllies(player);
            case "war"           -> this.handleWar(player, args);
            case "enemy"         -> this.handleEnemy(player, args);
            case "enemies"       -> this.handleEnemies(player);
            case "transfer"      -> this.handleTransfer(player, args);
            default              -> this.sendUsage(player);
        }
        return true;
    }

    // ---- Usage ----------------------------------------------------------------

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Team Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/team create <name>");
        player.sendMessage(ChatColor.YELLOW + "/team join <name>");
        player.sendMessage(ChatColor.YELLOW + "/team leave");
        player.sendMessage(ChatColor.YELLOW + "/team disband");
        player.sendMessage(ChatColor.YELLOW + "/team color <color>");
        player.sendMessage(ChatColor.YELLOW + "/team info [name]");
        player.sendMessage(ChatColor.YELLOW + "/team stats [name]" + ChatColor.GRAY + " - full stats with member list");
        player.sendMessage(ChatColor.YELLOW + "/team list");
        player.sendMessage(ChatColor.YELLOW + "/team setmode <open|invite>" + ChatColor.GRAY + " - owner only");
        player.sendMessage(ChatColor.YELLOW + "/team invite <player>" + ChatColor.GRAY + " - owner only, invite-only teams");
        player.sendMessage(ChatColor.YELLOW + "/team uninvite <player>" + ChatColor.GRAY + " - owner only");
        player.sendMessage(ChatColor.YELLOW + "/team kick <player>" + ChatColor.GRAY + " - owner only");
        player.sendMessage(ChatColor.YELLOW + "/team ally <team>" + ChatColor.GRAY + " - owner only, propose/accept an alliance");
        player.sendMessage(ChatColor.YELLOW + "/team unally <team>" + ChatColor.GRAY + " - owner only, breaks an alliance");
        player.sendMessage(ChatColor.YELLOW + "/team allies" + ChatColor.GRAY + " - list your team's allies");
        player.sendMessage(ChatColor.YELLOW + "/team war <team>" + ChatColor.GRAY + " - owner only, declare a war");
        player.sendMessage(ChatColor.YELLOW + "/team war accept <team>" + ChatColor.GRAY + " - owner only, accept a war request");
        player.sendMessage(ChatColor.YELLOW + "/team war deny <team>" + ChatColor.GRAY + " - owner only, deny a war request");
        player.sendMessage(ChatColor.YELLOW + "/team enemy <team>" + ChatColor.GRAY + " - mark a team as enemy (no war needed)");
        player.sendMessage(ChatColor.YELLOW + "/team enemies" + ChatColor.GRAY + " - list your marked enemies");
        player.sendMessage(ChatColor.YELLOW + "/team transfer <player>" + ChatColor.GRAY + " - owner only, transfer ownership");
        if (player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/team reloadbanlist" + ChatColor.GRAY + " - reload bad.txt");
        }
    }

    // ---- Create ---------------------------------------------------------------

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team create <name>");
            return;
        }
        String name = args[1];
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (!data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're already in a team. Leave it first with /team leave.");
            return;
        }
        int min = this.plugin.getConfig().getInt("team.min-name-length", 3);
        int max = this.plugin.getConfig().getInt("team.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            player.sendMessage(ChatColor.RED + "Team name must be between " + min + " and " + max + " characters.");
            return;
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            player.sendMessage(ChatColor.RED + "Team names can only contain letters, numbers, and underscores.");
            return;
        }
        if (this.plugin.getBannedNameManager().isBanned(name)) {
            player.sendMessage(ChatColor.RED + "That team name isn't allowed. Pick a different name.");
            return;
        }
        if (this.plugin.getTeamManager().exists(name)) {
            player.sendMessage(ChatColor.RED + "A team named '" + name + "' already exists.");
            return;
        }
        TeamData team = this.plugin.getTeamManager().createTeam(name, player.getUniqueId());
        data.setTeamKey(team.getKey());
        this.plugin.getPlayerDataManager().save(data);
        this.plugin.getTeamManager().addPlayerToScoreboardTeam(team, player.getName());
        player.sendMessage(ChatColor.GREEN + "Team '" + team.getDisplayName() + "' created! You are the owner.");
    }

    // ---- Join -----------------------------------------------------------------

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team join <name>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (!data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're already in a team. Leave it first with /team leave.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(args[1]);
        if (teamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + args[1] + "' exists.");
            return;
        }
        TeamData team = teamOpt.get();
        if (team.isInviteOnly() && !player.hasPermission("teams.admin")) {
            if (!this.plugin.getTeamManager().isInvited(team, player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + this.plugin.getTeamManager().colorizedPrefix(team)
                        + ChatColor.RED + " is invite-only. Ask the owner for an invite.");
                return;
            }
            this.plugin.getTeamManager().clearInvite(team, player.getUniqueId());
        }
        data.setTeamKey(team.getKey());
        this.plugin.getPlayerDataManager().save(data);
        this.plugin.getTeamManager().addPlayerToScoreboardTeam(team, player.getName());
        player.sendMessage(ChatColor.GREEN + "You joined " + this.plugin.getTeamManager().colorizedPrefix(team));
    }

    // ---- Leave ----------------------------------------------------------------

    private void handleLeave(Player player) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        String teamKey = data.getTeamKey();
        data.setTeamKey(null);
        this.plugin.getPlayerDataManager().save(data);
        this.plugin.getTeamManager().removePlayerFromAllScoreboardTeams(player.getName());
        teamOpt.ifPresent(team -> {
            if (team.getOwner().equals(player.getUniqueId())) {
                Optional<PlayerData> nextMember = this.plugin.getPlayerDataManager().all().values().stream()
                        .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                        .findFirst();
                if (nextMember.isPresent()) {
                    team.setOwner(nextMember.get().getUuid());
                    this.plugin.getTeamManager().saveTeam(team);
                    player.sendMessage(ChatColor.YELLOW + "Ownership transferred to "
                            + nextMember.get().getUsername());
                } else {
                    this.plugin.getTeamManager().disbandTeam(team);
                    player.sendMessage(ChatColor.YELLOW + "You were the last member - the team was disbanded.");
                }
            }
        });
        player.sendMessage(ChatColor.GREEN + "You left your team.");
    }

    // ---- Kick -----------------------------------------------------------------

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team kick <player>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Team not found.");
            return;
        }
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can kick members.");
            return;
        }
        String targetName = args[1];
        String teamKey = team.getKey();
        Optional<PlayerData> targetOpt = this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey())
                          && targetName.equalsIgnoreCase(p.getUsername()))
                .findFirst();
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + targetName + " isn't on your team.");
            return;
        }
        PlayerData target = targetOpt.get();
        if (target.getUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You can't kick yourself - use /team leave or /team disband.");
            return;
        }
        target.setTeamKey(null);
        this.plugin.getPlayerDataManager().save(target);
        this.plugin.getTeamManager().removePlayerFromAllScoreboardTeams(target.getUsername());
        player.sendMessage(ChatColor.GREEN + "Kicked " + target.getUsername() + " from the team.");
        Player targetPlayer = Bukkit.getPlayer(target.getUuid());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(ChatColor.RED + "You were kicked from " + team.getDisplayName() + ".");
        }
    }

    // ---- Disband --------------------------------------------------------------

    private void handleDisband(Player player) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
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
        this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                .forEach(p -> {
                    p.setTeamKey(null);
                    this.plugin.getPlayerDataManager().save(p);
                    this.plugin.getTeamManager().removePlayerFromAllScoreboardTeams(p.getUsername());
                });
        this.plugin.getTeamManager().disbandTeam(team);
        player.sendMessage(ChatColor.GREEN + "Team '" + team.getDisplayName() + "' has been disbanded.");
    }

    // ---- Color ----------------------------------------------------------------

    private void handleColor(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team color <color>");
            player.sendMessage(ChatColor.GRAY + "Available: " + ColorUtil.namesList());
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
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
        this.plugin.getTeamManager().saveTeam(team);
        player.sendMessage(ChatColor.GREEN + "Team color updated: "
                + this.plugin.getTeamManager().colorizedPrefix(team));
    }

    // ---- Info -----------------------------------------------------------------

    private void handleInfo(Player player, String[] args) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        String targetName = args.length >= 2 ? args[1] : data.getTeamKey();
        if (targetName == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team. Use /team info <name> to check another team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(targetName);
        if (teamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No such team.");
            return;
        }
        TeamData team = teamOpt.get();
        String teamKey = team.getKey();

        long memberCount = this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                .count();

        double teamBounty = this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                .mapToDouble(PlayerData::getBounty)
                .sum();

        player.sendMessage(ChatColor.GOLD + "--- " + this.plugin.getTeamManager().colorizedPrefix(team)
                + ChatColor.GOLD + " ---");
        player.sendMessage(ChatColor.YELLOW + "Score: " + ChatColor.WHITE + (int) team.getScore() + " pts");
        player.sendMessage(ChatColor.YELLOW + "Team Bounty: " + ChatColor.GOLD + MoneyUtil.format(teamBounty));
        player.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.WHITE + memberCount);
        player.sendMessage(ChatColor.YELLOW + "Join mode: " + ChatColor.WHITE
                + team.getJoinMode().name().toLowerCase());
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/team stats "
                + team.getDisplayName() + ChatColor.GRAY + " for full member stats.");
    }

    // ---- Stats ----------------------------------------------------------------

    private void handleStats(Player player, String[] args) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        String targetName = args.length >= 2 ? args[1] : data.getTeamKey();
        if (targetName == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team. Use /team stats <name> to check another team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(targetName);
        if (teamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No such team.");
            return;
        }
        TeamData team = teamOpt.get();
        String teamKey = team.getKey();

        List<PlayerData> members = this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey()))
                .sorted(Comparator.comparingInt(PlayerData::getKills).reversed())
                .collect(Collectors.toList());

        double teamBounty  = members.stream().mapToDouble(PlayerData::getBounty).sum();
        int    totalKills  = members.stream().mapToInt(PlayerData::getKills).sum();
        int    totalDeaths = members.stream().mapToInt(PlayerData::getDeaths).sum();

        Set<String> allies  = this.plugin.getTeamManager().getAllies(teamKey);
        Set<String> enemies = this.plugin.getTeamManager().getEnemies(teamKey);
        Set<String> wars    = this.plugin.getTeamManager().getWars(teamKey);

        player.sendMessage(ChatColor.GOLD + "━━━ " + this.plugin.getTeamManager().colorizedPrefix(team)
                + ChatColor.GOLD + " ━━━");
        player.sendMessage(ChatColor.YELLOW + "Score: "       + ChatColor.WHITE + (int) team.getScore() + " pts");
        player.sendMessage(ChatColor.YELLOW + "Team Bounty: " + ChatColor.GOLD + MoneyUtil.format(teamBounty));
        player.sendMessage(ChatColor.YELLOW + "Members: "     + ChatColor.WHITE + members.size());
        player.sendMessage(ChatColor.YELLOW + "K/D: "         + ChatColor.WHITE + totalKills
                + ChatColor.GRAY + "/" + ChatColor.WHITE + totalDeaths);
        player.sendMessage(ChatColor.YELLOW + "Join Mode: "   + ChatColor.WHITE
                + team.getJoinMode().name().toLowerCase());

        if (!allies.isEmpty()) {
            String allyList = allies.stream()
                    .map(k -> this.plugin.getTeamManager().getTeam(k)
                            .map(t -> this.plugin.getTeamManager().colorizedPrefix(t))
                            .orElse(ChatColor.GRAY + k))
                    .collect(Collectors.joining(ChatColor.GRAY + ", "));
            player.sendMessage(ChatColor.GREEN + "Allies: " + allyList);
        }
        if (!wars.isEmpty()) {
            String warList = wars.stream()
                    .map(k -> this.plugin.getTeamManager().getTeam(k)
                            .map(t -> this.plugin.getTeamManager().colorizedPrefix(t))
                            .orElse(ChatColor.GRAY + k))
                    .collect(Collectors.joining(ChatColor.GRAY + ", "));
            player.sendMessage(ChatColor.DARK_RED + "⚔ At War: " + warList);
        }
        if (!enemies.isEmpty()) {
            String enemyList = enemies.stream()
                    .map(k -> this.plugin.getTeamManager().getTeam(k)
                            .map(t -> this.plugin.getTeamManager().colorizedPrefix(t))
                            .orElse(ChatColor.GRAY + k))
                    .collect(Collectors.joining(ChatColor.GRAY + ", "));
            player.sendMessage(ChatColor.RED + "Enemies: " + enemyList);
        }

        if (members.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "(No members found)");
        } else {
            player.sendMessage(ChatColor.GOLD + "--- Members (" + members.size() + ") ---");
            for (PlayerData m : members) {
                boolean isOwner = m.getUuid().equals(team.getOwner());
                String ownerTag = isOwner ? ChatColor.GOLD + "★ " : "";
                player.sendMessage(
                        ownerTag + ChatColor.WHITE + m.getUsername()
                        + ChatColor.GRAY + " | "
                        + ChatColor.GREEN + "⚔ " + m.getKills()
                        + ChatColor.GRAY + " | "
                        + ChatColor.RED + "✞ " + m.getDeaths()
                        + ChatColor.GRAY + " | "
                        + ChatColor.GOLD + MoneyUtil.format(m.getBounty()));
            }
        }
    }

    // ---- List -----------------------------------------------------------------

    private void handleList(Player player) {
        List<TeamData> teams = this.plugin.getTeamManager().all().values().stream()
                .sorted(Comparator.comparingDouble(TeamData::getScore).reversed())
                .collect(Collectors.toList());
        if (teams.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No teams exist yet. Create one with /team create <name>!");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Teams (" + teams.size() + ") ---");
        for (TeamData t : teams) {
            String modeTag = t.isInviteOnly() ? ChatColor.GRAY + " [invite-only]" : "";
            player.sendMessage(this.plugin.getTeamManager().colorizedPrefix(t)
                    + ChatColor.GRAY + " - score: " + (int) t.getScore() + modeTag);
        }
    }

    // ---- SetMode --------------------------------------------------------------

    private void handleSetMode(Player player, String[] args) {
        if (args.length < 2
                || (!args[1].equalsIgnoreCase("open") && !args[1].equalsIgnoreCase("invite"))) {
            player.sendMessage(ChatColor.RED + "Usage: /team setmode <open|invite>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can change the join mode.");
            return;
        }
        TeamData.JoinMode mode = args[1].equalsIgnoreCase("invite")
                ? TeamData.JoinMode.INVITE
                : TeamData.JoinMode.OPEN;
        team.setJoinMode(mode);
        this.plugin.getTeamManager().saveTeam(team);
        player.sendMessage(ChatColor.GREEN + this.plugin.getTeamManager().colorizedPrefix(team)
                + ChatColor.GREEN + " is now "
                + (mode == TeamData.JoinMode.OPEN
                        ? "open - anyone can /team join it."
                        : "invite-only - players need /team invite from you first."));
    }

    // ---- Invite ---------------------------------------------------------------

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team invite <player>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
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
        PlayerData targetData = this.plugin.getPlayerDataManager().getOrCreate(target);
        if (!targetData.isCivilian()) {
            player.sendMessage(ChatColor.RED + target.getName() + " is already in a team.");
            return;
        }
        this.plugin.getTeamManager().invite(team, target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to "
                + this.plugin.getTeamManager().colorizedPrefix(team));
        target.sendMessage(ChatColor.GREEN + "You've been invited to join "
                + this.plugin.getTeamManager().colorizedPrefix(team)
                + ChatColor.GREEN + "! Use " + ChatColor.YELLOW + "/team join "
                + team.getDisplayName() + ChatColor.GREEN + " to accept.");
    }

    // ---- Uninvite -------------------------------------------------------------

    private void handleUninvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team uninvite <player>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can revoke invites.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player '" + args[1] + "' isn't online.");
            return;
        }
        this.plugin.getTeamManager().uninvite(team, target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Revoked " + args[1] + "'s invite.");
    }

    // ---- Reload ban list ------------------------------------------------------

    private void handleReloadBanList(Player player) {
        if (!player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        this.plugin.getBannedNameManager().reload();
        player.sendMessage(ChatColor.GREEN + "Reloaded bad.txt - "
                + this.plugin.getBannedNameManager().size() + " banned words loaded.");
    }

    // ---- Ally -----------------------------------------------------------------

    private TeamData requireOwnedTeam(Player player, String[] args, String usage) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + usage);
            return null;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return null;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return null;
        TeamData ourTeam = teamOpt.get();
        if (!ourTeam.getOwner().equals(player.getUniqueId())
                && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can do this.");
            return null;
        }
        return ourTeam;
    }

    private void handleAlly(Player player, String[] args) {
        TeamData ourTeam = this.requireOwnedTeam(player, args, "Usage: /team ally <team>");
        if (ourTeam == null) return;
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[1]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + args[1] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        switch (this.plugin.getTeamManager().proposeAlly(ourTeam, targetTeam)) {
            case CANNOT_ALLY_SELF ->
                player.sendMessage(ChatColor.RED + "You can't ally your own team.");
            case ALREADY_ALLIED ->
                player.sendMessage(ChatColor.YELLOW + "You're already allied with "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + ".");
            case NOW_ALLIED ->
                Bukkit.broadcastMessage(this.plugin.getTeamManager().colorizedPrefix(ourTeam)
                        + ChatColor.AQUA + " and "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam)
                        + ChatColor.AQUA + " are now allied!");
            case REQUEST_SENT -> {
                player.sendMessage(ChatColor.GREEN + "Alliance request sent to "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam)
                        + ChatColor.GREEN + ". They need to run "
                        + ChatColor.YELLOW + "/team ally " + ourTeam.getDisplayName()
                        + ChatColor.GREEN + " to accept.");
                Player targetOwner = Bukkit.getPlayer(targetTeam.getOwner());
                if (targetOwner != null) {
                    targetOwner.sendMessage(this.plugin.getTeamManager().colorizedPrefix(ourTeam)
                            + ChatColor.GREEN + " has proposed an alliance! Run "
                            + ChatColor.YELLOW + "/team ally " + ourTeam.getDisplayName()
                            + ChatColor.GREEN + " to accept.");
                }
            }
        }
    }

    private void handleUnally(Player player, String[] args) {
        TeamData ourTeam = this.requireOwnedTeam(player, args, "Usage: /team unally <team>");
        if (ourTeam == null) return;
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[1]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + args[1] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        boolean wasAllied = this.plugin.getTeamManager().areAllied(ourTeam.getKey(), targetTeam.getKey());
        this.plugin.getTeamManager().removeAlly(ourTeam, targetTeam);
        if (wasAllied) {
            Bukkit.broadcastMessage(this.plugin.getTeamManager().colorizedPrefix(ourTeam)
                    + ChatColor.RED + " has broken their alliance with "
                    + this.plugin.getTeamManager().colorizedPrefix(targetTeam)
                    + ChatColor.RED + ".");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Cancelled any pending alliance with "
                    + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + ".");
        }
    }

    private void handleAllies(Player player) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();
        Set<String> allyKeys = this.plugin.getTeamManager().getAllies(team.getKey());
        if (allyKeys.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Your team has no allies yet.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Allies of "
                + this.plugin.getTeamManager().colorizedPrefix(team) + ChatColor.GOLD + " ---");
        for (String allyKey : allyKeys) {
            this.plugin.getTeamManager().getTeam(allyKey)
                    .ifPresent(ally -> player.sendMessage(
                            this.plugin.getTeamManager().colorizedPrefix(ally)));
        }
    }

    // ---- War ------------------------------------------------------------------

    private void handleWar(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("accept")) {
            this.handleWarAccept(player, args);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
            this.handleWarDeny(player, args);
            return;
        }
        TeamData ourTeam = this.requireOwnedTeam(player, args, "Usage: /team war <team>");
        if (ourTeam == null) return;
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[1]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + args[1] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        switch (this.plugin.getTeamManager().proposeWar(ourTeam, targetTeam)) {
            case CANNOT_WAR_SELF ->
                player.sendMessage(ChatColor.RED + "You can't declare war on your own team.");
            case ALREADY_AT_WAR ->
                player.sendMessage(ChatColor.YELLOW + "You're already at war with "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + ".");
            case REQUEST_SENT -> {
                player.sendMessage(ChatColor.GREEN + "War request sent to "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam)
                        + ChatColor.GREEN + ". They need to run "
                        + ChatColor.YELLOW + "/team war accept " + ourTeam.getDisplayName()
                        + ChatColor.GREEN + " to accept, or "
                        + ChatColor.YELLOW + "/team war deny " + ourTeam.getDisplayName()
                        + ChatColor.GREEN + " to decline.");
                Player targetOwner = Bukkit.getPlayer(targetTeam.getOwner());
                if (targetOwner != null) {
                    targetOwner.sendMessage(ChatColor.RED
                            + this.plugin.getTeamManager().colorizedPrefix(ourTeam)
                            + ChatColor.RED + " has declared war on your team! Run "
                            + ChatColor.YELLOW + "/team war accept " + ourTeam.getDisplayName()
                            + ChatColor.RED + " to accept, or "
                            + ChatColor.YELLOW + "/team war deny " + ourTeam.getDisplayName()
                            + ChatColor.RED + " to decline.");
                }
            }
            default -> { /* no-op */ }
        }
    }

    private void handleWarAccept(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /team war accept <team>");
            return;
        }
        TeamData ourTeam = this.requireOwnedTeam(player, new String[]{args[0], args[2]},
                "Usage: /team war accept <team>");
        if (ourTeam == null) return;
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[2]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + args[2] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        switch (this.plugin.getTeamManager().acceptWar(ourTeam, targetTeam)) {
            case NO_PENDING_REQUEST ->
                player.sendMessage(ChatColor.RED + "No pending war request from "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + ".");
            case ALREADY_AT_WAR ->
                player.sendMessage(ChatColor.YELLOW + "You're already at war with "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + ".");
            case NOW_AT_WAR ->
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚔ WAR DECLARED ⚔ "
                        + ChatColor.RESET
                        + this.plugin.getTeamManager().colorizedPrefix(ourTeam)
                        + ChatColor.DARK_RED + " and "
                        + this.plugin.getTeamManager().colorizedPrefix(targetTeam)
                        + ChatColor.DARK_RED + " are now at war!");
            default -> { /* no-op */ }
        }
    }

    private void handleWarDeny(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /team war deny <team>");
            return;
        }
        TeamData ourTeam = this.requireOwnedTeam(player, new String[]{args[0], args[2]},
                "Usage: /team war deny <team>");
        if (ourTeam == null) return;
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[2]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + args[2] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        TeamManager.WarResult result = this.plugin.getTeamManager().denyWar(ourTeam, targetTeam);
        if (result == TeamManager.WarResult.REQUEST_DENIED) {
            player.sendMessage(ChatColor.YELLOW + "Denied the war request from "
                    + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + ".");
            Player targetOwner = Bukkit.getPlayer(targetTeam.getOwner());
            if (targetOwner != null) {
                targetOwner.sendMessage(this.plugin.getTeamManager().colorizedPrefix(ourTeam)
                        + ChatColor.YELLOW + " denied your war request.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "No pending war request from "
                    + this.plugin.getTeamManager().colorizedPrefix(targetTeam) + ".");
        }
    }

    // ---- Enemy ----------------------------------------------------------------

    private void handleEnemy(Player player, String[] args) {
        TeamData ourTeam = this.requireOwnedTeam(player, args, "Usage: /team enemy <team>");
        if (ourTeam == null) return;
        Optional<TeamData> targetOpt = this.plugin.getTeamManager().getTeam(args[1]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No team named '" + args[1] + "' exists.");
            return;
        }
        TeamData targetTeam = targetOpt.get();
        if (ourTeam.getKey().equals(targetTeam.getKey())) {
            player.sendMessage(ChatColor.RED + "You can't mark your own team as an enemy.");
            return;
        }
        if (this.plugin.getTeamManager().hasMarkedEnemy(ourTeam.getKey(), targetTeam.getKey())) {
            this.plugin.getTeamManager().unmarkEnemy(ourTeam, targetTeam);
            player.sendMessage(this.plugin.getTeamManager().colorizedPrefix(targetTeam)
                    + ChatColor.YELLOW + " is no longer marked as an enemy.");
        } else {
            this.plugin.getTeamManager().markEnemy(ourTeam, targetTeam);
            player.sendMessage(this.plugin.getTeamManager().colorizedPrefix(targetTeam)
                    + ChatColor.RED + " has been marked as an enemy.");
        }
    }

    private void handleEnemies(Player player) {
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();
        Set<String> enemyKeys = this.plugin.getTeamManager().getEnemies(team.getKey());
        if (enemyKeys.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Your team hasn't marked anyone as an enemy.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Enemies of "
                + this.plugin.getTeamManager().colorizedPrefix(team) + ChatColor.GOLD + " ---");
        for (String enemyKey : enemyKeys) {
            this.plugin.getTeamManager().getTeam(enemyKey)
                    .ifPresent(e -> player.sendMessage(
                            this.plugin.getTeamManager().colorizedPrefix(e)));
        }
    }

    // ---- Transfer -------------------------------------------------------------

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team transfer <player>");
            return;
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        if (data.isCivilian()) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        Optional<TeamData> teamOpt = this.plugin.getTeamManager().getTeam(data.getTeamKey());
        if (teamOpt.isEmpty()) return;
        TeamData team = teamOpt.get();
        if (!team.getOwner().equals(player.getUniqueId())
                && !player.hasPermission("teams.admin")) {
            player.sendMessage(ChatColor.RED + "Only the team owner can transfer ownership.");
            return;
        }
        String targetName = args[1];
        String teamKey = team.getKey();
        Optional<PlayerData> targetOpt = this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> teamKey.equalsIgnoreCase(p.getTeamKey())
                          && targetName.equalsIgnoreCase(p.getUsername()))
                .findFirst();
        if (targetOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + targetName + " isn't on your team.");
            return;
        }
        PlayerData target = targetOpt.get();
        if (target.getUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You already own this team.");
            return;
        }
        this.plugin.getTeamManager().transferOwnership(team, target.getUuid());
        player.sendMessage(ChatColor.GREEN + "Ownership of "
                + this.plugin.getTeamManager().colorizedPrefix(team)
                + ChatColor.GREEN + " transferred to " + target.getUsername() + ".");
        Player targetPlayer = Bukkit.getPlayer(target.getUuid());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(ChatColor.GREEN + "You are now the owner of "
                    + this.plugin.getTeamManager().colorizedPrefix(team)
                    + ChatColor.GREEN + "!");
        }
    }
}
