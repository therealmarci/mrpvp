package at.marcelr.mrpvp.api;

import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Public interface for other plugins, called by reflection (no build dependency needed):
 * <pre>
 * Plugin p = Bukkit.getPluginManager().getPlugin("MRpvp");
 * Class&lt;?&gt; api = p.getClass().getClassLoader().loadClass("at.marcelr.mrpvp.api.MRpvpApi");
 * boolean inGame = (boolean) api.getMethod("inGame", UUID.class).invoke(null, uuid);
 * </pre>
 * MRsleep leaves players in a duel out of the sleep percentage, MRtoolbar pauses the toolbar, MRchunks keeps
 * loaders out of the arena world. Keep the signatures stable; new methods raise {@link #API_VERSION}. Main thread
 * only.
 */
public final class MRpvpApi {

    public static final int API_VERSION = 1;

    private MRpvpApi() {
    }

    /**
     * Whether the online player with this id is in the arena world for MRpvp right now: in a round, in the arena
     * editor or watching. True from <b>before</b> MRpvp saves and clears the inventory until <b>after</b> it gave
     * everything back - in that time other plugins must leave the inventory alone. False while MRpvp is disabled.
     */
    public static boolean inGame(UUID player) {
        MRPvp plugin = JavaPlugin.getPlugin(MRPvp.class);
        if (!plugin.isEnabled()) {
            return false;
        }
        Player online = Bukkit.getPlayer(player);
        return online != null && plugin.stateKeeper().busy(online);
    }

    /** Whether this world (by name) is MRpvp's arena world - not a place for sleeping, chunk loaders or the like. */
    public static boolean isArenaWorld(String world) {
        MRPvp plugin = JavaPlugin.getPlugin(MRPvp.class);
        if (!plugin.isEnabled()) {
            return false;
        }
        World arena = plugin.arenaWorld().world();
        return arena != null && arena.getName().equals(world);
    }
}
