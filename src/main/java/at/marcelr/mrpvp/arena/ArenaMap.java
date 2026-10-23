package at.marcelr.mrpvp.arena;

import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top view of an arena for the mini map in the web interface: per column the map colour of the highest block
 * (like a Minecraft map), a little darker the lower it is. Computed once per template version and kept.
 * Main thread only (reads blocks).
 */
public final class ArenaMap {

    private final Map<String, Map<String, Object>> cache = new HashMap<>();

    /**
     * The map of an arena: "sizeX", "sizeZ" and "colors" (row by row along z, each "#rrggbb" or null for nothing).
     *
     * @param key cache key, e.g. "template:version"
     */
    public Map<String, Object> get(String key, World world, int x0, int y0, int z0, int sizeX, int sizeY, int sizeZ) {
        Map<String, Object> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> colors = new ArrayList<>(sizeX * sizeZ);
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                colors.add(column(world, x0 + x, y0, z0 + z, sizeY));
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sizeX", sizeX);
        map.put("sizeZ", sizeZ);
        map.put("colors", colors);
        cache.put(key, map);
        return map;
    }

    private static String column(World world, int x, int y0, int z, int sizeY) {
        for (int y = sizeY - 1; y >= 0; y--) {
            Block block = world.getBlockAt(x, y0 + y, z);
            if (block.getType().isAir()) {
                continue;
            }
            Color color = block.getBlockData().getMapColor();
            if (color.getRed() == 0 && color.getGreen() == 0 && color.getBlue() == 0) {
                // glass and the like have no map colour - look further down
                continue;
            }
            // Higher blocks a little brighter, so walls and pillars stand out from the floor.
            double shade = 0.75 + 0.25 * (sizeY <= 1 ? 1 : (double) y / (sizeY - 1));
            return String.format("#%02x%02x%02x", (int) (color.getRed() * shade), (int) (color.getGreen() * shade),
                    (int) (color.getBlue() * shade));
        }
        return null;
    }

    /** Forget everything (a template was saved or deleted). */
    public void clear() {
        cache.clear();
    }
}
