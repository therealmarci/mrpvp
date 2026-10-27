package at.marcelr.mrpvp.api;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.snapshot.ToolbarBelts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The optional connection to MRadmin. MRpvp works the same without it; while MRadmin runs, MRpvp is one of its modules
 * ({@link MRadminModule}): players in admin mode cannot duel, open web pages update live, alerts go to the Discord
 * admin log. This class uses no MRadmin types, so it is safe to load without MRadmin - only {@link MRadminModule}
 * touches them, and only after MRadmin is known to be enabled.
 */
public final class MRadminLink implements Listener {

    private static final String MRADMIN = "MRadmin";
    /** At most this often per round a "changed" for the web (hits come many per second). */
    private static final long EVENT_INTERVAL_MILLIS = 500;

    private final MRPvp plugin;
    private final Map<String, Long> lastEvent = new HashMap<>();
    private boolean connected;

    public MRadminLink(MRPvp plugin) {
        this.plugin = plugin;
    }

    /** Connects if MRadmin runs (on enable; MRadmin enables first, MRpvp lists it under softdepend). */
    public void connect() {
        connected = false;
        if (!Bukkit.getPluginManager().isPluginEnabled(MRADMIN)) {
            return;
        }
        try {
            connected = MRadminModule.register(plugin);
            if (!connected) {
                plugin.getLogger().info("MRadmin found, but it is too old for the module API - update MRadmin to connect.");
            }
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Could not connect to MRadmin (versions do not fit?): " + e);
        }
    }

    /** Whether the player is in MRadmin's admin mode (false without MRadmin). */
    public boolean adminMode(Player player) {
        if (!connected) {
            return false;
        }
        try {
            return MRadminModule.adminMode(player);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("MRadmin admin mode check failed, ignoring it from now on: " + e);
            connected = false;
            return false;
        }
    }

    /**
     * Tells open web pages that something changed ({@code type} "game", id = Game-ID).
     *
     * @param throttle at most twice a second per id (for hits)
     */
    public void webEvent(String type, String id, boolean throttle) {
        if (throttle) {
            long now = System.currentTimeMillis();
            Long last = lastEvent.get(type + id);
            if (last != null && now - last < EVENT_INTERVAL_MILLIS) {
                return;
            }
            lastEvent.put(type + id, now);
        } else {
            lastEvent.remove(type + id);
        }
        if (!connected) {
            return;
        }
        try {
            MRadminModule.webChanged(type, id);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("MRadmin web event failed: " + e);
        }
    }

    /** A line in MRadmin's Discord admin log (if MRadmin has one configured). */
    public void adminLog(String text) {
        if (!connected) {
            return;
        }
        try {
            MRadminModule.adminLog(text);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("MRadmin admin log failed: " + e);
        }
    }

    /**
     * MRtoolbar's belts of the player in {@link ToolbarBelts} format (recorded with the state before a duel), or null
     * without MRadmin, MRtoolbar or tool slots. MRtoolbar hands them out through MRadmin (module API).
     */
    public byte[] toolbarBelts(Player player) {
        if (!connected) {
            return null;
        }
        try {
            List<Map<String, Object>> raw = MRadminModule.storedItems(player, "mrtoolbar");
            if (raw.isEmpty()) {
                return null;
            }
            List<ToolbarBelts.Belt> result = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                List<ItemStack> items = new ArrayList<>();
                if (entry.get("items") instanceof List<?> list) {
                    for (Object bytes : list) {
                        items.add(bytes instanceof byte[] b ? ItemStack.deserializeBytes(b) : null);
                    }
                }
                result.add(new ToolbarBelts.Belt(((Number) entry.get("slot")).intValue(),
                        ((Number) entry.get("selected")).intValue(), items));
            }
            return ToolbarBelts.encode(result);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Reading MRtoolbar's belts through MRadmin failed: " + e);
            return null;
        }
    }

    /** MRadmin enabled later (e.g. with a plugin manager): connect now. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals(MRADMIN)) {
            connect();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals(MRADMIN)) {
            connected = false;
        }
    }
}
