package at.marcelr.mrpvp.api;

import at.marcelr.mradmin.api.WebItems;
import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.kit.KitRules;
import at.marcelr.mrpvp.kit.KitSlot;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The kit editor of the web interface (permission mrpvp.admin.kits, like the kit editor in the game).
 * <pre>
 *  kits                {kits: [id, name, icon, permission, rules, hotbar (items), item count], arenas}
 *  kit      {id}       one kit: names / descriptions per language, icon, permission, rules, all items, arenas to choose
 *  kitSave  {kit}      new or changed kit ("original" = id before, null for a new one; another id renames it)
 *  kitDelete {id}      kitOrder {ids}
 * </pre>
 * Items go through MRadmin's item support (WebItems, API 2) - so they can be shown and edited with everything.
 */
final class KitWeb {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern PERMISSION = Pattern.compile("[a-z0-9._-]{0,64}");
    private static final int MAX_NAME = 128;
    private static final int MAX_LINES = 10;
    private static final int MAX_LINE = 200;
    private static final Map<KitSlot.Special, Integer> ARMOR = Map.of(KitSlot.Special.BOOTS, 0,
            KitSlot.Special.LEGGINGS, 1, KitSlot.Special.CHESTPLATE, 2, KitSlot.Special.HELMET, 3);

    private KitWeb() {
    }

    // ------------------------------------------------------------------ data

