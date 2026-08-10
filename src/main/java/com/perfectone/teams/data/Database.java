/*
 * Decompiled with CFR 0.152.
 */
package com.perfectone.teams.data;

import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class Database {
    private final Logger logger;
    private final File dbFile;
    private Connection connection;

    public Database(File dataFolder, String fileName, Logger logger) {
        this.logger = logger;
        this.dbFile = new File(dataFolder, fileName);
    }

    public void connect() {
        try {
            if (!this.dbFile.getParentFile().exists()) {
                this.dbFile.getParentFile().mkdirs();
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbFile.getAbsolutePath());
            try (Statement st = this.connection.createStatement();){
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA foreign_keys=ON;");
            }
            this.createTables();
        }
        catch (SQLException e) {
            this.logger.severe("Failed to connect to SQLite database: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
        }
        catch (SQLException e) {
            this.logger.warning("Failed to close database connection: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = this.connection.createStatement();){
            st.execute("    CREATE TABLE IF NOT EXISTS teams (\n        team_key      TEXT PRIMARY KEY,\n        display_name  TEXT NOT NULL,\n        prefix_color  TEXT NOT NULL DEFAULT 'WHITE',\n        owner_uuid    TEXT,\n        score         REAL NOT NULL DEFAULT 0,\n        created_at    INTEGER NOT NULL,\n        join_mode     TEXT NOT NULL DEFAULT 'OPEN'\n    );\n");
            st.execute("    CREATE TABLE IF NOT EXISTS players (\n        uuid            TEXT PRIMARY KEY,\n        username        TEXT NOT NULL,\n        team_key        TEXT,\n        personal_score  REAL NOT NULL DEFAULT 0,\n        kills           INTEGER NOT NULL DEFAULT 0,\n        deaths          INTEGER NOT NULL DEFAULT 0,\n        bounty          REAL NOT NULL DEFAULT 0,\n        updated_at      INTEGER NOT NULL\n    );\n");
            st.execute("    CREATE TABLE IF NOT EXISTS alliances (\n        team_a TEXT NOT NULL,\n        team_b TEXT NOT NULL,\n        PRIMARY KEY (team_a, team_b)\n    );\n");
            st.execute("    CREATE TABLE IF NOT EXISTS wars (\n        team_a TEXT NOT NULL,\n        team_b TEXT NOT NULL,\n        PRIMARY KEY (team_a, team_b)\n    );\n");
            st.execute("    CREATE TABLE IF NOT EXISTS enemies (\n        declarer_team TEXT NOT NULL,\n        target_team   TEXT NOT NULL,\n        PRIMARY KEY (declarer_team, target_team)\n    );\n");
        }
        this.migrateSchema();
    }

    private void migrateSchema() throws SQLException {
        if (!this.columnExists("teams", "join_mode")) {
            try (Statement st = this.connection.createStatement();){
                st.execute("ALTER TABLE teams ADD COLUMN join_mode TEXT NOT NULL DEFAULT 'OPEN';");
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ");");){
            while (rs.next()) {
                if (!rs.getString("name").equalsIgnoreCase(column)) continue;
                boolean bl = true;
                return bl;
            }
        }
        return false;
    }

    public synchronized void upsertTeam(TeamData team) {
        String sql = "    INSERT INTO teams (team_key, display_name, prefix_color, owner_uuid, score, created_at, join_mode)\n    VALUES (?, ?, ?, ?, ?, ?, ?)\n    ON CONFLICT(team_key) DO UPDATE SET\n        display_name=excluded.display_name,\n        prefix_color=excluded.prefix_color,\n        owner_uuid=excluded.owner_uuid,\n        score=excluded.score,\n        join_mode=excluded.join_mode;\n";
        try (PreparedStatement ps = this.connection.prepareStatement(sql);){
            ps.setString(1, team.getKey());
            ps.setString(2, team.getDisplayName());
            ps.setString(3, team.getPrefixColor());
            ps.setString(4, team.getOwner() == null ? null : team.getOwner().toString());
            ps.setDouble(5, team.getScore());
            ps.setLong(6, team.getCreatedAt());
            ps.setString(7, team.getJoinMode().name());
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("upsertTeam failed: " + e.getMessage());
        }
    }

    public synchronized void deleteTeam(String teamKey) {
        try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM teams WHERE team_key = ?");){
            ps.setString(1, teamKey);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("deleteTeam failed: " + e.getMessage());
        }
    }

    public synchronized List<TeamData> loadAllTeams() {
        ArrayList<TeamData> list = new ArrayList<TeamData>();
        String sql = "SELECT team_key, display_name, prefix_color, owner_uuid, score, created_at, join_mode FROM teams";
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery(sql);){
            while (rs.next()) {
                TeamData.JoinMode mode;
                UUID owner = rs.getString("owner_uuid") == null ? null : UUID.fromString(rs.getString("owner_uuid"));
                try {
                    mode = TeamData.JoinMode.valueOf(rs.getString("join_mode"));
                }
                catch (Exception ex) {
                    mode = TeamData.JoinMode.OPEN;
                }
                list.add(new TeamData(rs.getString("team_key"), rs.getString("display_name"), rs.getString("prefix_color"), owner, rs.getDouble("score"), rs.getLong("created_at"), mode));
            }
        }
        catch (SQLException e) {
            this.logger.warning("loadAllTeams failed: " + e.getMessage());
        }
        return list;
    }

    public synchronized void upsertPlayer(PlayerData p) {
        String sql = "    INSERT INTO players (uuid, username, team_key, personal_score, kills, deaths, bounty, updated_at)\n    VALUES (?, ?, ?, ?, ?, ?, ?, ?)\n    ON CONFLICT(uuid) DO UPDATE SET\n        username=excluded.username,\n        team_key=excluded.team_key,\n        personal_score=excluded.personal_score,\n        kills=excluded.kills,\n        deaths=excluded.deaths,\n        bounty=excluded.bounty,\n        updated_at=excluded.updated_at;\n";
        try (PreparedStatement ps = this.connection.prepareStatement(sql);){
            ps.setString(1, p.getUuid().toString());
            ps.setString(2, p.getUsername());
            ps.setString(3, p.isCivilian() ? null : p.getTeamKey());
            ps.setDouble(4, p.getPersonalScore());
            ps.setInt(5, p.getKills());
            ps.setInt(6, p.getDeaths());
            ps.setDouble(7, p.getBounty());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("upsertPlayer failed: " + e.getMessage());
        }
    }

    public synchronized List<PlayerData> loadAllPlayers() {
        ArrayList<PlayerData> list = new ArrayList<PlayerData>();
        String sql = "SELECT uuid, username, team_key, personal_score, kills, deaths, bounty FROM players";
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery(sql);){
            while (rs.next()) {
                list.add(new PlayerData(UUID.fromString(rs.getString("uuid")), rs.getString("username"), rs.getString("team_key"), rs.getDouble("personal_score"), rs.getInt("kills"), rs.getInt("deaths"), rs.getDouble("bounty")));
            }
        }
        catch (SQLException e) {
            this.logger.warning("loadAllPlayers failed: " + e.getMessage());
        }
        return list;
    }

    private String[] canonicalPair(String teamKeyA, String teamKeyB) {
        String[] stringArray;
        if (teamKeyA.compareTo(teamKeyB) <= 0) {
            String[] stringArray2 = new String[2];
            stringArray2[0] = teamKeyA;
            stringArray = stringArray2;
            stringArray2[1] = teamKeyB;
        } else {
            String[] stringArray3 = new String[2];
            stringArray3[0] = teamKeyB;
            stringArray = stringArray3;
            stringArray3[1] = teamKeyA;
        }
        return stringArray;
    }

    public synchronized void addAlliance(String teamKeyA, String teamKeyB) {
        String[] pair = this.canonicalPair(teamKeyA, teamKeyB);
        try (PreparedStatement ps = this.connection.prepareStatement("INSERT OR IGNORE INTO alliances (team_a, team_b) VALUES (?, ?)");){
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("addAlliance failed: " + e.getMessage());
        }
    }

    public synchronized void removeAlliance(String teamKeyA, String teamKeyB) {
        String[] pair = this.canonicalPair(teamKeyA, teamKeyB);
        try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM alliances WHERE team_a = ? AND team_b = ?");){
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("removeAlliance failed: " + e.getMessage());
        }
    }

    public synchronized void removeAllAlliancesFor(String teamKey) {
        try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM alliances WHERE team_a = ? OR team_b = ?");){
            ps.setString(1, teamKey);
            ps.setString(2, teamKey);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("removeAllAlliancesFor failed: " + e.getMessage());
        }
    }

    public synchronized List<String[]> loadAllAlliances() {
        ArrayList<String[]> list = new ArrayList<String[]>();
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT team_a, team_b FROM alliances");){
            while (rs.next()) {
                list.add(new String[]{rs.getString("team_a"), rs.getString("team_b")});
            }
        }
        catch (SQLException e) {
            this.logger.warning("loadAllAlliances failed: " + e.getMessage());
        }
        return list;
    }

    // ---- Wars (mutual, canonical-pair, same shape as alliances) ----

    public synchronized void addWar(String teamKeyA, String teamKeyB) {
        String[] pair = this.canonicalPair(teamKeyA, teamKeyB);
        try (PreparedStatement ps = this.connection.prepareStatement("INSERT OR IGNORE INTO wars (team_a, team_b) VALUES (?, ?)");){
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("addWar failed: " + e.getMessage());
        }
    }

    public synchronized void removeWar(String teamKeyA, String teamKeyB) {
        String[] pair = this.canonicalPair(teamKeyA, teamKeyB);
        try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM wars WHERE team_a = ? AND team_b = ?");){
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("removeWar failed: " + e.getMessage());
        }
    }

    public synchronized void removeAllWarsFor(String teamKey) {
        try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM wars WHERE team_a = ? OR team_b = ?");){
            ps.setString(1, teamKey);
            ps.setString(2, teamKey);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("removeAllWarsFor failed: " + e.getMessage());
        }
    }

    public synchronized List<String[]> loadAllWars() {
        ArrayList<String[]> list = new ArrayList<String[]>();
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT team_a, team_b FROM wars");){
            while (rs.next()) {
                list.add(new String[]{rs.getString("team_a"), rs.getString("team_b")});
            }
        }
        catch (SQLException e) {
            this.logger.warning("loadAllWars failed: " + e.getMessage());
        }
        return list;
    }

    // ---- Enemies (unilateral - declarer_team marks target_team as an enemy) ----

    public synchronized void addEnemy(String declarerTeam, String targetTeam) {
        try (PreparedStatement ps = this.connection.prepareStatement("INSERT OR IGNORE INTO enemies (declarer_team, target_team) VALUES (?, ?)");){
            ps.setString(1, declarerTeam);
            ps.setString(2, targetTeam);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("addEnemy failed: " + e.getMessage());
        }
    }

    public synchronized void removeEnemy(String declarerTeam, String targetTeam) {
        try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM enemies WHERE declarer_team = ? AND target_team = ?");){
            ps.setString(1, declarerTeam);
            ps.setString(2, targetTeam);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("removeEnemy failed: " + e.getMessage());
        }
    }

    public synchronized void removeAllEnemiesFor(String teamKey) {
        try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM enemies WHERE declarer_team = ? OR target_team = ?");){
            ps.setString(1, teamKey);
            ps.setString(2, teamKey);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            this.logger.warning("removeAllEnemiesFor failed: " + e.getMessage());
        }
    }

    public synchronized List<String[]> loadAllEnemies() {
        ArrayList<String[]> list = new ArrayList<String[]>();
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT declarer_team, target_team FROM enemies");){
            while (rs.next()) {
                list.add(new String[]{rs.getString("declarer_team"), rs.getString("target_team")});
            }
        }
        catch (SQLException e) {
            this.logger.warning("loadAllEnemies failed: " + e.getMessage());
        }
        return list;
    }
}

