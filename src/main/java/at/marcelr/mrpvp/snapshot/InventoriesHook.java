package at.marcelr.mrpvp.snapshot;

import at.marcelr.mrpvp.MRPvp;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.mvplugins.multiverse.inventories.event.GameModeChangeShareHandlingEvent;
import org.mvplugins.multiverse.inventories.event.WorldChangeShareHandlingEvent;

/**
 * Multiverse-Inventories (optional, only registered if it is installed) stores the inventory per world group. MRpvp
 * empties the inventory before the teleport and gives it back before the way home - left alone, Multiverse-Inventories
 * would save the empty inventory for the home world and load it on the way back. The arena world is therefore outside
 * of it: {@link StateKeeper} alone looks after the inventory there.
 */
public final class InventoriesHook implements Listener {

    private final MRPvp plugin;

    public InventoriesHook(MRPvp plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(WorldChangeShareHandlingEvent event) {
        World arena = plugin.arenaWorld().world();
        if (arena != null && (arena.getName().equals(event.getFromWorld()) || arena.getName().equals(event.getToWorld()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGameModeChange(GameModeChangeShareHandlingEvent event) {
        if (plugin.stateKeeper().busy(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
