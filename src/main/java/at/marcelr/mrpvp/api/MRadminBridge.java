package at.marcelr.mrpvp.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Talks to MRadmin ({@code at.marcelr.mradmin.api.MRadminApi}) - found at runtime, no dependency in either
 * direction:
 * <ul>
 *   <li>{@code adminMode(UUID)} (API 1): players in admin mode cannot duel;</li>
 *   <li>{@code webEvent(String, String, String)} (API 2): "look again" for open pages of the web interface;</li>
 *   <li>{@code adminLog(String, String)} (API 2): a line in MRadmin's Discord admin log.</li>
 * </ul>
 * Without MRadmin (or an older one) each simply does nothing. Looked up once per MRadmin instance.
 */
public final class MRadminBridge {

    private static final String API_CLASS = "at.marcelr.mradmin.api.MRadminApi";
    private static final String PLUGIN = "mrpvp";
    /** At most this often per round a "changed" for the web (hits come many per second). */
    private static final long EVENT_INTERVAL_MILLIS = 500;

    private final Logger log;
    private Plugin resolvedFor;
    private MethodHandle adminMode;
    private MethodHandle webEvent;
    private MethodHandle adminLog;
    private final Map<String, Long> lastEvent = new HashMap<>();

    public MRadminBridge(Logger log) {
        this.log = log;
    }

    private boolean resolve() {
        Plugin mradmin = Bukkit.getPluginManager().getPlugin("MRadmin");
        if (mradmin == null || !mradmin.isEnabled()) {
            return false;
        }
        if (mradmin != resolvedFor) {
            resolvedFor = mradmin;
            adminMode = find(mradmin, "adminMode", MethodType.methodType(boolean.class, UUID.class));
            webEvent = find(mradmin, "webEvent", MethodType.methodType(void.class, String.class, String.class, String.class));
            adminLog = find(mradmin, "adminLog", MethodType.methodType(void.class, String.class, String.class));
        }
        return true;
    }

    private static MethodHandle find(Plugin mradmin, String name, MethodType type) {
        try {
            Class<?> api = Class.forName(API_CLASS, true, mradmin.getClass().getClassLoader());
            return MethodHandles.publicLookup().findStatic(api, name, type);
        } catch (ReflectiveOperationException e) {
            // older MRadmin without this method
            return null;
        }
    }

    public boolean adminMode(Player player) {
        if (!resolve() || adminMode == null) {
            return false;
        }
        try {
            return (boolean) adminMode.invokeExact(player.getUniqueId());
        } catch (Throwable e) {
            log.warning("MRadmin admin mode check failed, ignoring it from now on: " + e);
            adminMode = null;
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
        if (!resolve() || webEvent == null) {
            return;
        }
        try {
            webEvent.invokeExact(PLUGIN, type, id);
        } catch (Throwable e) {
            log.warning("MRadmin web event failed, not sending them from now on: " + e);
            webEvent = null;
        }
    }

    /** A line in MRadmin's Discord admin log (if MRadmin has one configured). */
    public void adminLog(String text) {
        if (!resolve() || adminLog == null) {
            return;
        }
        try {
            adminLog.invokeExact(PLUGIN, text);
        } catch (Throwable e) {
            log.warning("MRadmin admin log failed, not sending to it from now on: " + e);
            adminLog = null;
        }
    }
}
