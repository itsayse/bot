package com.example.nighttreefeller;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skips the night as soon as the configured number/percentage of players are sleeping,
 * animating world time smoothly instead of jumping straight to morning. Because vanilla
 * Minecraft renders the sun/moon position purely from the world time value, gradually
 * incrementing that value each tick produces a smooth "time-lapse" sunrise for free.
 */
public class SleepListener implements Listener {

    private static final long DAY_TICKS = 24000L;
    private static final long NIGHT_START = 12541L;
    private static final long NIGHT_END = 23458L;

    private final NightTreeFellerPlugin plugin;
    private final Map<UUID, BukkitTask> activeTransitions = new HashMap<>();
    private final Map<UUID, Boolean> previousDaylightCycle = new HashMap<>();

    public SleepListener(NightTreeFellerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }
        World world = event.getPlayer().getWorld();

        // Run a tick later so Player#isSleeping() has updated for everyone.
        plugin.getServer().getScheduler().runTask(plugin, () -> checkWorld(world));
    }

    private void checkWorld(World world) {
        if (activeTransitions.containsKey(world.getUID())) {
            return; // already mid-transition
        }

        long time = world.getTime();
        boolean isNight = time >= NIGHT_START && time <= NIGHT_END;
        if (!isNight && !world.isThundering()) {
            return;
        }

        int sleeping = 0;
        int total = 0;
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            total++;
            if (p.isSleeping()) sleeping++;
        }
        if (total == 0 || sleeping == 0) {
            return;
        }

        boolean usePercentage = plugin.getConfig().getBoolean("sleep.use-percentage", false);
        int required = Math.max(1, plugin.getConfig().getInt("sleep.players-required", 1));

        boolean enough;
        if (usePercentage) {
            double pct = (sleeping / (double) total) * 100.0;
            enough = pct >= required;
        } else {
            enough = sleeping >= required;
        }

        if (enough) {
            startTransition(world);
        }
    }

    private void startTransition(World world) {
        double seconds = plugin.getConfig().getDouble("sleep.transition-seconds", 6);
        boolean clearStorm = plugin.getConfig().getBoolean("sleep.clear-storm", true);
        String msg = plugin.getConfig().getString("sleep.start-message", "");

        previousDaylightCycle.put(world.getUID(), world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

        if (clearStorm) {
            world.setStorm(false);
            world.setThundering(false);
        }

        if (msg != null && !msg.isEmpty()) {
            String colored = ChatColor.translateAlternateColorCodes('&', msg);
            for (Player p : world.getPlayers()) {
                p.sendMessage(colored);
            }
        }

        long startTime = world.getTime();
        long ticksToDay = (DAY_TICKS - startTime) % DAY_TICKS;
        if (ticksToDay == 0) ticksToDay = DAY_TICKS;

        long totalAnimTicks = Math.max(1L, (long) (seconds * 20));
        double stepPerTick = ticksToDay / (double) totalAnimTicks;

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            long ticksRun = 0;
            double accumulated = 0;

            @Override
            public void run() {
                ticksRun++;
                accumulated += stepPerTick;
                boolean done = ticksRun >= totalAnimTicks;

                long newTime = done ? DAY_TICKS : (long) (startTime + accumulated);
                world.setTime(newTime % DAY_TICKS);

                if (done) {
                    finishTransition(world);
                }
            }
        }, 1L, 1L);

        activeTransitions.put(world.getUID(), task);
    }

    private void finishTransition(World world) {
        BukkitTask task = activeTransitions.remove(world.getUID());
        if (task != null) {
            task.cancel();
        }

        Boolean prev = previousDaylightCycle.remove(world.getUID());
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, prev == null || prev);

        for (Player p : world.getPlayers()) {
            if (p.isSleeping()) {
                p.wakeup(false);
            }
        }
    }

    public void cancelAllTransitions() {
        for (BukkitTask task : activeTransitions.values()) {
            task.cancel();
        }
        activeTransitions.clear();
    }
}
