/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 */
package com.perfectone.teams.util;

import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;

public final class ColorUtil {
    private static final List<ChatColor> VALID_COLORS = Arrays.asList(ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE);

    private ColorUtil() {
    }

    public static ChatColor parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String cleaned = input.trim();
        if (cleaned.length() == 2 && (cleaned.charAt(0) == '&' || cleaned.charAt(0) == '\u00a7')) {
            ChatColor byCode = ChatColor.getByChar((char)cleaned.charAt(1));
            return VALID_COLORS.contains(byCode) ? byCode : null;
        }
        try {
            ChatColor color = ChatColor.valueOf((String)cleaned.toUpperCase());
            return VALID_COLORS.contains(color) ? color : null;
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String namesList() {
        StringBuilder sb = new StringBuilder();
        for (ChatColor c : VALID_COLORS) {
            sb.append(c).append(c.name()).append(ChatColor.RESET).append(", ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}

