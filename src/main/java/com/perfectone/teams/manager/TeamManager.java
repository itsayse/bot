/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.scoreboard.Scoreboard
 *  org.bukkit.scoreboard.Team
 */
package com.perfectone.teams.manager;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.Database;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.util.ColorUtil;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class TeamManager {
    private final TeamsPlugin plugin;
    private final Database database;
    private final Map<String, TeamData> teamsByKey = new ConcurrentHashMap<String, TeamData>();
    private final Map<String, Set<UUID>> pendingInvites = new ConcurrentHashMap<String, Set<UUID>>();
    private final Map<String, Set<String>> alliances = new ConcurrentHashMap<String, Set<String>>();
    private final Map<String, Set<String>> pendingAllyRequests = new ConcurrentHashMap<String, Set<String>>();
    private final Map<String, Set<String>> wars = new ConcurrentHashMap<String, Set<String>>();
    private final Map<String, Set<String>> pendingWarRequests = new ConcurrentHashMap<String, Set<String>>();
    // Unilateral: declarer key -> set of teams it has marked as enemies. No consent needed.
    private final Map<String, Set<String>> enemies = new ConcurrentHashMap<String, Set<String>>();

    public TeamManager(TeamsPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void loadAll() {
        this.teamsByKey.clear();
        for (TeamData t : this.database.loadAllTeams()) {
            this.teamsByKey.put(t.getKey(), t);
            this.syncScoreboardTeam(t);
        }
        this.loadAlliances();
        this.loadWars();
        this.loadEnemies();
    }

    private void loadAlliances() {
        this.alliances.clear();
        for (String[] pair : this.database.loadAllAlliances()) {
            this.alliances.computeIfAbsent(pair[0], k -> ConcurrentHashMap.newKeySet()).add(pair[1]);
            this.alliances.computeIfAbsent(pair[1], k -> ConcurrentHashMap.newKeySet()).add(pair[0]);
        }
    }

    private void loadWars() {
        this.wars.clear();
        for (String[] pair : this.database.loadAllWars()) {
            this.wars.computeIfAbsent(pair[0], k -> ConcurrentHashMap.newKeySet()).add(pair[1]);
            this.wars.computeIfAbsent(pair[1], k -> ConcurrentHashMap.newKeySet()).add(pair[0]);
        }
    }

    private void loadEnemies() {
        this.enemies.clear();
        for (String[] pair : this.database.loadAllEnemies()) {
            this.enemies.computeIfAbsent(pair[0], k -> ConcurrentHashMap.newKeySet()).add(pair[1]);
        }
    }

    public static String key(String name) {
        return name.toLowerCase();
    }

    public boolean exists(String name) {
        return this.teamsByKey.containsKey(TeamManager.key(name));
    }

    public Optional<TeamData> getTeam(String name) {
        return Optional.ofNullable(this.teamsByKey.get(TeamManager.key(name)));
    }

    public Map<String, TeamData> all() {
        return this.teamsByKey;
    }

    public TeamData createTeam(String name, UUID owner) {
        TeamData team = new TeamData(TeamManager.key(name), name, this.plugin.getConfig().getString("team.default-prefix-color", "WHITE"), owner, 0.0, System.currentTimeMillis(), TeamData.JoinMode.OPEN);
        this.teamsByKey.put(team.getKey(), team);
        this.database.upsertTeam(team);
        this.syncScoreboardTeam(team);
        return team;
    }

    public void invite(TeamData team, UUID playerId) {
        this.pendingInvites.computeIfAbsent(team.getKey(), k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public void uninvite(TeamData team, UUID playerId) {
        Set<UUID> set = this.pendingInvites.get(team.getKey());
        if (set != null) {
            set.remove(playerId);
        }
    }

    public boolean isInvited(TeamData team, UUID playerId) {
        Set<UUID> set = this.pendingInvites.get(team.getKey());
        return set != null && set.contains(playerId);
    }

    public void clearInvite(TeamData team, UUID playerId) {
        Set<UUID> set = this.pendingInvites.get(team.getKey());
        if (set != null) {
            set.remove(playerId);
        }
    }

    private void clearAllInvites(String teamKey) {
        this.pendingInvites.remove(teamKey);
    }

    public boolean areAllied(String teamKeyA, String teamKeyB) {
        Set<String> set = this.alliances.get(teamKeyA);
        return set != null && set.contains(teamKeyB);
    }

    public AllyResult proposeAlly(TeamData us, TeamData target) {
        if (us.getKey().equals(target.getKey())) {
            return AllyResult.CANNOT_ALLY_SELF;
        }
        if (this.areAllied(us.getKey(), target.getKey())) {
            return AllyResult.ALREADY_ALLIED;
        }
        Set<String> theirRequestsToUs = this.pendingAllyRequests.get(target.getKey());
        if (theirRequestsToUs != null && theirRequestsToUs.contains(us.getKey())) {
            theirRequestsToUs.remove(us.getKey());
            Set<String> ourRequests = this.pendingAllyRequests.get(us.getKey());
            if (ourRequests != null) {
                ourRequests.remove(target.getKey());
            }
            this.alliances.computeIfAbsent(us.getKey(), k -> ConcurrentHashMap.newKeySet()).add(target.getKey());
            this.alliances.computeIfAbsent(target.getKey(), k -> ConcurrentHashMap.newKeySet()).add(us.getKey());
            this.database.addAlliance(us.getKey(), target.getKey());
            return AllyResult.NOW_ALLIED;
        }
        this.pendingAllyRequests.computeIfAbsent(us.getKey(), k -> ConcurrentHashMap.newKeySet()).add(target.getKey());
        return AllyResult.REQUEST_SENT;
    }

    public boolean hasPendingAllyRequestTo(String fromTeamKey, String toTeamKey) {
        Set<String> set = this.pendingAllyRequests.get(fromTeamKey);
        return set != null && set.contains(toTeamKey);
    }

    public void removeAlly(TeamData us, TeamData target) {
        Set<String> theirRequests;
        Set<String> theirSet;
        Set<String> ourSet = this.alliances.get(us.getKey());
        if (ourSet != null) {
            ourSet.remove(target.getKey());
        }
        if ((theirSet = this.alliances.get(target.getKey())) != null) {
            theirSet.remove(us.getKey());
        }
        this.database.removeAlliance(us.getKey(), target.getKey());
        Set<String> ourRequests = this.pendingAllyRequests.get(us.getKey());
        if (ourRequests != null) {
            ourRequests.remove(target.getKey());
        }
        if ((theirRequests = this.pendingAllyRequests.get(target.getKey())) != null) {
            theirRequests.remove(us.getKey());
        }
    }

    public Set<String> getAllies(String teamKey) {
        return this.alliances.getOrDefault(teamKey, Set.of());
    }

    private void clearAllAlliancesFor(String teamKey) {
        Set<String> allied = this.alliances.remove(teamKey);
        if (allied != null) {
            for (String string : allied) {
                Set<String> otherSet = this.alliances.get(string);
                if (otherSet == null) continue;
                otherSet.remove(teamKey);
            }
        }
        this.pendingAllyRequests.remove(teamKey);
        for (Set set : this.pendingAllyRequests.values()) {
            set.remove(teamKey);
        }
        this.database.removeAllAlliancesFor(teamKey);
    }

    private void clearAllWarsFor(String teamKey) {
        Set<String> atWarWith = this.wars.remove(teamKey);
        if (atWarWith != null) {
            for (String other : atWarWith) {
                Set<String> otherSet = this.wars.get(other);
                if (otherSet != null) {
                    otherSet.remove(teamKey);
                }
            }
        }
        this.pendingWarRequests.remove(teamKey);
        for (Set<String> set : this.pendingWarRequests.values()) {
            set.remove(teamKey);
        }
        this.database.removeAllWarsFor(teamKey);
    }

    private void clearAllEnemiesFor(String teamKey) {
        this.enemies.remove(teamKey);
        for (Set<String> set : this.enemies.values()) {
            set.remove(teamKey);
        }
        this.database.removeAllEnemiesFor(teamKey);
    }

    public void disbandTeam(TeamData team) {
        this.teamsByKey.remove(team.getKey());
        this.database.deleteTeam(team.getKey());
        this.clearAllInvites(team.getKey());
        this.clearAllAlliancesFor(team.getKey());
        this.clearAllWarsFor(team.getKey());
        this.clearAllEnemiesFor(team.getKey());
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(this.scoreboardTeamName(team.getKey()));
        if (sbTeam != null) {
            sbTeam.unregister();
        }
    }

    // ---- Wars: mutual, request/accept/deny flow (mirrors alliances) ----

    public boolean isAtWar(String teamKeyA, String teamKeyB) {
        Set<String> set = this.wars.get(teamKeyA);
        return set != null && set.contains(teamKeyB);
    }

    public boolean hasPendingWarRequestTo(String fromTeamKey, String toTeamKey) {
        Set<String> set = this.pendingWarRequests.get(fromTeamKey);
        return set != null && set.contains(toTeamKey);
    }

    /** Sends a war request to the target team. They must run /team war accept to start the war. */
    public WarResult proposeWar(TeamData us, TeamData target) {
        if (us.getKey().equals(target.getKey())) {
            return WarResult.CANNOT_WAR_SELF;
        }
        if (this.isAtWar(us.getKey(), target.getKey())) {
            return WarResult.ALREADY_AT_WAR;
        }
        this.pendingWarRequests.computeIfAbsent(us.getKey(), k -> ConcurrentHashMap.newKeySet()).add(target.getKey());
        return WarResult.REQUEST_SENT;
    }

    /** Explicit accept - requires a pending request from targetTeam to us. */
    public WarResult acceptWar(TeamData us, TeamData target) {
        if (us.getKey().equals(target.getKey())) {
            return WarResult.CANNOT_WAR_SELF;
        }
        if (this.isAtWar(us.getKey(), target.getKey())) {
            return WarResult.ALREADY_AT_WAR;
        }
        if (!this.hasPendingWarRequestTo(target.getKey(), us.getKey())) {
            return WarResult.NO_PENDING_REQUEST;
        }
        this.pendingWarRequests.get(target.getKey()).remove(us.getKey());
        Set<String> ourRequests = this.pendingWarRequests.get(us.getKey());
        if (ourRequests != null) {
            ourRequests.remove(target.getKey());
        }
        this.wars.computeIfAbsent(us.getKey(), k -> ConcurrentHashMap.newKeySet()).add(target.getKey());
        this.wars.computeIfAbsent(target.getKey(), k -> ConcurrentHashMap.newKeySet()).add(us.getKey());
        this.database.addWar(us.getKey(), target.getKey());
        return WarResult.NOW_AT_WAR;
    }

    /** Explicit deny - removes a pending request from targetTeam to us (does not end an active war). */
    public WarResult denyWar(TeamData us, TeamData target) {
        Set<String> theirRequests = this.pendingWarRequests.get(target.getKey());
        boolean had = theirRequests != null && theirRequests.remove(us.getKey());
        return had ? WarResult.REQUEST_DENIED : WarResult.NO_PENDING_REQUEST;
    }

    /** Ends an active war between the two teams (either side can call this). */
    public boolean endWar(TeamData us, TeamData target) {
        boolean wasAtWar = this.isAtWar(us.getKey(), target.getKey());
        Set<String> ourSet = this.wars.get(us.getKey());
        if (ourSet != null) {
            ourSet.remove(target.getKey());
        }
        Set<String> theirSet = this.wars.get(target.getKey());
        if (theirSet != null) {
            theirSet.remove(us.getKey());
        }
        if (wasAtWar) {
            this.database.removeWar(us.getKey(), target.getKey());
        }
        return wasAtWar;
    }

    public Set<String> getWars(String teamKey) {
        return this.wars.getOrDefault(teamKey, Set.of());
    }

    public static enum WarResult {
        REQUEST_SENT,
        NOW_AT_WAR,
        ALREADY_AT_WAR,
        CANNOT_WAR_SELF,
        NO_PENDING_REQUEST,
        REQUEST_DENIED;
    }

    // ---- Enemies: unilateral marking, no consent required ----

    public boolean hasMarkedEnemy(String declarerKey, String targetKey) {
        Set<String> set = this.enemies.get(declarerKey);
        return set != null && set.contains(targetKey);
    }

    /** True if either team has unilaterally marked the other as an enemy. */
    public boolean isEnemyEitherWay(String teamKeyA, String teamKeyB) {
        return this.hasMarkedEnemy(teamKeyA, teamKeyB) || this.hasMarkedEnemy(teamKeyB, teamKeyA);
    }

    public boolean markEnemy(TeamData declarer, TeamData target) {
        if (declarer.getKey().equals(target.getKey())) {
            return false;
        }
        boolean added = this.enemies.computeIfAbsent(declarer.getKey(), k -> ConcurrentHashMap.newKeySet()).add(target.getKey());
        if (added) {
            this.database.addEnemy(declarer.getKey(), target.getKey());
        }
        return added;
    }

    public boolean unmarkEnemy(TeamData declarer, TeamData target) {
        Set<String> set = this.enemies.get(declarer.getKey());
        boolean removed = set != null && set.remove(target.getKey());
        if (removed) {
            this.database.removeEnemy(declarer.getKey(), target.getKey());
        }
        return removed;
    }

    public Set<String> getEnemies(String teamKey) {
        return this.enemies.getOrDefault(teamKey, Set.of());
    }

    // ---- Ownership transfer ----

    public void transferOwnership(TeamData team, UUID newOwner) {
        team.setOwner(newOwner);
        this.saveTeam(team);
    }

    public void saveTeam(TeamData team) {
        this.database.upsertTeam(team);
        this.syncScoreboardTeam(team);
    }

    public void syncScoreboardTeam(TeamData team) {
        ChatColor color;
        String sbName;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(sbName = this.scoreboardTeamName(team.getKey()));
        if (sbTeam == null) {
            sbTeam = board.registerNewTeam(sbName);
        }
        if ((color = ColorUtil.parse(team.getPrefixColor())) == null) {
            color = ChatColor.WHITE;
        }
        String prefix = String.valueOf(color) + "[" + team.getDisplayName() + "]" + String.valueOf(ChatColor.RESET) + " ";
        sbTeam.setPrefix(this.trimTo64(prefix));
        sbTeam.setColor(color);
    }

    public void addPlayerToScoreboardTeam(TeamData team, String playerName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(this.scoreboardTeamName(team.getKey()));
        if (sbTeam != null) {
            sbTeam.addEntry(playerName);
        }
    }

    public void removePlayerFromAllScoreboardTeams(String playerName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : board.getTeams()) {
            if (!t.hasEntry(playerName)) continue;
            t.removeEntry(playerName);
        }
    }

    private String scoreboardTeamName(String key) {
        String safe = "pt_" + key;
        return safe.length() > 16 ? safe.substring(0, 16) : safe;
    }

    private String trimTo64(String s) {
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    public String colorizedPrefix(TeamData team) {
        ChatColor color = ColorUtil.parse(team.getPrefixColor());
        if (color == null) {
            color = ChatColor.WHITE;
        }
        return String.valueOf(color) + "[" + team.getDisplayName() + "]" + String.valueOf(ChatColor.RESET);
    }

    public static enum AllyResult {
        REQUEST_SENT,
        NOW_ALLIED,
        ALREADY_ALLIED,
        CANNOT_ALLY_SELF;

    }
}

