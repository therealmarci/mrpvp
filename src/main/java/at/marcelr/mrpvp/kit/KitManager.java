package at.marcelr.mrpvp.kit;

import at.marcelr.mrpvp.ConfigUpdater;
import at.marcelr.mrpvp.MRPvp;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The kits from kits.yml. Every item handed out carries the marker {@code mrpvp:kit} (Game-ID) - such an item has no
 * business outside a round, and {@link #sweep} removes it wherever it turns up.
 */
public final class KitManager {

    private final MRPvp plugin;
    private final NamespacedKey marker;
    private volatile Map<String, Kit> kits = Map.of();

    public KitManager(MRPvp plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "kit");
    }

    /** Reads kits.yml (created with the default kits on the first start). */
    public void load() {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("kits.yml is invalid, no kits loaded: " + e.getMessage());
            kits = Map.of();
            return;
        }
        Map<String, Kit> loaded = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("kits");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection k = section.getConfigurationSection(id);
                if (k != null) {
                    Kit kit = read(id.toLowerCase(Locale.ROOT), k);
                    if (kit != null) {
                        loaded.put(kit.id(), kit);
                    }
                }
            }
        }
        if (loaded.isEmpty()) {
            plugin.getLogger().warning("kits.yml has no kits - nobody can start a duel.");
        }
        // Keeps the order of the file for the menus.
        kits = Collections.unmodifiableMap(loaded);
    }

    private Kit read(String id, ConfigurationSection k) {
        Material icon = Material.matchMaterial(k.getString("icon", "IRON_SWORD"));
        Map<KitSlot, ItemStack> items = new LinkedHashMap<>();
        ConfigurationSection itemSection = k.getConfigurationSection("items");
        if (itemSection != null) {
            for (String slotName : itemSection.getKeys(false)) {
                KitSlot slot = KitSlot.parse(slotName);
                if (slot == null) {
                    plugin.getLogger().warning("kits.yml " + id + ": unknown slot '" + slotName + "' - skipped.");
                    continue;
                }
                ConfigurationSection itemConfig = itemSection.getConfigurationSection(slotName);
                ItemStack item = itemConfig == null ? null : item(id, slotName, itemConfig);
                if (item != null) {
                    items.put(slot, item);
                }
            }
        }
        KitRules rules = rules(id, k.getConfigurationSection("rules"));
        // Without items only makes sense with own rules (sumo: bare hands, only knockback).
        if (items.isEmpty() && rules.isDefault()) {
            plugin.getLogger().warning("kits.yml " + id + ": no items - kit skipped.");
            return null;
        }
        return new Kit(id, names(k, id), icon == null || !icon.isItem() ? Material.IRON_SWORD : icon,
                descriptions(k), k.getString("permission", "").trim(), rules, items);
    }

    /** name: "..." (every language) or name: { de: "...", en: "..." }. */
    private static Map<String, String> names(ConfigurationSection k, String id) {
        Map<String, String> names = new LinkedHashMap<>();
        ConfigurationSection perLanguage = k.getConfigurationSection("name");
        if (perLanguage != null) {
            for (String language : perLanguage.getKeys(false)) {
                names.put(language.toLowerCase(Locale.ROOT), perLanguage.getString(language, id));
            }
        } else {
            names.put(Kit.ALL, k.getString("name", id));
        }
        return names;
    }

    /** description: [lines] (every language) or description: { de: [lines], en: [lines] }. */
    private static Map<String, List<String>> descriptions(ConfigurationSection k) {
        Map<String, List<String>> descriptions = new LinkedHashMap<>();
        ConfigurationSection perLanguage = k.getConfigurationSection("description");
        if (perLanguage != null) {
            for (String language : perLanguage.getKeys(false)) {
                descriptions.put(language.toLowerCase(Locale.ROOT), List.copyOf(perLanguage.getStringList(language)));
            }
        } else {
            descriptions.put(Kit.ALL, List.copyOf(k.getStringList("description")));
        }
        return descriptions;
    }

    /** Writes a value per language - or a plain value if it is the same for every language. */
    private static void setLocalized(YamlConfiguration yaml, String path, Map<String, ?> values) {
        if (values.size() == 1 && values.containsKey(Kit.ALL)) {
            yaml.set(path, values.get(Kit.ALL));
            return;
        }
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            yaml.set(path + "." + (entry.getKey().isEmpty() ? "en" : entry.getKey()), entry.getValue());
        }
    }

    private KitRules rules(String kit, ConfigurationSection r) {
        if (r == null) {
            return KitRules.NONE;
        }
        // effects: { speed: 2 } - unknown effects are skipped with a warning
        Map<String, Integer> effects = new LinkedHashMap<>();
        ConfigurationSection e = r.getConfigurationSection("effects");
        if (e != null) {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
            for (String name : e.getKeys(false)) {
                String key = name.toLowerCase(Locale.ROOT);
                if (!Key.parseable(key) || registry.get(Key.key(key)) == null) {
                    plugin.getLogger().warning("kits.yml " + kit + ": unknown effect '" + name + "' - skipped.");
                    continue;
                }
                effects.put(key, Math.clamp(e.getInt(name, 1), 1, KitRules.MAX_EFFECT_LEVEL));
            }
        }
        // cooldowns: { ender_pearl: 15 } - seconds after using such an item
        Map<String, Integer> cooldowns = new LinkedHashMap<>();
        ConfigurationSection c = r.getConfigurationSection("cooldowns");
        if (c != null) {
            for (String name : c.getKeys(false)) {
                Material type = Material.matchMaterial(name);
                if (type == null || !type.isItem() || type.isAir()) {
                    plugin.getLogger().warning("kits.yml " + kit + ": unknown item '" + name + "' in cooldowns - skipped.");
                    continue;
                }
                cooldowns.put(type.getKey().getKey(), Math.clamp(c.getInt(name, 0), 1, KitRules.MAX_COOLDOWN));
            }
        }
        return new KitRules(r.getBoolean("damage", true), Math.max(0, r.getInt("hits-to-win", 0)),
                r.isBoolean("regen") ? r.getBoolean("regen") : null,
                r.isBoolean("hunger") ? r.getBoolean("hunger") : null,
                r.getStringList("arenas").stream().map(a -> a.toLowerCase(Locale.ROOT)).toList(),
                r.getBoolean("build", false), effects,
                Math.clamp(r.getInt("max-hearts", 0), 0, KitRules.MAX_HEARTS), cooldowns,
                Math.clamp(r.getInt("minutes", 0), 0, KitRules.MAX_MINUTES));
    }

    private static void writeRules(YamlConfiguration yaml, String path, KitRules rules) {
        if (rules.isDefault()) {
            return;
        }
        if (!rules.damage()) {
            yaml.set(path + ".rules.damage", false);
        }
        if (rules.hitsToWin() > 0) {
            yaml.set(path + ".rules.hits-to-win", rules.hitsToWin());
        }
        if (rules.regen() != null) {
            yaml.set(path + ".rules.regen", rules.regen());
        }
        if (rules.hunger() != null) {
            yaml.set(path + ".rules.hunger", rules.hunger());
        }
        if (!rules.arenas().isEmpty()) {
            yaml.set(path + ".rules.arenas", rules.arenas());
        }
        if (rules.build()) {
            yaml.set(path + ".rules.build", true);
        }
        rules.effects().forEach((effect, level) -> yaml.set(path + ".rules.effects." + effect, level));
        if (rules.maxHearts() > 0) {
            yaml.set(path + ".rules.max-hearts", rules.maxHearts());
        }
        rules.cooldowns().forEach((item, seconds) -> yaml.set(path + ".rules.cooldowns." + item, seconds));
        if (rules.minutes() > 0) {
            yaml.set(path + ".rules.minutes", rules.minutes());
        }
    }

    private ItemStack item(String kit, String slot, ConfigurationSection c) {
        String where = "kits.yml " + kit + "." + slot;
        String data = c.getString("data");
        if (data != null && !data.isBlank()) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(data.trim()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning(where + ": unreadable data - skipped.");
                return null;
            }
        }
        Material type = Material.matchMaterial(c.getString("type", ""));
        if (type == null || !type.isItem() || type.isAir()) {
            plugin.getLogger().warning(where + ": unknown item type '" + c.getString("type") + "' - skipped.");
            return null;
        }
        ItemStack item = ItemStack.of(type, Math.clamp(c.getInt("amount", 1), 1, type.getMaxStackSize()));
        ConfigurationSection enchantments = c.getConfigurationSection("enchantments");
        if (enchantments != null) {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            for (String name : enchantments.getKeys(false)) {
                Enchantment enchantment = registry.get(Key.key(name.toLowerCase(Locale.ROOT)));
                if (enchantment == null) {
                    plugin.getLogger().warning(where + ": unknown enchantment '" + name + "' - skipped.");
                    continue;
                }
                item.addUnsafeEnchantment(enchantment, Math.max(1, enchantments.getInt(name, 1)));
            }
        }
        String potion = c.getString("potion");
        if (potion != null && !potion.isBlank() && item.getItemMeta() instanceof PotionMeta) {
            PotionType potionType = RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION)
                    .get(Key.key(potion.trim().toLowerCase(Locale.ROOT)));
            if (potionType == null) {
                plugin.getLogger().warning(where + ": unknown potion '" + potion + "' - skipped.");
            } else {
                item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(potionType));
            }
        }
        String name = c.getString("name");
        boolean unbreakable = c.getBoolean("unbreakable", false);
        if ((name != null && !name.isBlank()) || unbreakable) {
            item.editMeta(meta -> {
                if (name != null && !name.isBlank()) {
                    meta.displayName(MiniMessage.miniMessage().deserialize(name)
                            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                }
                meta.setUnbreakable(unbreakable);
            });
        }
        return item;
    }

    /**
     * Writes a kit into kits.yml (new or replacing the one with the same id) and reloads. Items from the editor are
     * stored as Paper bytes (Base64), so nothing of them is lost. Other kits and the comments stay as they are.
     */
    public void save(Kit kit) throws IOException {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        YamlConfiguration yaml = readFile(file);
        String path = "kits." + kit.id();
        yaml.set(path, null);
        setLocalized(yaml, path + ".name", kit.names());
        yaml.set(path + ".icon", kit.icon().name());
        setLocalized(yaml, path + ".description", kit.descriptions());
        yaml.set(path + ".permission", kit.permission());
        writeRules(yaml, path, kit.rules());
        for (Map.Entry<KitSlot, ItemStack> entry : kit.items().entrySet()) {
            yaml.set(path + ".items." + entry.getKey() + ".data",
                    Base64.getEncoder().encodeToString(entry.getValue().serializeAsBytes()));
        }
        write(file, yaml);
        load();
    }

    /**
     * Puts the kits into this order (the menus show them so); ids not listed keep their place after the listed ones.
     */
    public void reorder(List<String> ids) throws IOException {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        YamlConfiguration yaml = readFile(file);
        ConfigurationSection section = yaml.getConfigurationSection("kits");
        if (section == null) {
            return;
        }
        List<String> order = new ArrayList<>();
        for (String id : ids) {
            if (section.isConfigurationSection(id) && !order.contains(id)) {
                order.add(id);
            }
        }
        for (String id : section.getKeys(false)) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        Map<String, ConfigurationSection> kept = new LinkedHashMap<>();
        for (String id : order) {
            kept.put(id, section.getConfigurationSection(id));
        }
        yaml.set("kits", null);
        kept.forEach((id, kit) -> yaml.set("kits." + id, kit));
        write(file, yaml);
        load();
    }

    public void delete(String id) throws IOException {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        YamlConfiguration yaml = readFile(file);
        yaml.set("kits." + id, null);
        write(file, yaml);
        load();
    }

    /**
     * Puts the bundled default kits back that are missing (changed ones are not touched).
     *
     * @return ids of the kits added
     */
    public List<String> restoreDefaults() throws IOException {
        return restoreDefaults(id -> true);
    }

    /** Like {@link #restoreDefaults()}, only for the bundled kits {@code which} accepts. */
    public List<String> restoreDefaults(java.util.function.Predicate<String> which) throws IOException {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        YamlConfiguration yaml = readFile(file);
        YamlConfiguration bundled = new YamlConfiguration();
        try (var in = plugin.getResource("kits.yml")) {
            if (in == null) {
                return List.of();
            }
            bundled.loadFromString(ConfigUpdater.stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (InvalidConfigurationException e) {
            throw new IOException(e.getMessage(), e);
        }
        List<String> added = new ArrayList<>();
        ConfigurationSection defaults = bundled.getConfigurationSection("kits");
        if (defaults != null) {
            for (String id : defaults.getKeys(false)) {
                if (which.test(id) && !yaml.contains("kits." + id)) {
                    yaml.set("kits." + id, defaults.getConfigurationSection(id));
                    added.add(id);
                }
            }
        }
        if (!added.isEmpty()) {
            write(file, yaml);
            load();
        }
        return added;
    }

    /** The kits that came with the first versions - a server without a record has offered these already. */
    private static final List<String> FIRST_DEFAULTS = List.of("klassisch", "diamant", "netherite", "bogen", "streitkolben");

    /**
     * Default kits that a newer MRpvp brings are added once (data.yml remembers which were offered), so an update
     * gets them without "restore defaults" - and a kit the admin deleted does not come back.
     *
     * @return ids of the kits added
     */
    public List<String> addNewDefaults() throws IOException {
        YamlConfiguration data = at.marcelr.mrpvp.arena.DefaultArena.data(plugin);
        List<String> offered = data.isList("defaults.kits") ? data.getStringList("defaults.kits") : FIRST_DEFAULTS;
        List<String> bundled = new ArrayList<>();
        List<String> added = restoreDefaults(id -> {
            bundled.add(id);
            return !offered.contains(id);
        });
        if (!bundled.isEmpty() && !bundled.equals(offered)) {
            data.set("defaults.kits", bundled);
            at.marcelr.mrpvp.arena.DefaultArena.saveData(plugin, data);
        }
        return added;
    }

    /**
     * Default kits from versions with German-only texts get the texts in all bundled languages - only if their name
     * and description are still exactly the old German ones, so an admin's own texts stay.
     *
     * @return ids of the kits that got translated texts
     */
    public List<String> translateDefaults() throws IOException {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            return List.of();
        }
        YamlConfiguration yaml = readFile(file);
        YamlConfiguration bundled = new YamlConfiguration();
        try (var in = plugin.getResource("kits.yml")) {
            if (in == null) {
                return List.of();
            }
            bundled.loadFromString(ConfigUpdater.stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (InvalidConfigurationException e) {
            throw new IOException(e.getMessage(), e);
        }
        List<String> translated = new ArrayList<>();
        ConfigurationSection defaults = bundled.getConfigurationSection("kits");
        if (defaults == null) {
            return translated;
        }
        for (String id : defaults.getKeys(false)) {
            String path = "kits." + id;
            boolean plainName = yaml.isString(path + ".name");
            if (!plainName || !yaml.contains(path)) {
                continue; // missing, or already per language
            }
            String germanName = defaults.getString(id + ".name.de");
            List<String> germanDescription = defaults.getStringList(id + ".description.de");
            if (germanName != null && germanName.equals(yaml.getString(path + ".name"))
                    && germanDescription.equals(yaml.getStringList(path + ".description"))) {
                yaml.set(path + ".name", defaults.getConfigurationSection(id + ".name"));
                yaml.set(path + ".description", defaults.getConfigurationSection(id + ".description"));
                translated.add(id);
            }
        }
        if (!translated.isEmpty()) {
            write(file, yaml);
        }
        return translated;
    }

    private static YamlConfiguration readFile(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            } catch (InvalidConfigurationException e) {
                throw new IOException("kits.yml is invalid: " + e.getMessage(), e);
            }
        }
        return yaml;
    }

    private static void write(File file, YamlConfiguration yaml) throws IOException {
        yaml.options().width(10_000);
        Files.writeString(file.toPath(), ConfigUpdater.BOM + yaml.saveToString(), StandardCharsets.UTF_8);
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    /** The kits the player may choose. */
    public List<Kit> allowed(Player player) {
        List<Kit> result = new ArrayList<>();
        for (Kit kit : kits.values()) {
            if (kit.allowed(player)) {
                result.add(kit);
            }
        }
        return result;
    }

    /** Puts the kit into the (empty) inventory; every item gets the marker with the Game-ID. */
    public void give(Player player, Kit kit, long gameId) {
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<KitSlot, ItemStack> entry : kit.items().entrySet()) {
            ItemStack item = entry.getValue().clone();
            item.editPersistentDataContainer(pdc -> pdc.set(marker, PersistentDataType.LONG, gameId));
            // Kit cooldown: the game itself waits after a use and shows it on the item (rules.cooldowns).
            Integer cooldown = kit.rules().cooldowns().get(item.getType().getKey().getKey());
            if (cooldown != null) {
                item.setData(io.papermc.paper.datacomponent.DataComponentTypes.USE_COOLDOWN,
                        io.papermc.paper.datacomponent.item.UseCooldown.useCooldown(cooldown)
                                .cooldownGroup(Key.key("mrpvp", "kit_" + item.getType().getKey().getKey())).build());
            }
            KitSlot slot = entry.getKey();
            if (slot.special() == null) {
                inv.setItem(slot.index(), item);
            } else {
                switch (slot.special()) {
                    case HELMET -> inv.setHelmet(item);
                    case CHESTPLATE -> inv.setChestplate(item);
                    case LEGGINGS -> inv.setLeggings(item);
                    case BOOTS -> inv.setBoots(item);
                    case OFFHAND -> inv.setItemInOffHand(item);
                }
            }
        }
    }

    /**
     * The kit's round rules on the player: max hearts (an attribute modifier that is never saved - a crash cannot
     * leave it behind, see {@link at.marcelr.mrpvp.snapshot.PlayerState#KIT_HEALTH}) and the effects for the whole
     * round. Call after {@link #give}; the state reset at the end of the round takes both away again.
     */
    public void applyRules(Player player, Kit kit) {
        KitRules rules = kit.rules();
        var health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(at.marcelr.mrpvp.snapshot.PlayerState.KIT_HEALTH);
            if (rules.maxHearts() > 0) {
                health.addTransientModifier(new org.bukkit.attribute.AttributeModifier(
                        at.marcelr.mrpvp.snapshot.PlayerState.KIT_HEALTH, rules.maxHearts() * 2.0 - health.getValue(),
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        rules.effects().forEach((key, level) -> {
            var type = registry.get(Key.key(key));
            if (type != null) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(type,
                        org.bukkit.potion.PotionEffect.INFINITE_DURATION, level - 1, false, false, true));
            }
        });
    }

    public boolean isKitItem(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(marker);
    }

    /**
     * Removes kit items from the player's inventory and ender chest (outside a round they must not exist).
     *
     * @return number of removed stacks
     */
    public int sweep(Player player) {
        int removed = sweep(player.getInventory()) + sweep(player.getEnderChest());
        if (isKitItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }
        return removed;
    }

    private int sweep(Inventory inventory) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isKitItem(contents[i])) {
                inventory.setItem(i, null);
                removed++;
            }
        }
        return removed;
    }
}
