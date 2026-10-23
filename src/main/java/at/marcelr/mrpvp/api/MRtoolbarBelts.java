package at.marcelr.mrpvp.api;

import at.marcelr.mrpvp.snapshot.ToolbarBelts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads MRtoolbar's belts ({@code at.marcelr.mrtoolbar.api.MRtoolbarApi.belts(UUID)}) for the saved state - found
 * at runtime, no dependency in either direction. Without MRtoolbar (or an older one) there are no belts. Only
 * recorded for the admin view: MRpvp never changes a belt (MRtoolbar pauses during a round).
 */
public final class MRtoolbarBelts {

    private static final String API_CLASS = "at.marcelr.mrtoolbar.api.MRtoolbarApi";

    private final Logger log;
    private Plugin resolvedFor;
    private MethodHandle belts;

    public MRtoolbarBelts(Logger log) {
        this.log = log;
    }

    /** The belts in {@link ToolbarBelts} format, or null without MRtoolbar / tool slots. */
    public byte[] belts(Player player) {
        Plugin mrtoolbar = Bukkit.getPluginManager().getPlugin("MRtoolbar");
        if (mrtoolbar == null || !mrtoolbar.isEnabled()) {
            return null;
        }
        if (mrtoolbar != resolvedFor) {
            resolvedFor = mrtoolbar;
            belts = resolve(mrtoolbar);
        }
        if (belts == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = (List<Map<String, Object>>) belts.invokeExact(player.getUniqueId());
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
        } catch (Throwable e) {
            log.warning("Reading MRtoolbar's belts failed, not recording them from now on: " + e);
            belts = null;
            return null;
        }
    }

    private MethodHandle resolve(Plugin mrtoolbar) {
        try {
            Class<?> api = Class.forName(API_CLASS, true, mrtoolbar.getClass().getClassLoader());
            return MethodHandles.publicLookup().findStatic(api, "belts", MethodType.methodType(List.class, UUID.class));
        } catch (ReflectiveOperationException e) {
            // MRtoolbar without this API (0.1.x)
            return null;
        }
    }
}
