package at.marcelr.mrpvp.arena;

import at.marcelr.mrpvp.MRPvp;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.BoundingBox;

import java.util.List;

/**
 * Nothing that happens in an arena may reach past its border (into the void around it, or another slot): lava and
 * water stop at the edge, explosions only break blocks inside, pistons do not push across.
 */
public final class ArenaProtection implements Listener {

    private final MRPvp plugin;

    public ArenaProtection(MRPvp plugin) {
        this.plugin = plugin;
    }

    private boolean inSlotGrid(Block block) {
        // Only the slot grid: in the editor area water must flow and TNT may blow up anything.
        return plugin.arenaWorld().is(block.getWorld()) && plugin.slots().inGrid(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!inSlotGrid(event.getBlock())) {
            return;
        }
        BoundingBox region = plugin.slots().regionAt(event.getBlock().getLocation());
        if (region == null || !contains(region, event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (inSlotGrid(event.getLocation().getBlock())) {
            keepInside(event.getLocation().getBlock(), event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        if (inSlotGrid(event.getBlock())) {
            keepInside(event.getBlock(), event.blockList());
        }
    }

    private void keepInside(Block center, List<Block> blocks) {
        BoundingBox region = plugin.slots().regionAt(center.getLocation());
        blocks.removeIf(block -> region == null || !contains(region, block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        if (inSlotGrid(event.getBlock()) && crosses(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        if (inSlotGrid(event.getBlock()) && crosses(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean crosses(Block piston, List<Block> moved, org.bukkit.block.BlockFace direction) {
        BoundingBox region = plugin.slots().regionAt(piston.getLocation());
        if (region == null) {
            return true;
        }
        for (Block block : moved) {
            if (!contains(region, block) || !contains(region, block.getRelative(direction))) {
                return true;
            }
        }
        return !contains(region, piston.getRelative(direction));
    }

    private static boolean contains(BoundingBox region, Block block) {
        return block.getX() >= region.getMinX() && block.getX() < region.getMaxX()
                && block.getY() >= region.getMinY() && block.getY() < region.getMaxY()
                && block.getZ() >= region.getMinZ() && block.getZ() < region.getMaxZ();
    }
}
