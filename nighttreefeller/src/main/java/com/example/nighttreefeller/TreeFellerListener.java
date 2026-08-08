package com.example.nighttreefeller;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * When a player chops the bottom log of a tree, this fells the whole tree at once:
 * every log block connected to the one broken (same wood type, connected through
 * other logs) is broken and dropped together.
 *
 * Independently of that, EVERY log break (base or not) triggers a check for nearby
 * leaves that have lost all log support as a result - matching vanilla's own
 * "distance to nearest log" decay rule, but evaluated immediately instead of
 * waiting on random block ticks. This is the part that actually makes leaves
 * decay fast and reliably, even if a player chops a trunk out of order (top
 * first, middle first, etc.) instead of always hitting the base. Leaves still
 * within range of any surviving log - whether from this tree or a neighbouring
 * one - are never touched.
 */
public class TreeFellerListener implements Listener {

    private final NightTreeFellerPlugin plugin;

    // 6 face neighbours + edge/corner diagonals, so leaning/branching trunks and
    // rounded leaf canopies still connect properly.
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1},
            {1, -1, 1}, {1, -1, -1}, {-1, -1, 1}, {-1, -1, -1}
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

        Material logType = origin.getType();
        World world = origin.getWorld();
        Location originLoc = origin.getLocation();

        // Every location that ends up as a removed log this event, whether just
        // the one block or a whole felled trunk - used to seed the leaf check below.
        List<Location> removedLogLocations = new ArrayList<>();
        removedLogLocations.add(originLoc);

        // Only fell if this is the base of the trunk - i.e. the block below is
        // NOT the same kind of log (it's dirt/grass/podzol/air/etc). Chopping a
        // log mid-trunk just breaks that one block normally, same as vanilla.
        Block below = origin.getRelative(0, -1, 0);
        boolean isBase = below.getType() != origin.getType();

        if (isBase) {
            int maxLogs = plugin.getConfig().getInt("tree-feller.max-logs", 200);
            boolean dropItems = plugin.getConfig().getBoolean("tree-feller.drop-items", true);
            boolean playSound = plugin.getConfig().getBoolean("tree-feller.play-sound", true);

            Set<Location> visited = new HashSet<>();
            visited.add(originLoc);
            ArrayDeque<Block> queue = new ArrayDeque<>();
            queue.add(origin);
            List<Block> logsToBreak = new ArrayList<>();

            while (!queue.isEmpty() && logsToBreak.size() < maxLogs) {
                Block current = queue.poll();
                for (int[] off : NEIGHBORS) {
                    Block neighbor = current.getRelative(off[0], off[1], off[2]);
                    Location loc = neighbor.getLocation();
                    if (visited.contains(loc)) continue;
                    visited.add(loc);

                    if (neighbor.getType() == logType) {
                        logsToBreak.add(neighbor);
                        queue.add(neighbor);
                        if (logsToBreak.size() >= maxLogs) break;
                    }
                }
            }

            if (!logsToBreak.isEmpty()) {
                if (playSound) {
                    world.playSound(originLoc, Sound.BLOCK_WOOD_BREAK, 1.5f, 0.7f);
                }
                for (Block b : logsToBreak) {
                    if (dropItems) {
                        world.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(logType));
                    }
                    removedLogLocations.add(b.getLocation());
                    b.setType(Material.AIR, false);
                }
            }
        }

        if (plugin.getConfig().getBoolean("tree-feller.leaf-decay.enabled", true)) {
            Material leafType = matchingLeaves(logType);
            if (leafType != null) {
                // Run next tick: the origin block itself is still standing right
                // now (vanilla removes it right after this event returns), so we
                // wait a tick to check the world's *actual* post-break state.
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        decayOrphanedLeaves(world, removedLogLocations, leafType));
            }
        }
    }

    /**
     * Flood-fills outward from the removed log location(s) through connected,
     * non-persistent (naturally grown) leaves of the matching type, and fast-decays
     * only the ones that no longer have ANY log within range - i.e. the ones that
     * actually got orphaned by this break, exactly like vanilla decay would
     * eventually do on its own, just immediate instead of relying on random ticks.
     * Leaves still supported by a surviving log (from this tree or another one
     * nearby) are left completely alone.
     */
    private void decayOrphanedLeaves(World world, List<Location> removedLogLocations, Material leafType) {
        int maxLeaves = plugin.getConfig().getInt("tree-feller.leaf-decay.max-leaves", 300);
        double decaySeconds = plugin.getConfig().getDouble("tree-feller.leaf-decay.decay-seconds", 1.5);
        double saplingChance = plugin.getConfig().getDouble("tree-feller.leaf-decay.sapling-drop-chance", 0.05);
        int supportRadius = plugin.getConfig().getInt("tree-feller.leaf-decay.support-radius", 6);
        long maxDelayTicks = Math.max(1L, (long) (decaySeconds * 20));

        Set<Location> visited = new HashSet<>(removedLogLocations);
        ArrayDeque<Location> queue = new ArrayDeque<>(removedLogLocations);

        List<Block> candidateLeaves = new ArrayList<>();

        while (!queue.isEmpty() && candidateLeaves.size() < maxLeaves) {
            Block current = queue.poll().getBlock();
            for (int[] off : NEIGHBORS) {
                Block neighbor = current.getRelative(off[0], off[1], off[2]);
                Location loc = neighbor.getLocation();
                if (visited.contains(loc)) continue;
                visited.add(loc);

                if (neighbor.getType() == leafType && !isPersistentLeaf(neighbor)) {
                    candidateLeaves.add(neighbor);
                    queue.add(loc);
                    if (candidateLeaves.size() >= maxLeaves) break;
                }
            }
        }

        if (candidateLeaves.isEmpty()) return;

        Material saplingType = matchingSapling(leafType);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (Block leaf : candidateLeaves) {
            if (hasNearbyLog(leaf, supportRadius)) continue; // still supported, leave it alone

            long delay = maxDelayTicks <= 1 ? 1 : random.nextLong(1, maxDelayTicks + 1);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (leaf.getType() != leafType) return; // already changed since we scheduled this
                if (hasNearbyLog(leaf, supportRadius)) return; // a log was placed back nearby - leave it
                if (saplingType != null && random.nextDouble() < saplingChance) {
                    world.dropItemNaturally(leaf.getLocation().add(0.5, 0.5, 0.5), new ItemStack(saplingType));
                }
                leaf.setType(Material.AIR, false);
            }, delay);
        }
    }

    /** Scans a cube of the given radius around the leaf for any log/stem block. */
    private boolean hasNearbyLog(Block leaf, int radius) {
        World world = leaf.getWorld();
        int bx = leaf.getX(), by = leaf.getY(), bz = leaf.getZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (isLog(world.getBlockAt(bx + dx, by + dy, bz + dz).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isPersistentLeaf(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Leaves) {
            return ((Leaves) data).isPersistent();
        }
        return false;
    }

    private Material matchingLeaves(Material logType) {
        String base = logType.name().replace("STRIPPED_", "").replace("_LOG", "");
        return Material.matchMaterial(base + "_LEAVES");
    }

    private Material matchingSapling(Material leafType) {
        String base = leafType.name().replace("_LEAVES", "");
        // A couple of species don't have a matching sapling item (e.g. azalea leaves).
        return Material.matchMaterial(base + "_SAPLING");
    }

    private boolean isLog(Material type) {
        String name = type.name();
        // Covers oak/spruce/birch/etc _LOG, stripped logs, and nether _STEM/stripped stems.
        return name.endsWith("_LOG") || name.endsWith("_STEM");
    }
}
