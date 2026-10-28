package at.marcelr.mrpvp.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Kits as a file to pass on to another server (JSON) - the same layout the web interface's kit editor sends, so a
 * file from the game can be imported in the web and the other way round:
 * <pre>
 *  { "format": "mrpvp-kits", "version": 1, "minecraft": "26.3", "kits": [
 *    { "id", "names": {lang: text}, "descriptions": {lang: [lines]}, "icon", "permission",
 *      "rules": { damage, hitsToWin, regen, hunger, arenas, build, effects: [{type, level}], maxHearts,
 *                 cooldowns: [{item, seconds}], minutes },
 *      "items": [ { "slot": "0" | "helmet" ..., "item": { "type", "amount", "data": Base64 } } ] } ] }
 * </pre>
 * Every item is stored complete (Paper's bytes). Arenas are named by id - the other server may not have them.
 */
public final class KitTransfer {

    public static final String FORMAT = "mrpvp-kits";
    public static final int VERSION = 1;
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private KitTransfer() {
    }

    /**
     * One kit from a file.
     *
     * @param kit           the kit as it would be stored (arenas the server does not know are already left out)
     * @param brokenItems   items that could not be read (e.g. from a newer Minecraft version) - left out
     * @param unknownArenas arena ids from the file that do not exist here
     */
    public record Entry(Kit kit, int brokenItems, List<String> unknownArenas) {
    }

    /** The kits of a file and the Minecraft version it was made with. */
    public record Contents(String minecraft, List<Entry> kits) {
    }

    // ------------------------------------------------------------------ write

    public static String write(Collection<Kit> kits) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);
        root.addProperty("minecraft", Bukkit.getMinecraftVersion());
        JsonArray list = new JsonArray();
        for (Kit kit : kits) {
            JsonObject k = new JsonObject();
            k.addProperty("id", kit.id());
            JsonObject names = new JsonObject();
            kit.names().forEach(names::addProperty);
            k.add("names", names);
            JsonObject descriptions = new JsonObject();
            kit.descriptions().forEach((language, lines) -> {
                JsonArray array = new JsonArray();
                lines.forEach(array::add);
                descriptions.add(language, array);
            });
            k.add("descriptions", descriptions);
            k.addProperty("icon", kit.icon().getKey().getKey());
            k.addProperty("permission", kit.permission());
            k.add("rules", rules(kit.rules()));
            JsonArray items = new JsonArray();
            kit.items().forEach((slot, item) -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", slot.toString());
                JsonObject data = new JsonObject();
                data.addProperty("type", item.getType().getKey().getKey());
                data.addProperty("amount", item.getAmount());
                data.addProperty("data", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                entry.add("item", data);
                items.add(entry);
            });
            k.add("items", items);
            list.add(k);
        }
        root.add("kits", list);
        return GSON.toJson(root);
    }

    private static JsonObject rules(KitRules rules) {
        JsonObject r = new JsonObject();
        r.addProperty("damage", rules.damage());
        r.addProperty("hitsToWin", rules.hitsToWin());
        if (rules.regen() != null) {
            r.addProperty("regen", rules.regen());
        }
        if (rules.hunger() != null) {
            r.addProperty("hunger", rules.hunger());
        }
        JsonArray arenas = new JsonArray();
        rules.arenas().forEach(arenas::add);
        r.add("arenas", arenas);
        r.addProperty("build", rules.build());
        JsonArray effects = new JsonArray();
        rules.effects().forEach((type, level) -> {
            JsonObject e = new JsonObject();
            e.addProperty("type", type);
            e.addProperty("level", level);
            effects.add(e);
        });
        r.add("effects", effects);
        r.addProperty("maxHearts", rules.maxHearts());
        JsonArray cooldowns = new JsonArray();
        rules.cooldowns().forEach((item, seconds) -> {
            JsonObject c = new JsonObject();
            c.addProperty("item", item);
            c.addProperty("seconds", seconds);
            cooldowns.add(c);
        });
        r.add("cooldowns", cooldowns);
        r.addProperty("minutes", rules.minutes());
        return r;
    }

    // ------------------------------------------------------------------ read

    /**
     * Reads a file. Lenient about single values (unknown effects, arenas or items are left out and counted), strict
     * about the layout.
     *
     * @param arenaExists whether an arena id exists on this server
     * @throws IllegalArgumentException "kit-file" if it is no MRpvp kit file
     */
    public static Contents read(String text, Predicate<String> arenaExists) {
        JsonObject root;
        try {
            root = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("kit-file");
        }
        if (!root.has("format") || !FORMAT.equals(root.get("format").getAsString()) || !root.has("kits")) {
            throw new IllegalArgumentException("kit-file");
        }
        String minecraft = root.has("minecraft") ? root.get("minecraft").getAsString() : "";
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("kits")) {
            if (!element.isJsonObject()) {
                continue;
            }
            Entry entry = entry(element.getAsJsonObject(), arenaExists);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new Contents(minecraft, entries);
    }

    private static Entry entry(JsonObject k, Predicate<String> arenaExists) {
        String id = string(k, "id").toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            return null;
        }
        Map<String, String> names = new LinkedHashMap<>();
        if (k.get("names") instanceof JsonObject n) {
            n.entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
        }
        if (names.isEmpty()) {
            names.put(Kit.ALL, id);
        }
        Map<String, List<String>> descriptions = new LinkedHashMap<>();
        if (k.get("descriptions") instanceof JsonObject d) {
            d.entrySet().forEach(e -> {
                List<String> lines = new ArrayList<>();
                if (e.getValue().isJsonArray()) {
                    e.getValue().getAsJsonArray().forEach(line -> lines.add(line.getAsString()));
                }
                descriptions.put(e.getKey(), List.copyOf(lines));
            });
        }
        Material icon = Material.matchMaterial(string(k, "icon"));
        if (icon == null || !icon.isItem() || icon.isAir()) {
            icon = Material.IRON_SWORD;
        }

        List<String> unknownArenas = new ArrayList<>();
        KitRules rules = KitRules.NONE;
        if (k.get("rules") instanceof JsonObject r) {
            List<String> arenas = new ArrayList<>();
            if (r.get("arenas") instanceof JsonArray a) {
                for (JsonElement e : a) {
                    String arena = e.getAsString().toLowerCase(Locale.ROOT);
                    (arenaExists.test(arena) ? arenas : unknownArenas).add(arena);
                }
            }
            Map<String, Integer> effects = new LinkedHashMap<>();
            if (r.get("effects") instanceof JsonArray a) {
                for (JsonElement e : a) {
                    if (e instanceof JsonObject o && o.has("type")) {
                        effects.put(o.get("type").getAsString().toLowerCase(Locale.ROOT),
                                Math.clamp(number(o, "level", 1), 1, KitRules.MAX_EFFECT_LEVEL));
                    }
                }
            }
            Map<String, Integer> cooldowns = new LinkedHashMap<>();
            if (r.get("cooldowns") instanceof JsonArray a) {
                for (JsonElement e : a) {
                    if (e instanceof JsonObject o && Material.matchMaterial(string(o, "item")) instanceof Material type
                            && type.isItem()) {
                        cooldowns.put(type.getKey().getKey(), Math.clamp(number(o, "seconds", 1), 1, KitRules.MAX_COOLDOWN));
                    }
                }
            }
            rules = new KitRules(!r.has("damage") || r.get("damage").getAsBoolean(),
                    Math.clamp(number(r, "hitsToWin", 0), 0, 1000),
                    r.has("regen") && !r.get("regen").isJsonNull() ? r.get("regen").getAsBoolean() : null,
                    r.has("hunger") && !r.get("hunger").isJsonNull() ? r.get("hunger").getAsBoolean() : null,
                    arenas, r.has("build") && r.get("build").getAsBoolean(), effects,
                    Math.clamp(number(r, "maxHearts", 0), 0, KitRules.MAX_HEARTS), cooldowns,
                    Math.clamp(number(r, "minutes", 0), 0, KitRules.MAX_MINUTES));
        }

        Map<KitSlot, ItemStack> items = new LinkedHashMap<>();
        int broken = 0;
        if (k.get("items") instanceof JsonArray a) {
            for (JsonElement e : a) {
                if (!(e instanceof JsonObject o) || !(o.get("item") instanceof JsonObject item)) {
                    broken++;
                    continue;
                }
                KitSlot slot = KitSlot.parse(string(o, "slot"));
                try {
                    ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(string(item, "data")));
                    if (slot == null || stack.isEmpty()) {
                        broken++;
                    } else {
                        items.put(slot, stack);
                    }
                } catch (RuntimeException ex) {
                    broken++;
                }
            }
        }
        String permission = string(k, "permission").toLowerCase(Locale.ROOT);
        if (!permission.matches("[a-z0-9._-]{0,64}")) {
            permission = "";
        }
        return new Entry(new Kit(id, names, icon, descriptions, permission, rules, items), broken, List.copyOf(unknownArenas));
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    private static int number(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        try {
            return e == null || !e.isJsonPrimitive() ? fallback : e.getAsInt();
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
