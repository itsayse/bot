package com.example.freeze;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FreezeCommand implements CommandExecutor {

    private final FreezePlugin plugin;
    private final FreezeManager freezeManager;

    public FreezeCommand(FreezePlugin plugin, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.freezeManager = freezeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("staff.freeze")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> [reason...]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player isn't online.");
            return true;
        }

        if (label.equalsIgnoreCase("freeze")) {
            if (freezeManager.isFrozen(target.getUniqueId())) {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " is already frozen.");
                return true;
            }

            String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;

            freezeManager.freeze(target.getUniqueId(), target.getLocation(), reason);
            applyBlackScreen(target);

            target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You have been frozen by staff.");
            target.sendMessage(ChatColor.RED + "Reason: " + ChatColor.GRAY + freezeManager.getReason(target.getUniqueId()));
            target.sendMessage(ChatColor.RED + "Do not log out. Join our Discord/voice for further instructions.");

            sender.sendMessage(ChatColor.GREEN + "Froze " + target.getName() + ".");
            return true;
        }

        if (label.equalsIgnoreCase("unfreeze")) {
            if (!freezeManager.isFrozen(target.getUniqueId())) {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " isn't frozen.");
                return true;
            }

            freezeManager.unfreeze(target.getUniqueId());
            removeBlackScreen(target);

            target.sendMessage(ChatColor.GREEN + "You have been unfrozen. You may act again.");
            sender.sendMessage(ChatColor.GREEN + "Unfroze " + target.getName() + ".");
            return true;
        }

        return false;
    }

    private void applyBlackScreen(Player player) {
        // Blindness + Darkness together produce a near-total black screen.
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 1, false, false, false));
    }

    private void removeBlackScreen(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
    }
}
