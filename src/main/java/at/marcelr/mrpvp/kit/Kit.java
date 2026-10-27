package at.marcelr.mrpvp.kit;

import at.marcelr.mrpvp.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * One kit from kits.yml.
 *
 * @param id           key in kits.yml (stored with each game)
 * @param names        display name (MiniMessage) per language ("de", "en" ...); {@link #ALL} = for every language
 * @param descriptions lines for the menu (MiniMessage) per language, like {@code names}
 * @param permission   needed permission, empty = everyone who may duel
 * @param items        slot -> item (the items are templates - {@link KitManager#give} hands out copies)
 * @param rules        what the kit changes about a round (sumo, boxing, UHC ...)
 */
public record Kit(String id, Map<String, String> names, Material icon, Map<String, List<String>> descriptions,
                  String permission, KitRules rules, Map<KitSlot, ItemStack> items) {

    /** Key for a name or description that is the same in every language (a plain value in kits.yml). */
    public static final String ALL = "";

    /** The name in the language texts are built in right now (see {@link Messages#language()}). */
    public String name() {
        return pick(names, id);
    }

    /** The description in the language texts are built in right now (see {@link Messages#language()}). */
    public List<String> description() {
        return pick(descriptions, List.of());
    }

    /**
     * The value for the current language: that language ("de" also for "de_at"), otherwise the one for every
     * language, otherwise English, otherwise any.
     */
    static <T> T pick(Map<String, T> values, T otherwise) {
        String language = Messages.language();
        String base = language.split("_", 2)[0];
        for (String key : new String[]{language, base, ALL, "en"}) {
            T value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return values.isEmpty() ? otherwise : values.values().iterator().next();
    }

    public boolean allowed(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /** The kit brings an elytra (arenas set to "elytra only" accept it). */
    public boolean hasElytra() {
        return items.values().stream().anyMatch(item -> item != null && item.getType() == Material.ELYTRA);
    }
}