    static Map<String, Object> list(MRPvp plugin) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Kit kit : plugin.kits().all()) {
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("id", kit.id());
            k.put("name", plain(kit.name()));
            k.put("icon", kit.icon().getKey().getKey());
            k.put("permission", kit.permission());
            k.put("rules", rules(kit.rules()));
            List<Map<String, Object>> hotbar = new ArrayList<>();
            kit.items().forEach((slot, item) -> {
                if (slot.special() == null && slot.index() < 9) {
                    Map<String, Object> view = ItemView.of(slot.index(), item);
                    if (view != null) {
                        hotbar.add(view);
                    }
                }
            });
            k.put("hotbar", hotbar);
            k.put("items", kit.items().size());
            result.add(k);
        }
        return Map.of("kits", result, "arenas", arenas(plugin));
    }

    static Map<String, Object> kit(MRPvp plugin, String id) {
        Kit kit = plugin.kits().get(id);
        if (kit == null) {
            return null;
        }
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("id", kit.id());
        k.put("names", kit.names());
        k.put("descriptions", kit.descriptions());
        k.put("icon", kit.icon().getKey().getKey());
        k.put("permission", kit.permission());
        k.put("rules", rules(kit.rules()));
        List<Map<String, Object>> inventory = new ArrayList<>();
        List<Map<String, Object>> armor = new ArrayList<>();
        List<Map<String, Object>> offhand = new ArrayList<>();
        kit.items().forEach((slot, item) -> {
            if (slot.special() == null) {
                add(inventory, ItemView.of(slot.index(), item));
            } else if (slot.special() == KitSlot.Special.OFFHAND) {
                add(offhand, ItemView.of(0, item));
            } else {
                add(armor, ItemView.of(ARMOR.get(slot.special()), item));
            }
        });
        k.put("inventory", inventory);
        k.put("armor", armor);
        k.put("offhand", offhand);
        k.put("arenas", arenas(plugin));
        return k;
    }

    /** Arenas a kit can be limited to. */
    static List<Map<String, Object>> arenas(MRPvp plugin) {
        List<Map<String, Object>> arenas = new ArrayList<>();
        for (ArenaTemplate t : plugin.templates().all()) {
            arenas.add(Map.of("id", t.id(), "name", plain(t.name()), "build", t.allowBuild(), "enabled", t.enabled()));
        }
        return arenas;
    }

    private static Map<String, Object> rules(KitRules rules) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("damage", rules.damage());
        r.put("hitsToWin", rules.hitsToWin());
        r.put("regen", rules.regen());
        r.put("hunger", rules.hunger());
        r.put("arenas", rules.arenas());
        r.put("build", rules.build());
        List<Map<String, Object>> effects = new ArrayList<>();
        rules.effects().forEach((type, level) -> effects.add(Map.of("type", type, "level", level)));
        r.put("effects", effects);
        r.put("maxHearts", rules.maxHearts());
        List<Map<String, Object>> cooldowns = new ArrayList<>();
        rules.cooldowns().forEach((item, seconds) -> cooldowns.add(Map.of("item", item, "seconds", seconds)));
        r.put("cooldowns", cooldowns);
        r.put("minutes", rules.minutes());
        return r;
    }

    private static void add(List<Map<String, Object>> list, Map<String, Object> view) {
        if (view != null) {
            list.add(view);
        }
    }

    // ------------------------------------------------------------------ actions

    /** @return null if saved, otherwise an error key */
    static String save(MRPvp plugin, Map<?, ?> args, String admin) {
        try {
            return saveChecked(plugin, args, admin);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static String saveChecked(MRPvp plugin, Map<?, ?> args, String admin) {
        WebItems items = ItemView.items();
        if (items == null) {
            return "kit-items-unsupported";
        }
        if (!(args.get("kit") instanceof Map<?, ?> data)) {
            return "kit-invalid";
        }
        String id = text(data.get("id")).toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            return "kit-id";
        }
        String original = data.get("original") instanceof String o && !o.isBlank() ? o.toLowerCase(Locale.ROOT) : null;
        if (original != null && plugin.kits().get(original) == null) {
            return "not-found";
        }
        if (!id.equals(original) && plugin.kits().get(id) != null) {
            return "kit-exists";
        }

        Map<String, String> names = new LinkedHashMap<>();
        if (data.get("names") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String value = text(e.getValue()).trim();
                if (value.length() > MAX_NAME) {
                    return "kit-name";
                }
                if (!value.isEmpty()) {
                    names.put(language(e.getKey()), value);
                }
            }
        }
        if (names.isEmpty()) {
            return "kit-name";
        }
        Map<String, List<String>> descriptions = new LinkedHashMap<>();
        if (data.get("descriptions") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getValue() instanceof List<?> lines) || lines.size() > MAX_LINES) {
                    return "kit-description";
                }
                List<String> kept = new ArrayList<>();
                for (Object line : lines) {
                    String value = text(line);
                    if (value.length() > MAX_LINE) {
                        return "kit-description";
                    }
                    kept.add(value);
                }
                while (!kept.isEmpty() && kept.getLast().isBlank()) {
                    kept.removeLast();
                }
                if (!kept.isEmpty()) {
                    descriptions.put(language(e.getKey()), List.copyOf(kept));
                }
            }
        }
        Material icon = Material.matchMaterial(text(data.get("icon")));
        if (icon == null || !icon.isItem() || icon.isAir()) {
            return "kit-icon";
        }
        String permission = text(data.get("permission")).trim().toLowerCase(Locale.ROOT);
        if (!PERMISSION.matcher(permission).matches()) {
            return "kit-permission";
        }
        KitRules rules;
        try {
            rules = rules(plugin, data.get("rules"));
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        Map<KitSlot, ItemStack> stacks = new LinkedHashMap<>();
        if (data.get("items") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> entry) || !(entry.get("item") instanceof Map<?, ?> item)) {
                    return "kit-invalid";
                }
                KitSlot slot = KitSlot.parse(text(entry.get("slot")));
                if (slot == null || stacks.containsKey(slot)) {
                    return "kit-invalid";
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemData = (Map<String, Object>) item;
                    stacks.put(slot, items.build(itemData));
                } catch (IllegalArgumentException e) {
                    return e.getMessage();
                }
            }
        }
        // Without items only with own rules (sumo: bare hands) - kits.yml would skip it otherwise.
        if (stacks.isEmpty() && rules.isDefault()) {
            return "kit-empty";
        }

        List<String> order = new ArrayList<>(plugin.kits().all().stream().map(Kit::id).toList());
        if (original != null) {
            order.set(order.indexOf(original), id);
        }
        try {
            plugin.kits().save(new Kit(id, names, icon, descriptions, permission, rules, stacks));
            if (original != null && !original.equals(id)) {
                plugin.kits().delete(original);
            }
            plugin.kits().reorder(order);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save kit '" + id + "': " + e.getMessage());
            return "kit-save-failed";
        }
        plugin.getLogger().info("Kit '" + id + "' " + (original == null ? "created" : "changed") + " by " + admin + " (web).");
        return null;
    }

    private static KitRules rules(MRPvp plugin, Object value) {
        if (!(value instanceof Map<?, ?> r)) {
            return KitRules.NONE;
        }
        int hits = r.get("hitsToWin") instanceof Number n ? n.intValue() : 0;
        if (hits < 0 || hits > 1000) {
            throw new IllegalArgumentException("kit-rules");
        }
        List<String> arenas = new ArrayList<>();
        if (r.get("arenas") instanceof List<?> list) {
            for (Object o : list) {
                String arena = text(o).toLowerCase(Locale.ROOT);
                if (plugin.templates().get(arena) == null) {
                    throw new IllegalArgumentException("kit-arena");
                }
                if (!arenas.contains(arena)) {
                    arenas.add(arena);
                }
            }
        }
        Map<String, Integer> effects = new LinkedHashMap<>();
        if (r.get("effects") instanceof List<?> list) {
            var registry = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.MOB_EFFECT);
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> e)) {
                    throw new IllegalArgumentException("kit-rules");
                }
                String type = text(e.get("type")).toLowerCase(Locale.ROOT);
                int level = e.get("level") instanceof Number n ? n.intValue() : 0;
                if (!net.kyori.adventure.key.Key.parseable(type) || registry.get(net.kyori.adventure.key.Key.key(type)) == null
                        || level < 1 || level > KitRules.MAX_EFFECT_LEVEL) {
                    throw new IllegalArgumentException("kit-effect");
                }
                effects.put(type, level);
            }
        }
        Map<String, Integer> cooldowns = new LinkedHashMap<>();
        if (r.get("cooldowns") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> c)) {
                    throw new IllegalArgumentException("kit-rules");
                }
                Material type = Material.matchMaterial(text(c.get("item")));
                int seconds = c.get("seconds") instanceof Number n ? n.intValue() : 0;
                if (type == null || !type.isItem() || type.isAir() || seconds < 1 || seconds > KitRules.MAX_COOLDOWN) {
                    throw new IllegalArgumentException("kit-cooldown");
                }
                cooldowns.put(type.getKey().getKey(), seconds);
            }
        }
        int hearts = r.get("maxHearts") instanceof Number n ? n.intValue() : 0;
        int minutes = r.get("minutes") instanceof Number n ? n.intValue() : 0;
        if (hearts < 0 || hearts > KitRules.MAX_HEARTS || minutes < 0 || minutes > KitRules.MAX_MINUTES) {
            throw new IllegalArgumentException("kit-rules");
        }
        return new KitRules(!Boolean.FALSE.equals(r.get("damage")), hits,
                r.get("regen") instanceof Boolean b ? b : null, r.get("hunger") instanceof Boolean b ? b : null,
                List.copyOf(arenas), Boolean.TRUE.equals(r.get("build")), effects, hearts, cooldowns, minutes);
    }

    static String delete(MRPvp plugin, Map<?, ?> args, String admin) {
        String id = text(args.get("id")).toLowerCase(Locale.ROOT);
        if (plugin.kits().get(id) == null) {
            return "not-found";
        }
        try {
            plugin.kits().delete(id);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete kit '" + id + "': " + e.getMessage());
            return "kit-save-failed";
        }
        plugin.getLogger().info("Kit '" + id + "' deleted by " + admin + " (web).");
        return null;
    }

    static String order(MRPvp plugin, Map<?, ?> args) {
        if (!(args.get("ids") instanceof List<?> ids)) {
            return "kit-invalid";
        }
        try {
            plugin.kits().reorder(ids.stream().map(KitWeb::text).map(s -> s.toLowerCase(Locale.ROOT)).toList());
        } catch (IOException e) {
            return "kit-save-failed";
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    /** "de", "en" ... - "" stands for "every language" (a plain value in kits.yml). */
    private static String language(Object key) {
        String value = text(key).trim().toLowerCase(Locale.ROOT);
        if (!value.isEmpty() && !value.matches("[a-z]{2}(_[a-z]{2})?")) {
            throw new IllegalArgumentException("kit-name");
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
