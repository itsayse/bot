package com.perfectone.teams.util;

import com.perfectone.teams.TeamsPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads plugins/PerfectTeams/bad.txt (one banned word per line, blank lines
 * and lines starting with "#" are ignored) and checks whether a proposed
 * team name contains any of those words. A default list ships with the
 * plugin; admins can freely add/remove lines and reload with
 * "/team reloadbanlist" without restarting the server.
 */
public class BannedNameManager {

    private final TeamsPlugin plugin;
    private final File file;
    private Set<String> bannedWords = Collections.emptySet();

    public BannedNameManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bad.txt");
    }

    /** Copies the bundled default bad.txt on first run, then loads whatever is on disk. */
    public void loadOrCreate() {
        if (!file.exists()) {
            plugin.saveResource("bad.txt", false);
        }
        reload();
    }

    public void reload() {
        Set<String> words = new HashSet<>();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    words.add(trimmed);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load bad.txt: " + e.getMessage());
            }
        }
        this.bannedWords = words;
        plugin.getLogger().info("Loaded " + bannedWords.size() + " banned team-name words from bad.txt");
    }

    /** True if the proposed name contains a banned word anywhere in it (case-insensitive). */
    public boolean isBanned(String name) {
        String lower = name.toLowerCase();
        for (String word : bannedWords) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return bannedWords.size();
    }
}
