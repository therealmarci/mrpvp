package at.marcelr.mrpvp.arena;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

/**
 * A ready arena for one round - all a round needs to know about it, whatever built it.
 *
 * @param name       arena template id, stored with the game
 * @param slot       place in the slot grid
 * @param spawn1     spawn of the inviting player
 * @param spawn2     spawn of the invited player
 * @param region     the arena; leaving it sideways puts the player back, falling below {@code minY} loses
 * @param minY       below this height a player has lost
 * @param allowBuild players may place and break blocks (inside the region, up to {@code buildMaxY})
 */
public record ArenaInstance(String name, int version, int slot, Location spawn1, Location spawn2, BoundingBox region,
                            double minY, boolean allowBuild, double buildMaxY) {
}
