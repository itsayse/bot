package com.example.nighttreefeller;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * When a player chops the bottom log of a tree, this fells the whole tree at once:
 * every log block connected to the one broken (same wood type, connected through
 * other logs) is broken and dropped together. Because the search only ever travels
 * from log to log (never through leaves or other block types), it can never "jump"
 * into a neighbouring tree - only the single tree that was chopped falls.
 *
 * If tree-feller.fell-delay-ticks is greater than 0, the connected logs are broken
 * one at a time (top-down) with that many ticks between each, so the tree visibly
 * collapses instead of vanishing all at once. A value of 0 keeps the old instant
 * behavior.
 */
public class TreeFellerListener implements Listener {

    private final NightTreeFellerPlugin plugin;

    // 6 face neighbours + diagonal neighbours, so leaning/branching trunks still connect.
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}
    };

    public TreeFellerListener(NightTreeFellerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("tree-feller.enabled", true)) return;
        if (event.isCancelled()) return;

        Block origin = event.getBlock();
        if (!isLog(origin.getType())) return;

        if (plugin.getConfig().getBoolean("tree-feller.require-axe", true)) {
            Player player = event.getPlayer();
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (!tool.getType().name().endsWith("_AXE")) return;
        }

        // Only fell if this is the base of the trunk - i.e. the block below is
        // NOT the same kind of log (it's dirt/grass/podzol/air/etc). Chopping a
        // log mid-trunk just breaks that one block normally.
        Block below = origin.getRelative(0, -1, 0);
        if (below.getType() == origin.getType()) {
            return;
        }

        Material logType = origin.getType();
        int maxLogs = plugin.getConfig().getInt("tree-feller.max-logs", 200);
        boolean dropItems = plugin.getConfig().getBoolean("tree-feller.drop-items", true);
        boolean playSound = plugin.getConfig().getBoolean("tree-feller.play-sound", true);
        int fellDelayTicks = Math.max(0, plugin.getConfig().getInt("tree-feller.fell-delay-ticks", 0));
        World world = origin.getWorld();

        Set<Location> visited = new HashSet<>();
        visited.add(origin.getLocation());

        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(origin);

        List<Block> toBreak = new ArrayList<>();

        while (!queue.isEmpty() && toBreak.size() < maxLogs) {
            Block current = queue.poll();
            for (int[] off : NEIGHBORS) {
                Block neighbor = current.getRelative(off[0], off[1], off[2]);
                Location loc = neighbor.getLocation();
                if (visited.contains(loc)) continue;
                visited.add(loc);

                if (neighbor.getType() == logType) {
                    toBreak.add(neighbor);
                    queue.add(neighbor);
                    if (toBreak.size() >= maxLogs) break;
                }
            }
        }

        if (toBreak.isEmpty()) return; // just a lone log, nothing else to fell

        if (playSound) {
            world.playSound(origin.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.5f, 0.7f);
        }

        // Collapse top-down so it visually reads as the tree toppling rather than
        // breaking outward from wherever the flood-fill happened to reach first.
        toBreak.sort(Comparator.comparingInt(Block::getY).reversed());

        if (fellDelayTicks <= 0) {
            // Original instant behavior.
            for (Block b : toBreak) {
                breakLog(world, b, logType, dropItems);
            }
            return;
        }

        // The origin block is left for BlockBreakEvent/vanilla to break+drop as normal.
        // Everything else breaks in staggered steps, one log at a time, every
        // fellDelayTicks ticks, until the whole tree is down.
        scheduleStagger(world, toBreak, logType, dropItems, fellDelayTicks);
    }

    private org.bukkit.scheduler.BukkitTask scheduleStagger(World world, List<Block> toBreak, Material logType,
                                                              boolean dropItems, int fellDelayTicks) {
        final int[] index = {0};
        final org.bukkit.scheduler.BukkitTask[] taskHolder = new org.bukkit.scheduler.BukkitTask[1];

        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (index[0] >= toBreak.size()) {
                taskHolder[0].cancel();
                return;
            }
            Block b = toBreak.get(index[0]);
            breakLog(world, b, logType, dropItems);
            index[0]++;

            if (index[0] >= toBreak.size()) {
                taskHolder[0].cancel();
            }
        }, fellDelayTicks, fellDelayTicks);

        return taskHolder[0];
    }

    private void breakLog(World world, Block b, Material logType, boolean dropItems) {
        if (dropItems) {
            world.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(logType));
        }
        b.setType(Material.AIR, false);
    }

    private boolean isLog(Material type) {
        String name = type.name();
        // Covers oak/spruce/birch/etc _LOG, stripped logs, and nether _STEM/stripped stems.
        return name.endsWith("_LOG") || name.endsWith("_STEM");
    }
}
