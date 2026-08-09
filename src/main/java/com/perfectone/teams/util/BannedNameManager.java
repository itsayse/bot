/*
 * Decompiled with CFR 0.152.
 */
package com.perfectone.teams.util;

import com.perfectone.teams.TeamsPlugin;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BannedNameManager {
    private final TeamsPlugin plugin;
    private final File file;
    private Set<String> bannedWords = Collections.emptySet();

    public BannedNameManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bad.txt");
    }

    public void loadOrCreate() {
        if (!this.file.exists()) {
            this.plugin.saveResource("bad.txt", false);
        }
        this.reload();
    }

    public void reload() {
        HashSet<String> words = new HashSet<String>();
        if (this.file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(this.file));){
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    words.add(trimmed);
                }
            }
            catch (IOException e) {
                this.plugin.getLogger().warning("Failed to load bad.txt: " + e.getMessage());
            }
        }
        this.bannedWords = words;
        this.plugin.getLogger().info("Loaded " + this.bannedWords.size() + " banned team-name words from bad.txt");
    }

    public boolean isBanned(String name) {
        String lower = name.toLowerCase();
        for (String word : this.bannedWords) {
            if (!lower.contains(word)) continue;
            return true;
        }
        return false;
    }

    public int size() {
        return this.bannedWords.size();
    }
}

