package at.marcelr.mrpvp.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Players that are no real players: MRchunks' dummy players (they sit on chunk loaders) and NPCs of other plugins
 * (Citizens and others mark them with the metadata "NPC"). MRpvp ignores them: they cannot be invited to a duel and do
 * not show up in player lists.
 */
public final class Npcs {

    /** MRchunks marks its dummies with this key in the player's data container. */
    private static final NamespacedKey MRCHUNKS_DUMMY = new NamespacedKey("mrchunks", "dummy");

    private Npcs() {
    }

    public static boolean is(Player player) {
        return player.hasMetadata("NPC") || player.getPersistentDataContainer().has(MRCHUNKS_DUMMY);
    }
}
