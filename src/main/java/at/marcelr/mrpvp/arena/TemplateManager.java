package at.marcelr.mrpvp.arena;

import at.marcelr.mrpvp.ConfigUpdater;
import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The arena templates in plugins/MRpvp/templates. The .nbt files are Minecraft's own structure format (written and
 * read through Bukkit's StructureManager), so no WorldEdit is needed. Main thread only.
 */
public final class TemplateManager {

    /** Template ids: what is allowed in a file name everywhere. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final MRPvp plugin;
    private final File folder;
    private final Map<String, ArenaTemplate> templates = new TreeMap<>();
    private final Map<String, Structure> structures = new HashMap<>();

    public TemplateManager(MRPvp plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "templates");
    }

    public void load() {
        templates.clear();
        structures.clear();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().severe("Could not create " + folder);
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        for (File file : files == null ? new File[0] : files) {
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!ID.matcher(id).matches()) {
                plugin.getLogger().warning("templates/" + file.getName() + ": invalid name (a-z, 0-9, _ and - only) - skipped.");
                continue;
            }
            if (!new File(folder, id + ".nbt").exists()) {
                plugin.getLogger().warning("templates/" + id + ".nbt is missing - template skipped.");
                continue;
            }
            ArenaTemplate template = read(id, file);
            if (template != null) {
                templates.put(id, template);
            }
        }
    }

    private ArenaTemplate read(String id, File file) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("templates/" + file.getName() + " is invalid: " + e.getMessage());
            return null;
        }
        List<Integer> size = y.getIntegerList("size");
        if (size.size() != 3 || size.stream().anyMatch(v -> v < 1)) {
            plugin.getLogger().warning("templates/" + file.getName() + ": size missing - skipped.");
            return null;
        }
        ArenaTemplate.Spawn s1 = spawn(y.getConfigurationSection("spawns.1"));
        ArenaTemplate.Spawn s2 = spawn(y.getConfigurationSection("spawns.2"));
        if (s1 == null || s2 == null) {
            plugin.getLogger().warning("templates/" + file.getName() + ": spawns missing - skipped.");
            return null;
        }
        return new ArenaTemplate(id, y.getString("name", id), y.getString("icon", "STONE_BRICKS"),
                y.getInt("version", 1), size.get(0), size.get(1), size.get(2), s1, s2,
                y.getBoolean("allow-build", false), Math.max(1, y.getInt("max-build-height", size.get(1))),
                y.getStringList("kits").stream().map(k -> k.toLowerCase(Locale.ROOT)).toList(),
                y.getBoolean("elytra-only", false),
                y.getBoolean("enabled", true), Math.clamp(y.getInt("pool", plugin.settings().arenas().pool()), 0, 16),
                y.getInt("editor.plot", -1), y.getInt("editor.x"), y.getInt("editor.y"), y.getInt("editor.z"));
    }

    private static ArenaTemplate.Spawn spawn(ConfigurationSection c) {
        if (c == null) {
            return null;
        }
        return new ArenaTemplate.Spawn(c.getDouble("x"), c.getDouble("y"), c.getDouble("z"),
                (float) c.getDouble("yaw"), (float) c.getDouble("pitch"));
    }

    /** Writes the .yml of a template (the .nbt only changes on {@link #save(ArenaTemplate, Structure)}). */
    public void write(ArenaTemplate t) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "MRpvp arena template - edited in game: /duel -> Admin -> Arenen.",
                "Blocks are in " + t.id() + ".nbt (Minecraft structure format)."));
        y.set("name", t.name());
        y.set("icon", t.icon());
        y.set("version", t.version());
        y.set("size", List.of(t.sizeX(), t.sizeY(), t.sizeZ()));
        setSpawn(y, "spawns.1", t.spawn1());
        setSpawn(y, "spawns.2", t.spawn2());
        y.set("allow-build", t.allowBuild());
        y.set("max-build-height", t.maxBuildHeight());
        y.set("kits", t.kits());
        y.set("elytra-only", t.elytraOnly());
        y.set("enabled", t.enabled());
        y.set("pool", t.pool());
        y.set("editor.plot", t.editorPlot());
        y.set("editor.x", t.editorX());
        y.set("editor.y", t.editorY());
        y.set("editor.z", t.editorZ());
        Files.writeString(new File(folder, t.id() + ".yml").toPath(), ConfigUpdater.BOM + y.saveToString(),
                StandardCharsets.UTF_8);
        templates.put(t.id(), t);
    }

    private static void setSpawn(YamlConfiguration y, String path, ArenaTemplate.Spawn s) {
        y.set(path + ".x", s.x());
        y.set(path + ".y", s.y());
        y.set(path + ".z", s.z());
        y.set(path + ".yaw", (double) s.yaw());
        y.set(path + ".pitch", (double) s.pitch());
    }

    /** Stores blocks and data of a template (new or changed). */
    public void save(ArenaTemplate t, Structure structure) throws IOException {
        Bukkit.getStructureManager().saveStructure(new File(folder, t.id() + ".nbt"), structure);
        structures.put(t.id(), structure);
        write(t);
    }

    /** Copies the blocks between the two corners (inclusive) into a new structure - without entities. */
    public static Structure capture(Location min, BlockVector size) {
        Structure structure = Bukkit.getStructureManager().createStructure();
        structure.fill(min, size, false);
        return structure;
    }

    /** The blocks of a template (read from the .nbt on first use), or null if unreadable. */
    public Structure structure(String id) {
        Structure cached = structures.get(id);
        if (cached != null) {
            return cached;
        }
        try {
            Structure structure = Bukkit.getStructureManager().loadStructure(new File(folder, id + ".nbt"));
            structures.put(id, structure);
            return structure;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("templates/" + id + ".nbt could not be read: " + e.getMessage());
            return null;
        }
    }

    public void delete(String id) {
        templates.remove(id);
        structures.remove(id);
        for (String ext : new String[] {".yml", ".nbt"}) {
            File file = new File(folder, id + ext);
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("Could not delete templates/" + file.getName());
            }
        }
    }

    public ArenaTemplate get(String id) {
        return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ArenaTemplate> all() {
        return List.copyOf(templates.values());
    }

    public List<ArenaTemplate> enabled() {
        List<ArenaTemplate> result = new ArrayList<>();
        for (ArenaTemplate t : templates.values()) {
            if (t.enabled()) {
                result.add(t);
            }
        }
        return result;
    }

    /** Whether at least one enabled arena fits the kit - otherwise it cannot be chosen in /duel. */
    public boolean playable(at.marcelr.mrpvp.kit.Kit kit) {
        List<ArenaTemplate> enabled = enabled();
        return enabled.isEmpty() ? kit.rules().arenas().isEmpty() && !kit.rules().build()
                : enabled.stream().anyMatch(a -> a.fits(kit));
    }

    /** The lowest editor plot no template uses. */
    public int freePlot() {
        int plot = 0;
        while (true) {
            int p = plot;
            if (templates.values().stream().noneMatch(t -> t.editorPlot() == p)) {
                return plot;
            }
            plot++;
        }
    }
}
