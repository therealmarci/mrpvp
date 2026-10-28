package at.marcelr.mrpvp.api;

import at.marcelr.mradmin.api.MRadminService;
import at.marcelr.mradmin.api.WebItems;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Items as plain data for the web interface. With MRadmin's item support (API 2) MRadmin describes them - the web
 * then shows them with the game's pictures and can edit them; with an older MRadmin a simple view of our own.
 */
final class ItemView {

    private ItemView() {
    }

    /** MRadmin's item support, or null with an MRadmin before API 2. */
    static WebItems items() {
        MRadminService service = MRadminService.get();
        return service != null && service.apiVersion() >= 2 ? service.items() : null;
    }

    /**
     * The item's data (see MRadmin's WebItems) plus "slot". Without MRadmin's item support: "slot", "type"
     * (e.g. "diamond_sword"), "amount", "name" (custom name or null), "enchants" ([key, level]), "damage" and "max"
     * (durability, max 0 = none). Null for an empty slot.
     */
    static Map<String, Object> of(int slot, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        WebItems items = items();
        if (items != null) {
            Map<String, Object> described = items.describe(item);
            if (described == null) {
                return null;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("slot", slot);
            view.putAll(described);
            return view;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("slot", slot);
        view.put("type", item.getType().getKey().getKey());
        view.put("amount", item.getAmount());
        var meta = item.getItemMeta();
        view.put("name", meta != null && meta.hasCustomName() && meta.customName() != null
                ? PlainTextComponentSerializer.plainText().serialize(meta.customName()) : null);
        List<Object[]> enchants = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
            enchants.add(new Object[] {e.getKey().getKey().getKey(), e.getValue()});
        }
        view.put("enchants", enchants);
        int max = item.getType().getMaxDurability();
        view.put("max", (int) max);
        view.put("damage", meta instanceof Damageable d ? d.getDamage() : 0);
        return view;
    }

    /** All non-empty slots of an array, numbered from {@code first}. */
    static List<Map<String, Object>> list(ItemStack[] items, int first) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            Map<String, Object> view = of(first + i, items[i]);
            if (view != null) {
                result.add(view);
            }
        }
        return result;
    }
}
