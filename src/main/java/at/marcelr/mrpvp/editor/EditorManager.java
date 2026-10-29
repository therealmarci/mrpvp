package at.marcelr.mrpvp.editor;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Messages;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.arena.TemplateManager;
import at.marcelr.mrpvp.gui.EditorExitMenu;
import at.marcelr.mrpvp.gui.TemplateSettingsMenu;
import at.marcelr.mrpvp.snapshot.Snapshot;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * The arena editor. Building happens in drafts "under construction": a new arena or changes to an existing one live
 * in their own plot of the editor area until somebody publishes them - an existing arena stays playable in its old
 * version until then (instances come from the saved structure, not from the plot). Several admins can build in the
 * same draft; a draft stays (arena-drafts.yml) when everyone leaves. Every builder's own state is saved (like
 * before a round), they get creative mode and a tool hotbar. Every template keeps its plot - the build there is the
 * source of the template, so changing it later starts from exactly what was saved.
 */
public final class EditorManager implements Listener {

    public static final String PERM_ARENA = "mrpvp.admin.arena";

    /** The editor area lies far away from the slots. */
    private static final int EDITOR_Z = -20000;
    private static final int BASE_Y = 100;
    private static final int PARTICLE_TICKS = 10;
    /** Drafts with builders inside are written every 30 s (settings, last change). */
    private static final int AUTOSAVE_RUNS = 60;

    enum Tool {
        WAND(0, Material.BLAZE_ROD, "editor-tool-wand"),
        SPAWN1(1, Material.RED_DYE, "editor-tool-spawn1"),
        SPAWN2(2, Material.LIGHT_BLUE_DYE, "editor-tool-spawn2"),
        SETTINGS(7, Material.COMPARATOR, "editor-tool-settings"),
        EXIT(8, Material.LIME_DYE, "editor-tool-exit");

        final int slot;
        final Material material;
        final String key;

        Tool(int slot, Material material, String key) {
            this.slot = slot;
            this.material = material;
            this.key = key;
        }
    }

    private final MRPvp plugin;
    private final NamespacedKey toolKey;
    private final File file;
    /** all drafts by arena id */
    private final Map<String, EditorSession> drafts = new LinkedHashMap<>();
    /** the draft every builder is in */
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private BukkitTask task;
    private int runs;

    public EditorManager(MRPvp plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "editor-tool");
        this.file = new File(plugin.getDataFolder(), "arena-drafts.yml");
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::show, PARTICLE_TICKS, PARTICLE_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public EditorSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** The draft of an arena, or null. */
    public EditorSession draft(String id) {
        return drafts.get(id);
    }

    public Collection<EditorSession> drafts() {
        return List.copyOf(drafts.values());
    }

