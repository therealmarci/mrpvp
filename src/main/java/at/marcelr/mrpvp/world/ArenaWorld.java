package at.marcelr.mrpvp.world;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Settings;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;

/**
 * The world all arenas live in. MRpvp creates it itself (empty void world, no Multiverse needed) and sets its rules
 * on every start: no mobs, no day/night or weather, no advancement or death messages, PvP on.
 * <p>
 * Nobody is meant to be there without a reason (round, editor, debug) - see {@code StateKeeper}.
 */
public final class ArenaWorld {

    /** Height of the platform at 0/0 (debug and, later, the editor entrance). */
    private static final int PLATFORM_Y = 100;
    private static final int PLATFORM_RADIUS = 4;

    private final MRPvp plugin;
    private World world;

    public ArenaWorld(MRPvp plugin) {
        this.plugin = plugin;
    }

    /** Loads the world (creates it on the first start) and applies the rules. */
    public void load() {
        Settings.ArenaWorldSettings s = plugin.settings().arenaWorld();
        World existing = Bukkit.getWorld(s.name());
        if (existing != null) {
            world = existing;
        } else {
            world = new WorldCreator(s.name())
                    .environment(World.Environment.NORMAL)
                    .generator(new VoidGenerator())
                    .generateStructures(false)
                    .createWorld();
            if (world == null) {
                plugin.getLogger().severe("Could not load the arena world '" + s.name() + "'.");
                return;
            }
            plugin.getLogger().info("Arena world '" + s.name() + "' loaded.");
        }
        applyRules();
    }

    /** Rules of the arena world - set on every start and reload, so a changed config always applies. */
    public void applyRules() {
        if (world == null) {
            return;
        }
        Settings.ArenaWorldSettings s = plugin.settings().arenaWorld();
        world.setDifficulty(s.difficulty());
        world.setTime(s.time());
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
        world.setSpawnLocation(0, PLATFORM_Y + 1, 0);

        set(GameRules.ADVANCE_TIME, false);
        set(GameRules.ADVANCE_WEATHER, false);
        set(GameRules.PVP, true);
        set(GameRules.SPAWN_MOBS, false);
        set(GameRules.SPAWN_MONSTERS, false);
        set(GameRules.SPAWN_PATROLS, false);
        set(GameRules.SPAWN_PHANTOMS, false);
        set(GameRules.SPAWN_WANDERING_TRADERS, false);
        set(GameRules.SPAWN_WARDENS, false);
        set(GameRules.RAIDS, false);
        set(GameRules.MOB_GRIEFING, false);
        set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        set(GameRules.SHOW_DEATH_MESSAGES, false);
        set(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
        set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        set(GameRules.SPECTATORS_GENERATE_CHUNKS, false);
        // Safety nets only: players never really die in a round (MRpvp ends it before).
        set(GameRules.KEEP_INVENTORY, true);
        set(GameRules.IMMEDIATE_RESPAWN, true);
    }

    private <T> void set(GameRule<T> rule, T value) {
        world.setGameRule(rule, value);
    }

    /** The world, or null if it could not be loaded. */
    public World world() {
        return world;
    }

    public boolean is(World other) {
        return world != null && world.equals(other);
    }

    /** Spot at 0/0 with a small platform (built when missing). */
    public Location platform() {
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                Block block = world.getBlockAt(x, PLATFORM_Y, z);
                if (block.getType().isAir()) {
                    boolean edge = Math.abs(x) == PLATFORM_RADIUS || Math.abs(z) == PLATFORM_RADIUS;
                    block.setType(edge ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE, false);
                }
            }
        }
        return new Location(world, 0.5, PLATFORM_Y + 1, 0.5);
    }

    /** Where players go when their saved world is gone: the spawn of the main world. */
    public static Location fallback() {
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }
}
