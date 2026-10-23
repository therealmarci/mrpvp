package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

/** Routes clicks to the open {@link Menu}, prevents taking items out and refreshes open menus. */
public final class MenuListener implements Listener {

    private final MRPvp plugin;
    private BukkitTask refreshTask;

    public MenuListener(MRPvp plugin) {
        this.plugin = plugin;
    }

    /** (Re)starts the live refresh with the interval from the config. */
    public void start() {
        stop();
        long interval = plugin.settings().gui().refreshTicks();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
                    plugin.messages().run(player, menu::render);
                }
            }
        }, interval, interval);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /** Closes all open MRpvp menus (on disable, so nobody keeps a dead menu). */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            plugin.messages().run(event.getWhoClicked(), () -> menu.click(event.getSlot(), event.getClick()));
        } else if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            plugin.messages().run(event.getWhoClicked(), () -> menu.clickBottom(event.getSlot(), event.getClick()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
