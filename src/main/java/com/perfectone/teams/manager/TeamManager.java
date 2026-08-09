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
    }

    private void loadAlliances() {
        this.alliances.clear();
        for (String[] pair : this.database.loadAllAlliances()) {
            this.alliances.computeIfAbsent(pair[0], k -> ConcurrentHashMap.newKeySet()).add(pair[1]);
            this.alliances.computeIfAbsent(pair[1], k -> ConcurrentHashMap.newKeySet()).add(pair[0]);
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

    public void disbandTeam(TeamData team) {
        this.teamsByKey.remove(team.getKey());
        this.database.deleteTeam(team.getKey());
        this.clearAllInvites(team.getKey());
        this.clearAllAlliancesFor(team.getKey());
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(this.scoreboardTeamName(team.getKey()));
        if (sbTeam != null) {
            sbTeam.unregister();
        }
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

