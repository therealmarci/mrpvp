package at.marcelr.mrpvp.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.BitSet;
import java.util.function.Supplier;

/**
 * Simple built-in arena (stone floor, glass walls, two pillars) in a grid of slots, built by code. Stand-in until
 * arena templates exist (phase 3); then it stays as fallback when no template is available.
 * <p>
 * Slot n lies at x = n * {@link #SPACING}, z = 0. Arenas are built once per slot and reused (nothing can be built
 * or broken in them).
 */
public final class BuiltinArenas implements ArenaProvider {

    public static final String NAME = "builtin";
    static final int SPACING = 1000;
    private static final int FLOOR_Y = 100;
    private static final int RADIUS = 16;
    private static final int WALL_HEIGHT = 6;
    /** Bump when the layout changes - slots built with an older one are built again. */
    private static final int VERSION = 1;

    private final Supplier<World> world;
    private final java.util.function.IntSupplier headroom;
    private final BitSet used = new BitSet();
    private final BitSet built = new BitSet();

    public BuiltinArenas(Supplier<World> world, java.util.function.IntSupplier headroom) {
        this.world = world;
        this.headroom = headroom;
    }

    @Override
    public ArenaInstance acquire(String template, String kit) {
        World w = world.get();
        if (w == null) {
            return null;
        }
        // Slot 0 stays free: the platform at 0/0 is there.
        int slot = used.nextClearBit(1);
        used.set(slot);
        int cx = slot * SPACING;
        if (!built.get(slot)) {
            build(w, cx);
            built.set(slot);
        }
        Location spawn1 = new Location(w, cx - RADIUS + 3.5, FLOOR_Y + 1, 0.5, -90, 0);
        Location spawn2 = new Location(w, cx + RADIUS - 2.5, FLOOR_Y + 1, 0.5, 90, 0);
        BoundingBox region = new BoundingBox(cx - RADIUS, FLOOR_Y - 1, -RADIUS, cx + RADIUS + 1,
                Math.min(FLOOR_Y + 30 + headroom.getAsInt(), w.getMaxHeight() - 2), RADIUS + 1);
        return new ArenaInstance(NAME, VERSION, -slot, spawn1, spawn2, region, FLOOR_Y - 10, false, 0);
    }

    @Override
    public void release(ArenaInstance arena) {
        // Negative slot numbers keep them apart from the template slots in stored games.
        used.clear(-arena.slot());
    }

    @Override
    public int inUse() {
        return used.cardinality();
    }

    private static void build(World w, int cx) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                set(w.getBlockAt(cx + x, FLOOR_Y, z), edge ? Material.POLISHED_BLACKSTONE_BRICKS
                        : (x + z) % 2 == 0 ? Material.SMOOTH_STONE : Material.STONE_BRICKS);
                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    set(w.getBlockAt(cx + x, FLOOR_Y + y, z), edge ? Material.TINTED_GLASS : Material.AIR);
                }
            }
        }
        // Two pillars as cover.
        for (int y = 1; y <= 3; y++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    set(w.getBlockAt(cx + dx, FLOOR_Y + y, 5 + dz), Material.STONE_BRICKS);
                    set(w.getBlockAt(cx - 1 + dx, FLOOR_Y + y, -6 + dz), Material.STONE_BRICKS);
                }
            }
        }
    }

    private static void set(Block block, Material type) {
        if (block.getType() != type) {
            block.setType(type, false);
        }
    }
}
