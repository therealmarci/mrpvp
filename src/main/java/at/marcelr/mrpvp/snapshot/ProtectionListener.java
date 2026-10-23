package at.marcelr.mrpvp.snapshot;

import at.marcelr.mrpvp.MRPvp;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Nothing a player reaches in the arena world counts outside: progress towards an advancement (e.g. "Cover Me with
 * Diamonds" from kit armor) is not granted at all while MRpvp has the player - no toast, no message, nothing to take
 * back. Taking it back afterwards does not work: the game grants it again the next tick as long as the condition
 * holds (the armor is still worn), an endless loop.
 */
public final class ProtectionListener implements Listener {

    private final MRPvp plugin;

    public ProtectionListener(MRPvp plugin) {
        this.plugin = plugin;
    }

    private boolean protect(Player player) {
        return plugin.settings().protection().revokeAdvancements() && plugin.stateKeeper().busy(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        // Recipe unlocks and other hidden ones have no display - they do no harm and come back anyway.
        if (event.getAdvancement().getDisplay() != null && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Safety net (granted by a command or another plugin): at least nobody hears of it. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (event.getAdvancement().getDisplay() != null && protect(event.getPlayer())) {
            event.message(null);
        }
    }
}
