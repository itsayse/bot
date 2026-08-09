package com.perfectone.teams.manager;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.Database;
import com.perfectone.teams.data.TeamData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all TeamData in memory, persists changes to SQLite, and mirrors
 * each team onto the main server Scoreboard so prefixes show in tab + chat.
 */
public class TeamManager {

    private final TeamsPlugin plugin;
    private final Database database;
    private final Map<String, TeamData> teamsByKey = new ConcurrentHashMap<>();
    // team key -> set of invited player UUIDs. Intentionally in-memory only (invites
    // are meant to be short-lived; they don't need to survive a server restart).
    private final Map<String, java.util.Set<UUID>> pendingInvites = new ConcurrentHashMap<>();

    public TeamManager(TeamsPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void loadAll() {
        teamsByKey.clear();
        for (TeamData t : database.loadAllTeams()) {
            teamsByKey.put(t.getKey(), t);
            syncScoreboardTeam(t);
        }
    }

    public static String key(String name) {
        return name.toLowerCase();
    }

    public boolean exists(String name) {
        return teamsByKey.containsKey(key(name));
    }

    public Optional<TeamData> getTeam(String name) {
        return Optional.ofNullable(teamsByKey.get(key(name)));
    }

    public Map<String, TeamData> all() {
        return teamsByKey;
    }

    /** Creates a new team owned by the given player. Caller must have already validated the name is free. */
    public TeamData createTeam(String name, UUID owner) {
        TeamData team = new TeamData(
                key(name), name,
                plugin.getConfig().getString("team.default-prefix-color", "WHITE"),
                owner, 0.0, System.currentTimeMillis(), TeamData.JoinMode.OPEN
        );
        teamsByKey.put(team.getKey(), team);
        database.upsertTeam(team);
        syncScoreboardTeam(team);
        return team;
    }

    // ------------------------------------------------------------- INVITES

    public void invite(TeamData team, UUID playerId) {
        pendingInvites.computeIfAbsent(team.getKey(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public void uninvite(TeamData team, UUID playerId) {
        java.util.Set<UUID> set = pendingInvites.get(team.getKey());
        if (set != null) set.remove(playerId);
    }

    public boolean isInvited(TeamData team, UUID playerId) {
        java.util.Set<UUID> set = pendingInvites.get(team.getKey());
        return set != null && set.contains(playerId);
    }

    /** Call once the invite has been used (player joined) or the team is disbanded. */
    public void clearInvite(TeamData team, UUID playerId) {
        java.util.Set<UUID> set = pendingInvites.get(team.getKey());
        if (set != null) set.remove(playerId);
    }

    private void clearAllInvites(String teamKey) {
        pendingInvites.remove(teamKey);
    }

    public void disbandTeam(TeamData team) {
        teamsByKey.remove(team.getKey());
        database.deleteTeam(team.getKey());
        clearAllInvites(team.getKey());

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(scoreboardTeamName(team.getKey()));
        if (sbTeam != null) {
            sbTeam.unregister();
        }
    }

    public void saveTeam(TeamData team) {
        database.upsertTeam(team);
        syncScoreboardTeam(team);
    }

    /** Creates/updates the vanilla scoreboard Team so the prefix shows in tab list + above-head + (via ChatListener) chat. */
    public void syncScoreboardTeam(TeamData team) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String sbName = scoreboardTeamName(team.getKey());
        Team sbTeam = board.getTeam(sbName);
        if (sbTeam == null) {
            sbTeam = board.registerNewTeam(sbName);
        }
        ChatColor color = com.perfectone.teams.util.ColorUtil.parse(team.getPrefixColor());
        if (color == null) color = ChatColor.WHITE;
        String prefix = color + "[" + team.getDisplayName() + "]" + ChatColor.RESET + " ";
        sbTeam.setPrefix(trimTo64(prefix));
        sbTeam.setColor(color);
    }

    /** Adds every current member of this team to the scoreboard team entry list (call on join/leave/startup). */
    public void addPlayerToScoreboardTeam(TeamData team, String playerName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(scoreboardTeamName(team.getKey()));
        if (sbTeam != null) {
            sbTeam.addEntry(playerName);
        }
    }

    public void removePlayerFromAllScoreboardTeams(String playerName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : board.getTeams()) {
            if (t.hasEntry(playerName)) {
                t.removeEntry(playerName);
            }
        }
    }

    private String scoreboardTeamName(String key) {
        // Scoreboard team names must be <= 16 chars pre-1.18 but Paper 1.13+/1.18+ relaxed this; keep it short & safe anyway.
        String safe = "pt_" + key;
        return safe.length() > 16 ? safe.substring(0, 16) : safe;
    }

    private String trimTo64(String s) {
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    public String colorizedPrefix(TeamData team) {
        ChatColor color = com.perfectone.teams.util.ColorUtil.parse(team.getPrefixColor());
        if (color == null) color = ChatColor.WHITE;
        return color + "[" + team.getDisplayName() + "]" + ChatColor.RESET;
    }
}
