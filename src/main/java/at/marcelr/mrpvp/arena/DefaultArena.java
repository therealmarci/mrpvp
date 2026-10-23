package at.marcelr.mrpvp.arena;

import at.marcelr.mrpvp.ConfigUpdater;
import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The arenas that come with MRpvp: "standard" (for every kit), "sumo" (a platform over the void), "bau" (building
 * allowed, for Build-UHC and crystal) and "xxl" (128 blocks of floating islands for elytra). They are built by code
 * in an editor plot on the first start and then saved like any template - so they can be changed in the editor like
 * every other arena. Once installed an arena is not brought back by itself after being deleted (data.yml remembers
 * that); "restore defaults" in the admin menu does.
 */
public final class DefaultArena {

    public static final String ID = "standard";
    public static final String SUMO = "sumo";
    public static final String BUILD = "bau";
    public static final String XXL = "xxl";

    private static final int SIZE = 45;
    private static final int HEIGHT = 12;
    private static final int WALL = 6;
    private static final int MID = SIZE / 2;

    private static final int SUMO_SIZE = 31;
    private static final int SUMO_RADIUS = 7;

    private static final int BUILD_SIZE = 41;
    private static final int BUILD_HEIGHT = 32;
    /** bedrock, dirt, dirt, grass - players stand on y = 4 */
    private static final int BUILD_FLOOR = 4;

    /** Floating islands for elytra fights; the arena's headroom (arenas.headroom) is above it. */
    private static final int XXL_SIZE = 128;
    private static final int XXL_HEIGHT = 64;

    /** One shipped arena: how it looks (built at the lowest corner) and its settings. */
    private record Spec(String id, String name, Material icon, int sizeX, int sizeY, int sizeZ,
                        ArenaTemplate.Spawn spawn1, ArenaTemplate.Spawn spawn2, boolean allowBuild, int buildHeight,
                        List<String> kits, Consumer<Location> builder, boolean sparse) {

        String flag() {
            return "defaults.arena-" + id;
        }
    }

    private static final List<Spec> SPECS = List.of(
            new Spec(ID, "<gray>Standard", Material.STONE_BRICKS, SIZE, HEIGHT, SIZE,
                    new ArenaTemplate.Spawn(5.5, 1, MID + 0.5, -90, 0),
                    new ArenaTemplate.Spawn(SIZE - 4.5, 1, MID + 0.5, 90, 0),
                    false, HEIGHT, List.of(), DefaultArena::buildStandard, false),
            new Spec(SUMO, "<white>Sumo", Material.SLIME_BALL, SUMO_SIZE, 4, SUMO_SIZE,
                    new ArenaTemplate.Spawn(SUMO_SIZE / 2 - 3.5, 1, SUMO_SIZE / 2 + 0.5, -90, 0),
                    new ArenaTemplate.Spawn(SUMO_SIZE / 2 + 4.5, 1, SUMO_SIZE / 2 + 0.5, 90, 0),
                    false, 0, List.of("sumo"), DefaultArena::buildSumo, false),
            new Spec(BUILD, "<green>Bau-Arena", Material.GRASS_BLOCK, BUILD_SIZE, BUILD_HEIGHT, BUILD_SIZE,
                    new ArenaTemplate.Spawn(5.5, BUILD_FLOOR, BUILD_SIZE / 2 + 0.5, -90, 0),
                    new ArenaTemplate.Spawn(BUILD_SIZE - 4.5, BUILD_FLOOR, BUILD_SIZE / 2 + 0.5, 90, 0),
                    true, BUILD_HEIGHT, List.of(), DefaultArena::buildBau, false),
            new Spec(XXL, "<light_purple>XXL-Himmel", Material.ELYTRA, XXL_SIZE, XXL_HEIGHT, XXL_SIZE,
                    new ArenaTemplate.Spawn(12.5, 21, XXL_SIZE / 2 + 0.5, -90, 0),
                    new ArenaTemplate.Spawn(XXL_SIZE - 11.5, 21, XXL_SIZE / 2 + 0.5, 90, 0),
                    false, 0, List.of(), DefaultArena::buildXxl, true));

    private DefaultArena() {
    }

    /** Installs every shipped arena on the first start (not again after it was deleted). */
    public static void installOnce(MRPvp plugin) {
        YamlConfiguration data = data(plugin);
        boolean changed = false;
        for (Spec spec : SPECS) {
            if (data.getBoolean(spec.flag())) {
                continue;
            }
            if (plugin.templates().get(spec.id()) != null || install(plugin, spec)) {
                data.set(spec.flag(), true);
                changed = true;
            }
        }
        if (changed) {
            saveData(plugin, data);
        }
    }

