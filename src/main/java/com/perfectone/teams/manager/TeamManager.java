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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all TeamData in memory, persists changes to SQLite, and mirrors
 * each team onto the main server Scoreboard so prefixes show in tab + chat.
 */
public class TeamManager {

    public enum AllyResult { REQUEST_SENT, NOW_ALLIED, ALREADY_ALLIED, CANNOT_ALLY_SELF }

    private final TeamsPlugin plugin;
    private final Database database;
    private final Map<String, TeamData> teamsByKey = new ConcurrentHashMap<>();
    // team key -> set of invited player UUIDs. Intentionally in-memory only (invites
    // are meant to be short-lived; they don't need to survive a server restart).
    private final Map<String, Set<UUID>> pendingInvites = new ConcurrentHashMap<>();
    // team key -> set of allied team keys. Persisted to the alliances table, symmetric.
    private final Map<String, Set<String>> alliances = new ConcurrentHashMap<>();
    // requesting team key -> set of target team keys they've proposed an alliance to.
    // In-memory only, same reasoning as invites.
    private final Map<String, Set<String>> pendingAllyRequests = new ConcurrentHashMap<>();

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
        loadAlliances();
    }

    private void loadAlliances() {
        alliances.clear();
        for (String[] pair : database.loadAllAlliances()) {
            alliances.computeIfAbsent(pair[0], k -> ConcurrentHashMap.newKeySet()).add(pair[1]);
            alliances.computeIfAbsent(pair[1], k -> ConcurrentHashMap.newKeySet()).add(pair[0]);
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

    // ------------------------------------------------------------- ALLIES

    public boolean areAllied(String teamKeyA, String teamKeyB) {
        Set<String> set = alliances.get(teamKeyA);
        return set != null && set.contains(teamKeyB);
    }

    /**
     * Proposes an alliance from `us` to `target`. If `target` already proposed
     * an alliance to `us`, this immediately completes it (mutual accept).
     */
    public AllyResult proposeAlly(TeamData us, TeamData target) {
        if (us.getKey().equals(target.getKey())) {
            return AllyResult.CANNOT_ALLY_SELF;
        }
        if (areAllied(us.getKey(), target.getKey())) {
            return AllyResult.ALREADY_ALLIED;
        }

        Set<String> theirRequestsToUs = pendingAllyRequests.get(target.getKey());
        if (theirRequestsToUs != null && theirRequestsToUs.contains(us.getKey())) {
            // They already asked us - this call completes the alliance.
            theirRequestsToUs.remove(us.getKey());
            Set<String> ourRequests = pendingAllyRequests.get(us.getKey());
            if (ourRequests != null) ourRequests.remove(target.getKey());

            alliances.computeIfAbsent(us.getKey(), k -> ConcurrentHashMap.newKeySet()).add(target.getKey());
            alliances.computeIfAbsent(target.getKey(), k -> ConcurrentHashMap.newKeySet()).add(us.getKey());
            database.addAlliance(us.getKey(), target.getKey());
            return AllyResult.NOW_ALLIED;
        }

        pendingAllyRequests.computeIfAbsent(us.getKey(), k -> ConcurrentHashMap.newKeySet()).add(target.getKey());
        return AllyResult.REQUEST_SENT;
    }

    public boolean hasPendingAllyRequestTo(String fromTeamKey, String toTeamKey) {
        Set<String> set = pendingAllyRequests.get(fromTeamKey);
        return set != null && set.contains(toTeamKey);
    }

    /** Breaks an alliance (or cancels a pending request either direction) between the two teams. Unilateral. */
    public void removeAlly(TeamData us, TeamData target) {
        Set<String> ourSet = alliances.get(us.getKey());
        if (ourSet != null) ourSet.remove(target.getKey());
        Set<String> theirSet = alliances.get(target.getKey());
        if (theirSet != null) theirSet.remove(us.getKey());
        database.removeAlliance(us.getKey(), target.getKey());

        Set<String> ourRequests = pendingAllyRequests.get(us.getKey());
        if (ourRequests != null) ourRequests.remove(target.getKey());
        Set<String> theirRequests = pendingAllyRequests.get(target.getKey());
        if (theirRequests != null) theirRequests.remove(us.getKey());
    }

    public Set<String> getAllies(String teamKey) {
        return alliances.getOrDefault(teamKey, Set.of());
    }

    private void clearAllAlliancesFor(String teamKey) {
        Set<String> allied = alliances.remove(teamKey);
        if (allied != null) {
            for (String otherKey : allied) {
                Set<String> otherSet = alliances.get(otherKey);
                if (otherSet != null) otherSet.remove(teamKey);
            }
        }
        pendingAllyRequests.remove(teamKey);
        for (Set<String> requests : pendingAllyRequests.values()) {
            requests.remove(teamKey);
        }
        database.removeAllAlliancesFor(teamKey);
    }

    public void disbandTeam(TeamData team) {
        teamsByKey.remove(team.getKey());
        database.deleteTeam(team.getKey());
        clearAllInvites(team.getKey());
        clearAllAlliancesFor(team.getKey());

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