    /** Names of the players building in a draft right now (empty = nobody). */
    public String builderNames(EditorSession s) {
        return s.builders.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName)
                .collect(Collectors.joining(", "));
    }

    private Messages messages() {
        return plugin.messages();
    }

    private TemplateManager templates() {
        return plugin.templates();
    }

    /** Lowest corner of an editor plot. */
    private Location plotOrigin(int plot) {
        return new Location(plugin.arenaWorld().world(), plot * plugin.settings().arenas().spacing(), BASE_Y, EDITOR_Z);
    }

    // ------------------------------------------------------------------ drafts on disk

    /** Reads the drafts (after the arena world is loaded). */
    public void load() {
        drafts.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection all = y.getConfigurationSection("drafts");
        if (all == null) {
            return;
        }
        World w = plugin.arenaWorld().world();
        for (String id : all.getKeys(false)) {
            ConfigurationSection d = all.getConfigurationSection(id);
            if (d == null || !TemplateManager.ID.matcher(id).matches()) {
                continue;
            }
            EditorSession s = new EditorSession(id, d.getInt("plot"));
            s.creator = d.getString("creator", "");
            s.lastBuilder = d.getString("last-builder", "");
            s.created = d.getLong("created");
            s.changed = d.getLong("changed");
            s.name = d.getString("name", "<white>" + id);
            s.icon = d.getString("icon", Material.STONE_BRICKS.name());
            s.allowBuild = d.getBoolean("allow-build");
            s.elytraOnly = d.getBoolean("elytra-only");
            s.maxBuildHeight = d.getInt("max-build-height");
            s.kits = new ArrayList<>(d.getStringList("kits"));
            s.pool = d.getInt("pool", plugin.settings().arenas().pool());
            s.enabled = d.getBoolean("enabled", true);
            s.version = d.getInt("version");
            s.pos1 = vector(d.getIntegerList("pos1"));
            s.pos2 = vector(d.getIntegerList("pos2"));
            s.spawn1 = location(w, d.getDoubleList("spawn1"));
            s.spawn2 = location(w, d.getDoubleList("spawn2"));
            drafts.put(id, s);
        }
        if (!drafts.isEmpty()) {
            plugin.getLogger().info("Loaded " + drafts.size() + " arena draft(s) under construction: " + drafts.keySet());
        }
    }

    private void saveDrafts() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Arena drafts under construction (MRpvp editor). Do not edit while the server runs."));
        for (EditorSession s : drafts.values()) {
            String p = "drafts." + s.id + ".";
            y.set(p + "plot", s.plot);
            y.set(p + "creator", s.creator);
            y.set(p + "last-builder", s.lastBuilder);
            y.set(p + "created", s.created);
            y.set(p + "changed", s.changed);
            y.set(p + "name", s.name);
            y.set(p + "icon", s.icon);
            y.set(p + "allow-build", s.allowBuild);
            y.set(p + "elytra-only", s.elytraOnly);
            y.set(p + "max-build-height", s.maxBuildHeight);
            y.set(p + "kits", s.kits);
            y.set(p + "pool", s.pool);
            y.set(p + "enabled", s.enabled);
            y.set(p + "version", s.version);
            if (s.pos1 != null) {
                y.set(p + "pos1", List.of(s.pos1.getBlockX(), s.pos1.getBlockY(), s.pos1.getBlockZ()));
            }
            if (s.pos2 != null) {
                y.set(p + "pos2", List.of(s.pos2.getBlockX(), s.pos2.getBlockY(), s.pos2.getBlockZ()));
            }
            if (s.spawn1 != null) {
                y.set(p + "spawn1", list(s.spawn1));
            }
            if (s.spawn2 != null) {
                y.set(p + "spawn2", list(s.spawn2));
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save the arena drafts", e);
        }
    }

    private static BlockVector vector(List<Integer> v) {
        return v.size() == 3 ? new BlockVector(v.get(0), v.get(1), v.get(2)) : null;
    }

    private static Location location(World w, List<Double> v) {
        return v.size() == 5 ? new Location(w, v.get(0), v.get(1), v.get(2), v.get(3).floatValue(), v.get(4).floatValue())
                : null;
    }

    private static List<Double> list(Location l) {
        return List.of(l.getX(), l.getY(), l.getZ(), (double) l.getYaw(), (double) l.getPitch());
    }

    // ------------------------------------------------------------------ enter / leave

    /** Opens a draft for a new arena with the given id - or joins it, if that draft exists already. */
    public void create(Player player, String id) {
        EditorSession existing = drafts.get(id);
        if (existing != null) {
            join(player, existing);
            return;
        }
        if (templates().get(id) != null) {
            messages().send(player, "editor-id-taken", tag("id", id));
            return;
        }
        EditorSession session = new EditorSession(id, freePlot());
        session.name = "<white>" + id;
        session.icon = Material.STONE_BRICKS.name();
        session.pool = plugin.settings().arenas().pool();
        session.creator = player.getName();
        session.created = System.currentTimeMillis();
        Location origin = plotOrigin(session.plot);
        if (!enter(player, session)) {
            return;
        }
        drafts.put(id, session);
        saveDrafts();
        // A small starting point in the void.
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                Block block = origin.getWorld().getBlockAt(origin.getBlockX() + x, BASE_Y, origin.getBlockZ() + z);
                if (block.getType().isAir()) {
                    block.setType(Material.GLASS, false);
                }
            }
        }
        player.teleport(origin.clone().add(1.5, 1, 1.5));
        plugin.getLogger().info(player.getName() + " started a draft for the new arena '" + id + "'.");
        messages().send(player, "editor-started-new", tag("id", id));
    }

    /** Opens a draft with changes to an existing template - or joins it, if there is one already. */
    public void edit(Player player, ArenaTemplate t) {
        EditorSession existing = drafts.get(t.id());
        if (existing != null) {
            join(player, existing);
            return;
        }
        int plot = t.editorPlot() >= 0 ? t.editorPlot() : freePlot();
        EditorSession session = new EditorSession(t.id(), plot);
        session.name = t.name();
        session.icon = t.icon();
        session.allowBuild = t.allowBuild();
        session.elytraOnly = t.elytraOnly();
        session.maxBuildHeight = t.maxBuildHeight();
        session.kits = new ArrayList<>(t.kits());
        session.pool = t.pool();
        session.enabled = t.enabled();
        session.version = t.version();
        session.creator = player.getName();
        session.created = System.currentTimeMillis();
        World w = plugin.arenaWorld().world();
        Location origin = t.editorPlot() >= 0 ? new Location(w, t.editorX(), t.editorY(), t.editorZ()) : plotOrigin(plot);
        session.pos1 = new BlockVector(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        session.pos2 = new BlockVector(origin.getBlockX() + t.sizeX() - 1, origin.getBlockY() + t.sizeY() - 1,
                origin.getBlockZ() + t.sizeZ() - 1);
        session.spawn1 = absolute(origin, t.spawn1());
        session.spawn2 = absolute(origin, t.spawn2());
        if (!enter(player, session)) {
            return;
        }
        drafts.put(t.id(), session);
        saveDrafts();
        if (looksEmpty(origin, t)) {
            // The source build is gone (new world, plot never used): start from the saved blocks.
            Structure structure = templates().structure(t.id());
            if (structure != null) {
                structure.place(origin, false, StructureRotation.NONE, Mirror.NONE, 0, 1f, new Random());
            }
        }
        player.teleport(session.spawn1);
        plugin.getLogger().info(player.getName() + " started a draft with changes to arena '" + t.id() + "'.");
        messages().send(player, "editor-started", tag("id", t.id()));
    }

    /** Builds along in an existing draft. */
    public void join(Player player, EditorSession s) {
        if (s.builders.contains(player.getUniqueId()) || !enter(player, s)) {
            return;
        }
        player.teleport(s.spawn1 != null ? s.spawn1 : plotOrigin(s.plot).add(1.5, 1, 1.5));
        Messages m = messages();
        m.send(player, "editor-joined", tag("id", s.id), tag("builders", builderNames(s)));
        others(s, player, other -> m.send(other, "editor-joined-other", tag("player", player.getName()), tag("id", s.id)));
    }

    private boolean enter(Player player, EditorSession session) {
        if (plugin.arenaWorld().world() == null) {
            messages().send(player, "arena-world-missing");
            return false;
        }
        if (plugin.stateKeeper().inside(player) || plugin.games().inGame(player.getUniqueId())) {
            messages().send(player, "invite-error-busy-self");
            return false;
        }
        if (plugin.stateKeeper().enter(player, Snapshot.Purpose.EDITOR, null, GameMode.CREATIVE) == null) {
            messages().send(player, "state-save-failed");
            return false;
        }
        sessions.put(player.getUniqueId(), session);
        session.builders.add(player.getUniqueId());
        session.lastBuilder = player.getName();
        session.changed = System.currentTimeMillis();
        player.setAllowFlight(true);
        player.setFlying(true);
        giveTools(player);
        return true;
    }

    /** Leaves the editor, the draft stays under construction. */
    public void leave(Player player) {
        EditorSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        exit(player);
        saveDrafts();
        Messages m = messages();
        m.send(player, "editor-left", tag("id", s.id));
        others(s, player, other -> m.send(other, "editor-left-other", tag("player", player.getName())));
    }

    /** Own state back (inventory, position ...). */
    private void exit(Player player) {
        EditorSession s = sessions.remove(player.getUniqueId());
        if (s != null) {
            s.builders.remove(player.getUniqueId());
            s.changed = System.currentTimeMillis();
            plugin.stateKeeper().leave(player);
        }
    }

    /** Everyone still building in the draft except the given player. */
    private void others(EditorSession s, Player except, java.util.function.Consumer<Player> action) {
        for (UUID id : List.copyOf(s.builders)) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && !other.equals(except)) {
                action.accept(other);
            }
        }
    }

    /** Ends a draft: everyone building in it gets their state back, the draft is gone. */
    private void close(EditorSession s, Player by, String key, String version) {
        Messages m = messages();
        for (UUID id : List.copyOf(s.builders)) {
            Player builder = Bukkit.getPlayer(id);
            if (builder == null) {
                s.builders.remove(id);
                continue;
            }
            exit(builder);
            if (by == null || !builder.equals(by)) {
                m.send(builder, key, tag("id", s.id), tag("version", version), tag("player", by == null ? "?" : by.getName()));
            }
        }
        drafts.remove(s.id);
        saveDrafts();
    }

    /**
     * Throws a draft away: an existing template's build goes back to the playable version, a new arena's plot is
     * cleared. Everyone building in it leaves.
     */
    public void discard(Player by, EditorSession s) {
        ArenaTemplate t = templates().get(s.id);
        World w = plugin.arenaWorld().world();
        if (t != null && t.editorPlot() == s.plot) {
            Structure structure = templates().structure(t.id());
            if (structure != null) {
                structure.place(new Location(w, t.editorX(), t.editorY(), t.editorZ()), false, StructureRotation.NONE,
                        Mirror.NONE, 0, 1f, new Random());
            }
        } else {
            clearPlot(s.plot);
        }
        close(s, by, "editor-discarded-other", "");
        plugin.getLogger().info(by.getName() + " discarded the arena draft '" + s.id + "'.");
        messages().send(by, "editor-discarded", tag("id", s.id));
    }

    /** The template was deleted: its draft goes too (builders leave, plot is cleared). */
    public void templateDeleted(Player by, String id) {
        EditorSession s = drafts.get(id);
        if (s != null) {
            close(s, by, "editor-discarded-other", "");
            clearPlot(s.plot);
        }
    }

    /** Server stop: everyone leaves the editor (their own state comes back through StateKeeper), drafts stay. */
    public void closeAll() {
        for (EditorSession s : drafts.values()) {
            s.builders.clear();
        }
        sessions.clear();
        saveDrafts();
    }

    private int freePlot() {
        int plot = templates().freePlot();
        while (true) {
            int p = plot;
            if (drafts.values().stream().noneMatch(s -> s.plot == p)) {
                return plot;
            }
            plot++;
        }
    }

    /** Removes everything a never saved build left in a plot. */
    public void clearPlot(int plot) {
        Location o = plotOrigin(plot);
        int width = plugin.settings().arenas().maxWidth() + 16;
        int height = plugin.settings().arenas().maxHeight() + 16;
        World w = o.getWorld();
        for (int x = -8; x < width; x++) {
            for (int z = -8; z < width; z++) {
                for (int y = -8; y < height; y++) {
                    Block block = w.getBlockAt(o.getBlockX() + x, o.getBlockY() + y, o.getBlockZ() + z);
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /** Clears the build of a deleted template in the editor area. */
    public void clearSource(ArenaTemplate t) {
        if (t.editorPlot() < 0) {
            return;
        }
        World w = plugin.arenaWorld().world();
        for (int x = 0; x < t.sizeX(); x++) {
            for (int y = 0; y < t.sizeY(); y++) {
                for (int z = 0; z < t.sizeZ(); z++) {
                    Block block = w.getBlockAt(t.editorX() + x, t.editorY() + y, t.editorZ() + z);
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private static boolean looksEmpty(Location origin, ArenaTemplate t) {
        World w = origin.getWorld();
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 4; j++) {
                for (int k = 0; k <= 2; k++) {
                    if (!w.getBlockAt(origin.getBlockX() + i * (t.sizeX() - 1) / 4, origin.getBlockY() + k * (t.sizeY() - 1) / 2,
                            origin.getBlockZ() + j * (t.sizeZ() - 1) / 4).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Location absolute(Location origin, ArenaTemplate.Spawn s) {
        return new Location(origin.getWorld(), origin.getX() + s.x(), origin.getY() + s.y(), origin.getZ() + s.z(),
                s.yaw(), s.pitch());
    }

    // ------------------------------------------------------------------ save

    /**
     * Publishes the draft as the new version of the template. Texts tell the player what is missing. Everyone
     * building in it leaves, the draft is gone.
     *
     * @return whether it was published
     */
    public boolean publish(Player player) {
        EditorSession s = session(player);
        if (s == null) {
            return false;
        }
        Messages m = messages();
        BlockVector min = s.min();
        BlockVector size = s.size();
        if (min == null) {
            m.send(player, "editor-error-selection");
            return false;
        }
        int maxWidth = plugin.settings().arenas().maxWidth();
        int maxHeight = plugin.settings().arenas().maxHeight();
        // An arena that is already larger (like the shipped XXL one) may stay that large.
        ArenaTemplate existing = plugin.templates().get(s.id());
        if (existing != null) {
            maxWidth = Math.max(maxWidth, Math.max(existing.sizeX(), existing.sizeZ()));
            maxHeight = Math.max(maxHeight, existing.sizeY());
        }
        if (size.getBlockX() > maxWidth || size.getBlockZ() > maxWidth || size.getBlockY() > maxHeight) {
            m.send(player, "editor-error-too-big", tag("size", format(size)), tag("width", maxWidth),
                    tag("height", maxHeight));
            return false;
        }
        if (size.getBlockX() < 5 || size.getBlockZ() < 5 || size.getBlockY() < 3) {
            m.send(player, "editor-error-too-small");
            return false;
        }
        if (s.spawn1 == null || s.spawn2 == null) {
            m.send(player, "editor-error-spawns");
            return false;
        }
        if (!inside(s.spawn1, min, size) || !inside(s.spawn2, min, size)) {
            m.send(player, "editor-error-spawn-outside");
            return false;
        }
        World w = plugin.arenaWorld().world();
        Location origin = new Location(w, min.getBlockX(), min.getBlockY(), min.getBlockZ());
        Structure structure = TemplateManager.capture(origin, size);
        // Switched on/off and pool size are set in the arena menu meanwhile - those of the playable version count.
        boolean enabled = existing != null ? existing.enabled() : s.enabled;
        int pool = existing != null ? existing.pool() : s.pool;
        ArenaTemplate t = new ArenaTemplate(s.id, s.name, s.icon, (existing != null ? existing.version() : s.version) + 1,
                size.getBlockX(), size.getBlockY(), size.getBlockZ(),
                relative(origin, s.spawn1), relative(origin, s.spawn2), s.allowBuild,
                s.maxBuildHeight <= 0 ? size.getBlockY() : Math.min(s.maxBuildHeight, size.getBlockY()),
                List.copyOf(s.kits), s.elytraOnly, enabled, pool, s.plot, min.getBlockX(), min.getBlockY(), min.getBlockZ());
        try {
            templates().save(t, structure);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arena template " + s.id, e);
            m.send(player, "editor-error-io", tag("error", e.getMessage()));
            return false;
        }
        plugin.slots().templateChanged(t.id());
        plugin.getLogger().info(player.getName() + " published arena template '" + t.id() + "' (version " + t.version()
                + ", " + format(size) + ").");
        close(s, player, "editor-published-other", String.valueOf(t.version()));
        m.send(player, "editor-saved", tag("id", t.id()), tag("version", t.version()), tag("size", format(size)));
        return true;
    }

    private static boolean inside(Location at, BlockVector min, BlockVector size) {
        return at.getX() >= min.getX() && at.getX() <= min.getX() + size.getX()
                && at.getY() >= min.getY() && at.getY() <= min.getY() + size.getY()
                && at.getZ() >= min.getZ() && at.getZ() <= min.getZ() + size.getZ();
    }

    private static ArenaTemplate.Spawn relative(Location origin, Location at) {
        return new ArenaTemplate.Spawn(at.getX() - origin.getX(), at.getY() - origin.getY(), at.getZ() - origin.getZ(),
                at.getYaw(), at.getPitch());
    }

    static String format(BlockVector size) {
        return size.getBlockX() + "×" + size.getBlockY() + "×" + size.getBlockZ();
    }

    // ------------------------------------------------------------------ tools

    private void giveTools(Player player) {
        for (Tool tool : Tool.values()) {
            player.getInventory().setItem(tool.slot, tool(tool));
        }
        player.getInventory().setHeldItemSlot(0);
    }

    /**
     * Puts tools back that are gone (thrown into the creative destroy slot ...) - without them nobody could save or
     * leave. A block lying in the tool's slot moves to a free place.
     */
    private void ensureTools(Player player) {
        EnumSet<Tool> present = EnumSet.noneOf(Tool.class);
        for (ItemStack item : player.getInventory().getContents()) {
            Tool tool = toolOf(item);
            if (tool != null) {
                present.add(tool);
            }
        }
        Tool onCursor = toolOf(player.getItemOnCursor());
        if (onCursor != null) {
            present.add(onCursor);
        }
        for (Tool tool : Tool.values()) {
            if (present.contains(tool)) {
                continue;
            }
            ItemStack existing = player.getInventory().getItem(tool.slot);
            player.getInventory().setItem(tool.slot, tool(tool));
            if (existing != null && !existing.isEmpty() && toolOf(existing) == null) {
                // Creative: a full inventory just loses the block, it can be taken again from the creative menu.
                player.getInventory().addItem(existing);
            }
        }
    }

    private ItemStack tool(Tool tool) {
        return tool(tool, tool.material, tool.key);
    }

    private ItemStack tool(Tool tool, Material material, String key) {
        ItemStack item = ItemStack.of(material);
        Messages m = messages();
        item.editMeta(meta -> {
            meta.displayName(m.item(key + "-name"));
            meta.lore(m.lore(key + "-lore"));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
        });
        return item;
    }

    private Tool toolOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String name = item.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        try {
            return name == null ? null : Tool.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        EditorSession s = session(player);
        if (s == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Tool tool = toolOf(event.getItem());
        if (tool == null) {
            return;
        }
        event.setCancelled(true);
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR;
        Messages m = messages();
        m.run(player, () -> {
            switch (tool) {
                case WAND -> {
                    Block block = event.getClickedBlock();
                    if (block == null) {
                        m.send(player, "editor-wand-block");
                        return;
                    }
                    BlockVector pos = new BlockVector(block.getX(), block.getY(), block.getZ());
                    if (left) {
                        s.pos1 = pos;
                    } else {
                        s.pos2 = pos;
                    }
                    BlockVector size = s.size();
                    m.send(player, left ? "editor-pos1" : "editor-pos2", tag("x", pos.getBlockX()),
                            tag("y", pos.getBlockY()), tag("z", pos.getBlockZ()),
                            tag("size", size == null ? "-" : format(size)));
                }
                case SPAWN1, SPAWN2 -> {
                    Location at = player.getLocation();
                    // Centre of the block, looking straight ahead: both players start evenly.
                    Location spawn = new Location(at.getWorld(), at.getBlockX() + 0.5, at.getBlockY(),
                            at.getBlockZ() + 0.5, Math.round(at.getYaw() / 45f) * 45f, 0);
                    if (tool == Tool.SPAWN1) {
                        s.spawn1 = spawn;
                    } else {
                        s.spawn2 = spawn;
                    }
                    m.send(player, tool == Tool.SPAWN1 ? "editor-spawn1" : "editor-spawn2");
                }
                case SETTINGS -> new TemplateSettingsMenu(plugin, player, s).open();
                case EXIT -> new EditorExitMenu(plugin, player, s).open();
            }
        });
    }

    /** Creative breaks a block instantly - with the wand in hand that must not happen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (session(event.getPlayer()) != null && toolOf(event.getPlayer().getInventory().getItemInMainHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (session(event.getPlayer()) != null && toolOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    /** Creative items must not get out: no ender chest, no other world. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && session(player) != null
                && event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            event.setCancelled(true);
            messages().send(player, "editor-no-enderchest");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (session(player) != null && !plugin.arenaWorld().is(event.getTo().getWorld())) {
            event.setCancelled(true);
            messages().send(player, "editor-leave-first");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // The draft stays; the own state comes back on the next login (StateKeeper).
        EditorSession s = sessions.remove(event.getPlayer().getUniqueId());
        if (s != null) {
            s.builders.remove(event.getPlayer().getUniqueId());
            s.changed = System.currentTimeMillis();
            saveDrafts();
        }
    }

    // ------------------------------------------------------------------ display

    private void show() {
        if (++runs >= AUTOSAVE_RUNS) {
            runs = 0;
            if (!sessions.isEmpty()) {
                long now = System.currentTimeMillis();
                sessions.values().forEach(s -> s.changed = now);
                saveDrafts();
            }
        }
        for (Map.Entry<UUID, EditorSession> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            EditorSession s = entry.getValue();
            if (player == null) {
                continue;
            }
            ensureTools(player);
            BlockVector min = s.min();
            BlockVector size = s.size();
            if (min != null) {
                outline(player, min, size);
            }
            marker(player, s.spawn1, Color.RED);
            marker(player, s.spawn2, Color.fromRGB(0x3c8cff));
            messages().run(player, () -> player.sendActionBar(messages().get("editor-status", tag("id", s.id),
                    tag("size", size == null ? "-" : format(size)),
                    tag("spawn1", messages().plain(s.spawn1 == null ? "editor-missing" : "editor-ok")),
                    tag("spawn2", messages().plain(s.spawn2 == null ? "editor-missing" : "editor-ok")))));
        }
    }

    private static void outline(Player player, BlockVector min, BlockVector size) {
        double x0 = min.getX();
        double y0 = min.getY();
        double z0 = min.getZ();
        double x1 = x0 + size.getX();
        double y1 = y0 + size.getY();
        double z1 = z0 + size.getZ();
        double longest = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
        double step = Math.max(1, longest / 48);
        Particle.DustOptions dust = new Particle.DustOptions(Color.ORANGE, 1.2f);
        for (double x = x0; x <= x1; x += step) {
            for (double[] yz : new double[][] {{y0, z0}, {y0, z1}, {y1, z0}, {y1, z1}}) {
                player.spawnParticle(Particle.DUST, x, yz[0], yz[1], 1, dust);
            }
        }
        for (double y = y0; y <= y1; y += step) {
            for (double[] xz : new double[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
                player.spawnParticle(Particle.DUST, xz[0], y, xz[1], 1, dust);
            }
        }
        for (double z = z0; z <= z1; z += step) {
            for (double[] xy : new double[][] {{x0, y0}, {x0, y1}, {x1, y0}, {x1, y1}}) {
                player.spawnParticle(Particle.DUST, xy[0], xy[1], z, 1, dust);
            }
        }
    }

    /** A column at the spawn and a short line in the direction the player will look. */
    private static void marker(Player player, Location spawn, Color color) {
        if (spawn == null || spawn.getWorld() != player.getWorld()) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
        for (double y = 0; y <= 2; y += 0.25) {
            player.spawnParticle(Particle.DUST, spawn.getX(), spawn.getY() + y, spawn.getZ(), 1, dust);
        }
        var direction = spawn.getDirection().setY(0).normalize();
        for (double d = 0.5; d <= 2; d += 0.25) {
            player.spawnParticle(Particle.DUST, spawn.getX() + direction.getX() * d, spawn.getY() + 1.6,
                    spawn.getZ() + direction.getZ() * d, 1, dust);
        }
    }
}
