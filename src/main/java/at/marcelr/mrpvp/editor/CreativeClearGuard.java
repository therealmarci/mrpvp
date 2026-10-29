package at.marcelr.mrpvp.editor;

import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stops "clear the whole inventory" in creative mode (shift-click on the destroy slot) while a player is in the
 * editor. The client then sends "empty" for every slot at once - normal building never empties more than one or
 * two slots in a tick. The inventory as it was before the first change of a tick is kept; if many slots were
 * emptied in that tick, it is put back the next tick.
 */
public final class CreativeClearGuard implements Listener {

    /** Emptied slots within one tick from which on it counts as clearing (a clear sends 46). */
    private static final int THRESHOLD = 10;

    private record Tick(ItemStack[] before, int[] emptied) {
    }

    private final MRPvp plugin;
    private final Map<UUID, Tick> ticks = new HashMap<>();

    public CreativeClearGuard(MRPvp plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || (plugin.editor().session(player) == null && !plugin.kitEditor().editing(player))) {
            return;
        }
        ItemStack placed = event.getCursor();
        if (placed != null && !placed.isEmpty()) {
            return;
        }
        Tick tick = ticks.get(player.getUniqueId());
        if (tick == null) {
            // First change this tick: remember the inventory as it is before it, check after the tick.
            ItemStack[] before = player.getInventory().getContents();
            for (int i = 0; i < before.length; i++) {
                before[i] = before[i] == null ? null : before[i].clone();
            }
            tick = new Tick(before, new int[1]);
            ticks.put(player.getUniqueId(), tick);
            Tick checked = tick;
            Bukkit.getScheduler().runTask(plugin, () -> check(player, checked));
        }
        tick.emptied()[0]++;
    }

    private void check(Player player, Tick tick) {
        ticks.remove(player.getUniqueId());
        if (tick.emptied()[0] < THRESHOLD || !player.isOnline() || (plugin.editor().session(player) == null && !plugin.kitEditor().editing(player))) {
            return;
        }
        player.getInventory().setContents(tick.before());
        player.updateInventory();
        plugin.messages().send(player, "editor-no-clear");
    }
}
