package com.perfectone.teams.data;

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

/**
 * Thin wrapper around a SQLite database shared with the Discord bot.
 * All methods are blocking JDBC calls - callers from the main server thread
 * should dispatch through the Bukkit async scheduler.
 */
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
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;"); // safer for concurrent readers (the discord bot)
                st.execute("PRAGMA foreign_keys=ON;");
            }
            createTables();
        } catch (SQLException e) {
            logger.severe("Failed to connect to SQLite database: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("Failed to close database connection: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS teams (
                    team_key      TEXT PRIMARY KEY,
                    display_name  TEXT NOT NULL,
                    prefix_color  TEXT NOT NULL DEFAULT 'WHITE',
                    owner_uuid    TEXT,
                    score         REAL NOT NULL DEFAULT 0,
                    created_at    INTEGER NOT NULL,
                    join_mode     TEXT NOT NULL DEFAULT 'OPEN'
                );
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid            TEXT PRIMARY KEY,
                    username        TEXT NOT NULL,
                    team_key        TEXT,
                    personal_score  REAL NOT NULL DEFAULT 0,
                    kills           INTEGER NOT NULL DEFAULT 0,
                    deaths          INTEGER NOT NULL DEFAULT 0,
                    bounty          REAL NOT NULL DEFAULT 0,
                    updated_at      INTEGER NOT NULL
                );
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS alliances (
                    team_a TEXT NOT NULL,
                    team_b TEXT NOT NULL,
                    PRIMARY KEY (team_a, team_b)
                );
            """);
        }
        migrateSchema();
    }

    /** Adds columns introduced after the initial release, for upgrades from older PerfectTeams versions. */
    private void migrateSchema() throws SQLException {
        if (!columnExists("teams", "join_mode")) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE teams ADD COLUMN join_mode TEXT NOT NULL DEFAULT 'OPEN';");
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ");")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- TEAMS

    public synchronized void upsertTeam(TeamData team) {
        String sql = """
            INSERT INTO teams (team_key, display_name, prefix_color, owner_uuid, score, created_at, join_mode)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(team_key) DO UPDATE SET
                display_name=excluded.display_name,
                prefix_color=excluded.prefix_color,
                owner_uuid=excluded.owner_uuid,
                score=excluded.score,
                join_mode=excluded.join_mode;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, team.getKey());
            ps.setString(2, team.getDisplayName());
            ps.setString(3, team.getPrefixColor());
            ps.setString(4, team.getOwner() == null ? null : team.getOwner().toString());
            ps.setDouble(5, team.getScore());
            ps.setLong(6, team.getCreatedAt());
            ps.setString(7, team.getJoinMode().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("upsertTeam failed: " + e.getMessage());
        }
    }

    public synchronized void deleteTeam(String teamKey) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM teams WHERE team_key = ?")) {
            ps.setString(1, teamKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("deleteTeam failed: " + e.getMessage());
        }
    }

    public synchronized List<TeamData> loadAllTeams() {
        List<TeamData> list = new ArrayList<>();
        String sql = "SELECT team_key, display_name, prefix_color, owner_uuid, score, created_at, join_mode FROM teams";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID owner = rs.getString("owner_uuid") == null ? null : UUID.fromString(rs.getString("owner_uuid"));
                TeamData.JoinMode mode;
                try {
                    mode = TeamData.JoinMode.valueOf(rs.getString("join_mode"));
                } catch (Exception ex) {
                    mode = TeamData.JoinMode.OPEN;
                }
                list.add(new TeamData(
                        rs.getString("team_key"),
                        rs.getString("display_name"),
                        rs.getString("prefix_color"),
                        owner,
                        rs.getDouble("score"),
                        rs.getLong("created_at"),
                        mode
                ));
            }
        } catch (SQLException e) {
            logger.warning("loadAllTeams failed: " + e.getMessage());
        }
        return list;
    }

    // -------------------------------------------------------------- PLAYERS

    public synchronized void upsertPlayer(PlayerData p) {
        String sql = """
            INSERT INTO players (uuid, username, team_key, personal_score, kills, deaths, bounty, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                username=excluded.username,
                team_key=excluded.team_key,
                personal_score=excluded.personal_score,
                kills=excluded.kills,
                deaths=excluded.deaths,
                bounty=excluded.bounty,
                updated_at=excluded.updated_at;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getUuid().toString());
            ps.setString(2, p.getUsername());
            ps.setString(3, p.isCivilian() ? null : p.getTeamKey());
            ps.setDouble(4, p.getPersonalScore());
            ps.setInt(5, p.getKills());
            ps.setInt(6, p.getDeaths());
            ps.setDouble(7, p.getBounty());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("upsertPlayer failed: " + e.getMessage());
        }
    }

    public synchronized List<PlayerData> loadAllPlayers() {
        List<PlayerData> list = new ArrayList<>();
        String sql = "SELECT uuid, username, team_key, personal_score, kills, deaths, bounty FROM players";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PlayerData(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("username"),
                        rs.getString("team_key"),
                        rs.getDouble("personal_score"),
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getDouble("bounty")
                ));
            }
        } catch (SQLException e) {
            logger.warning("loadAllPlayers failed: " + e.getMessage());
        }
        return list;
    }

    // ------------------------------------------------------------ ALLIANCES

    /** Always stores/looks up the pair in alphabetical order so (A,B) and (B,A) are the same row. */
    private String[] canonicalPair(String teamKeyA, String teamKeyB) {
        return teamKeyA.compareTo(teamKeyB) <= 0
                ? new String[]{teamKeyA, teamKeyB}
                : new String[]{teamKeyB, teamKeyA};
    }

    public synchronized void addAlliance(String teamKeyA, String teamKeyB) {
        String[] pair = canonicalPair(teamKeyA, teamKeyB);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO alliances (team_a, team_b) VALUES (?, ?)")) {
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("addAlliance failed: " + e.getMessage());
        }
    }

    public synchronized void removeAlliance(String teamKeyA, String teamKeyB) {
        String[] pair = canonicalPair(teamKeyA, teamKeyB);
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM alliances WHERE team_a = ? AND team_b = ?")) {
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("removeAlliance failed: " + e.getMessage());
        }
    }

    /** Also removes every alliance a disbanded team was part of. */
    public synchronized void removeAllAlliancesFor(String teamKey) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM alliances WHERE team_a = ? OR team_b = ?")) {
            ps.setString(1, teamKey);
            ps.setString(2, teamKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("removeAllAlliancesFor failed: " + e.getMessage());
        }
    }

    /** Returns every alliance pair as [teamKeyA, teamKeyB]. */
    public synchronized List<String[]> loadAllAlliances() {
        List<String[]> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT team_a, team_b FROM alliances")) {
            while (rs.next()) {
                list.add(new String[]{rs.getString("team_a"), rs.getString("team_b")});
            }
        } catch (SQLException e) {
            logger.warning("loadAllAlliances failed: " + e.getMessage());
        }
        return list;
    }
}
