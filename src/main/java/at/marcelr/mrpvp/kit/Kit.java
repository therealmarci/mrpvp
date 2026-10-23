package at.marcelr.mrpvp.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * One kit from kits.yml.
 *
 * @param id          key in kits.yml (stored with each game)
 * @param name        display name (MiniMessage)
 * @param description lines for the menu (MiniMessage)
 * @param permission  needed permission, empty = everyone who may duel
 * @param items       slot -> item (the items are templates - {@link KitManager#give} hands out copies)
 * @param rules       what the kit changes about a round (sumo, boxing, UHC ...)
 */
public record Kit(String id, String name, Material icon, List<String> description, String permission, KitRules rules,
                  Map<KitSlot, ItemStack> items) {

    public boolean allowed(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /** The kit brings an elytra (arenas set to "elytra only" accept it). */
    public boolean hasElytra() {
        return items.values().stream().anyMatch(item -> item != null && item.getType() == Material.ELYTRA);
    }
}
