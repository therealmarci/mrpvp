package at.marcelr.mrpvp.api;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Items as plain data for the web interface - what the page needs to show a slot, never the item itself. */
final class ItemView {

    private ItemView() {
    }

    /**
     * "slot", "type" (e.g. "diamond_sword"), "amount", "name" (custom name or null), "enchants" ([key, level]),
     * "damage" and "max" (durability, max 0 = none). Null for an empty slot.
     */
    static Map<String, Object> of(int slot, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
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
