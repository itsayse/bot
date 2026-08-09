package com.perfectone.teams.util;

import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.List;

public final class ColorUtil {

    private ColorUtil() {}

    private static final List<ChatColor> VALID_COLORS = Arrays.asList(
            ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
    );

    /**
     * Parses a color name (e.g. "GOLD", "gold", "&6") into a ChatColor.
     * Returns null if invalid.
     */
    public static ChatColor parse(String input) {
        if (input == null || input.isBlank()) return null;
        String cleaned = input.trim();

        // Allow "&6" / "§6" style codes
        if (cleaned.length() == 2 && (cleaned.charAt(0) == '&' || cleaned.charAt(0) == '§')) {
            ChatColor byCode = ChatColor.getByChar(cleaned.charAt(1));
            return VALID_COLORS.contains(byCode) ? byCode : null;
        }

        try {
            ChatColor color = ChatColor.valueOf(cleaned.toUpperCase());
            return VALID_COLORS.contains(color) ? color : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String namesList() {
        StringBuilder sb = new StringBuilder();
        for (ChatColor c : VALID_COLORS) {
            sb.append(c).append(c.name()).append(ChatColor.RESET).append(", ");
        }
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}