    /** Builds the shipped arenas that are missing again; returns their ids. */
    public static List<String> restore(MRPvp plugin) {
        List<String> restored = new ArrayList<>();
        for (Spec spec : SPECS) {
            if (plugin.templates().get(spec.id()) == null && install(plugin, spec)) {
                restored.add(spec.id());
            }
        }
        return restored;
    }

    /** Builds and saves one arena in a free editor plot. */
    private static boolean install(MRPvp plugin, Spec spec) {
        World w = plugin.arenaWorld().world();
        if (w == null) {
            return false;
        }
        int plot = plugin.templates().freePlot();
        int spacing = plugin.settings().arenas().spacing();
        Location origin = new Location(w, plot * spacing, 100, -20000);
        BlockVector size = new BlockVector(spec.sizeX(), spec.sizeY(), spec.sizeZ());
        if (spec.sparse()) {
            // Empty space as structure void: it is not saved, so pasting the arena only sets its real blocks.
            fill(origin, size, Material.AIR, Material.STRUCTURE_VOID);
        }
        spec.builder().accept(origin);
        ArenaTemplate t = new ArenaTemplate(spec.id(), spec.name(), spec.icon().name(), 1, spec.sizeX(), spec.sizeY(),
                spec.sizeZ(), spec.spawn1(), spec.spawn2(), spec.allowBuild(), spec.buildHeight(), spec.kits(),
                spec.id().equals(XXL), true,
                plugin.settings().arenas().pool(), plot, origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        try {
            plugin.templates().save(t, TemplateManager.capture(origin, size));
            if (spec.sparse()) {
                fill(origin, size, Material.STRUCTURE_VOID, Material.AIR);
            }
            plugin.getLogger().info("Installed the default arena '" + spec.id() + "'.");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the default arena '" + spec.id() + "'", e);
            return false;
        }
    }

    /** Every block of type {@code from} in the box becomes {@code to}. */
    private static void fill(Location o, BlockVector size, Material from, Material to) {
        World w = o.getWorld();
        for (int x = 0; x < size.getBlockX(); x++) {
            for (int y = 0; y < size.getBlockY(); y++) {
                for (int z = 0; z < size.getBlockZ(); z++) {
                    Block block = w.getBlockAt(o.getBlockX() + x, o.getBlockY() + y, o.getBlockZ() + z);
                    if (block.getType() == from) {
                        block.setType(to, false);
                    }
                }
            }
        }
    }

    /**
     * XXL sky: two spawn islands at the edges, a large island in the middle, smaller ones at different heights
     * around it and a few rings to fly through - everything over the void (falling out = lost).
     */
    private static void buildXxl(Location o) {
        int c = XXL_SIZE / 2;
        island(o, 12, 20, c, 6, Material.RED_CONCRETE);
        island(o, XXL_SIZE - 12, 20, c, 6, Material.BLUE_CONCRETE);
        island(o, c, 14, c, 14, null);
        int[][] small = {{c, 34, 20, 6}, {c, 30, XXL_SIZE - 20, 6}, {32, 44, 30, 5}, {XXL_SIZE - 32, 40, XXL_SIZE - 30, 5},
                {30, 26, XXL_SIZE - 28, 4}, {XXL_SIZE - 30, 52, 28, 4}, {c - 22, 50, c + 4, 3}, {c + 22, 24, c - 6, 3}};
        for (int[] i : small) {
            island(o, i[0], i[1], i[2], i[3], null);
        }
        // Rings to fly through.
        ring(o, c, 36, c, 7, Material.PURPLE_CONCRETE, true);
        ring(o, 34, 30, c, 5, Material.CYAN_CONCRETE, false);
        ring(o, XXL_SIZE - 34, 30, c, 5, Material.CYAN_CONCRETE, false);
        // A tree on the middle island (cover).
        for (int y = 1; y <= 5; y++) {
            set(o.getWorld(), o.getBlockX() + c + 5, o.getBlockY() + 14 + y, o.getBlockZ() + c + 3, Material.OAK_LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 4; dy <= 6; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + (dy - 4) <= 4 && !(dx == 0 && dz == 0 && dy < 6)) {
                        set(o.getWorld(), o.getBlockX() + c + 5 + dx, o.getBlockY() + 14 + dy,
                                o.getBlockZ() + c + 3 + dz, Material.OAK_LEAVES);
                    }
                }
            }
        }
    }

    /**
     * A floating island: grass on top (at {@code top}), narrowing down like a cone of dirt and stone.
     *
     * @param mark concrete in the middle (spawn), or null
     */
    private static void island(Location o, int cx, int top, int cz, int radius, Material mark) {
        World w = o.getWorld();
        int depth = Math.max(3, radius);
        for (int d = 0; d <= depth; d++) {
            double r = radius * (1 - (double) d / (depth + 1)) + 0.5;
            Material type = d == 0 ? Material.GRASS_BLOCK : d < 3 ? Material.DIRT : Material.STONE;
            for (int x = (int) -r; x <= r; x++) {
                for (int z = (int) -r; z <= r; z++) {
                    // A bit uneven at the edge so it does not look like a cylinder.
                    double edge = r - ((x * 7 + z * 13 + d * 5) & 1) * 0.6;
                    if (x * x + z * z <= edge * edge) {
                        set(w, o.getBlockX() + cx + x, o.getBlockY() + top - d, o.getBlockZ() + cz + z, type);
                    }
                }
            }
        }
        if (mark != null) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    set(w, o.getBlockX() + cx + x, o.getBlockY() + top, o.getBlockZ() + cz + z, mark);
                }
            }
        }
    }

    /** A ring standing upright (facing along x when {@code alongX}, else along z). */
    private static void ring(Location o, int cx, int cy, int cz, int radius, Material type, boolean alongX) {
        World w = o.getWorld();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > radius - 0.5 && d <= radius + 0.5) {
                    int x = alongX ? cx : cx + a;
                    int z = alongX ? cz + a : cz;
                    set(w, o.getBlockX() + x, o.getBlockY() + cy + b, o.getBlockZ() + z, type);
                }
            }
        }
    }

    /** A round platform in the middle of the void - push the other one down. Air around it to fall through. */
    private static void buildSumo(Location o) {
        World w = o.getWorld();
        int ox = o.getBlockX();
        int oy = o.getBlockY();
        int oz = o.getBlockZ();
        int c = SUMO_SIZE / 2;
        for (int x = 0; x < SUMO_SIZE; x++) {
            for (int z = 0; z < SUMO_SIZE; z++) {
                double d = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                Material type = Material.AIR;
                if (d <= SUMO_RADIUS + 0.5) {
                    type = d > SUMO_RADIUS - 0.5 ? Material.RED_CONCRETE
                            : d <= 1.5 ? Material.QUARTZ_PILLAR
                            : (x + z) % 2 == 0 ? Material.WHITE_CONCRETE : Material.SMOOTH_QUARTZ;
                }
                set(w, ox + x, oy, oz + z, type);
                for (int y = 1; y < 4; y++) {
                    set(w, ox + x, oy + y, oz + z, Material.AIR);
                }
            }
        }
        // Spawn marks.
        set(w, ox + c - 4, oy, oz + c, Material.RED_GLAZED_TERRACOTTA);
        set(w, ox + c + 4, oy, oz + c, Material.BLUE_GLAZED_TERRACOTTA);
    }

    /** Flat grass to build and dig on (bedrock below), a low fence, a few trees of logs as cover. */
    private static void buildBau(Location o) {
        World w = o.getWorld();
        int ox = o.getBlockX();
        int oy = o.getBlockY();
        int oz = o.getBlockZ();
        int c = BUILD_SIZE / 2;
        for (int x = 0; x < BUILD_SIZE; x++) {
            for (int z = 0; z < BUILD_SIZE; z++) {
                boolean edge = x == 0 || z == 0 || x == BUILD_SIZE - 1 || z == BUILD_SIZE - 1;
                set(w, ox + x, oy, oz + z, Material.BEDROCK);
                set(w, ox + x, oy + 1, oz + z, Material.STONE);
                set(w, ox + x, oy + 2, oz + z, Material.DIRT);
                set(w, ox + x, oy + 3, oz + z, edge ? Material.STONE_BRICKS : Material.GRASS_BLOCK);
                for (int y = BUILD_FLOOR; y < BUILD_HEIGHT; y++) {
                    Material type = Material.AIR;
                    if (edge && y < BUILD_FLOOR + 2) {
                        type = Material.STONE_BRICK_WALL;
                    }
                    set(w, ox + x, oy + y, oz + z, type);
                }
            }
        }
        for (int[] p : new int[][] {{12, 10}, {28, 30}, {12, 30}, {28, 10}}) {
            for (int y = 0; y < 4; y++) {
                set(w, ox + p[0], oy + BUILD_FLOOR + y, oz + p[1], Material.OAK_LOG);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(w, ox + p[0] + dx, oy + BUILD_FLOOR + 4, oz + p[1] + dz, Material.OAK_LEAVES);
                }
            }
        }
        // A small pond in the middle (water buckets, clutches).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(w, ox + c + dx, oy + 3, oz + c + dz, Material.WATER);
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            set(w, ox + 5, oy + 3, oz + c + dz, Material.RED_CONCRETE);
            set(w, ox + BUILD_SIZE - 5, oy + 3, oz + c + dz, Material.BLUE_CONCRETE);
        }
    }

    /** Stone floor with a pattern, blackstone walls, cover pillars, a raised middle and two coloured spawns. */
    private static void buildStandard(Location o) {
        World w = o.getWorld();
        int ox = o.getBlockX();
        int oy = o.getBlockY();
        int oz = o.getBlockZ();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
                boolean corner = (x == 0 || x == SIZE - 1) && (z == 0 || z == SIZE - 1);
                set(w, ox + x, oy, oz + z, floor(x, z));
                for (int y = 1; y < HEIGHT; y++) {
                    Material type = Material.AIR;
                    if (edge && y <= WALL) {
                        type = corner ? Material.CHISELED_POLISHED_BLACKSTONE
                                : y == 3 && (x + z) % 6 == 0 ? Material.TINTED_GLASS
                                : Material.POLISHED_BLACKSTONE_BRICKS;
                    } else if (edge && y == WALL + 1) {
                        type = Material.POLISHED_BLACKSTONE_BRICK_SLAB;
                    }
                    set(w, ox + x, oy + y, oz + z, type);
                }
            }
        }
        // Four pillars as cover.
        for (int[] p : new int[][] {{13, 13}, {13, 30}, {30, 13}, {30, 30}}) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    for (int y = 1; y <= 4; y++) {
                        set(w, ox + p[0] + dx, oy + y, oz + p[1] + dz, y == 4 ? Material.CHISELED_STONE_BRICKS
                                : Material.STONE_BRICKS);
                    }
                }
            }
        }
        // Low walls north and south of the middle (cover against arrows, can be walked around).
        for (int x = MID - 3; x <= MID + 3; x++) {
            for (int y = 1; y <= 2; y++) {
                set(w, ox + x, oy + y, oz + 8, Material.MOSSY_STONE_BRICKS);
                set(w, ox + x, oy + y, oz + SIZE - 9, Material.MOSSY_STONE_BRICKS);
            }
        }
        // Raised middle with slab steps around it.
        for (int x = MID - 2; x <= MID + 2; x++) {
            for (int z = MID - 2; z <= MID + 2; z++) {
                boolean ring = Math.abs(x - MID) == 2 || Math.abs(z - MID) == 2;
                set(w, ox + x, oy + 1, oz + z, ring ? Material.STONE_BRICK_SLAB : Material.POLISHED_ANDESITE);
            }
        }
        set(w, ox + MID, oy + 1, oz + MID, Material.CHISELED_STONE_BRICKS);
        // Spawns: red west, blue east.
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                set(w, ox + 5 + dx, oy, oz + MID + dz, Material.RED_CONCRETE);
                set(w, ox + SIZE - 5 + dx, oy, oz + MID + dz, Material.BLUE_CONCRETE);
            }
        }
    }

    private static Material floor(int x, int z) {
        if (x % 11 == 0 && z % 11 == 0 && x > 0 && z > 0 && x < SIZE - 1 && z < SIZE - 1) {
            return Material.SEA_LANTERN;
        }
        if (x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        int ring = Math.max(Math.abs(x - MID), Math.abs(z - MID));
        if (ring % 7 == 0) {
            return Material.POLISHED_ANDESITE;
        }
        return (x + z) % 2 == 0 ? Material.STONE_BRICKS : Material.CRACKED_STONE_BRICKS;
    }

    private static void set(World w, int x, int y, int z, Material type) {
        Block block = w.getBlockAt(x, y, z);
        if (block.getType() != type) {
            block.setType(type, false);
        }
    }

    // ------------------------------------------------------------------ data.yml

    private static File file(MRPvp plugin) {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    public static YamlConfiguration data(MRPvp plugin) {
        YamlConfiguration y = new YamlConfiguration();
        File file = file(plugin);
        if (file.exists()) {
            try {
                y.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().warning("data.yml is invalid: " + e.getMessage());
            }
        }
        return y;
    }

    public static void saveData(MRPvp plugin, YamlConfiguration y) {
        y.options().setHeader(List.of("MRpvp - internal data, do not edit."));
        try {
            Files.writeString(file(plugin).toPath(), y.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write data.yml: " + e.getMessage());
        }
    }
}
