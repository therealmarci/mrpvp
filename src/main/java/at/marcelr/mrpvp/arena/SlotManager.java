package at.marcelr.mrpvp.arena;

import at.marcelr.mrpvp.ConfigUpdater;
import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Arenas from templates in a grid of slots in the arena world.
 * <p>
 * A slot is pasted once and kept {@link State#READY} (the pool), so a round starts with a teleport only. After a
 * round the template is pasted over the slot again - the structure contains the air too, so everything built,
 * broken or dropped is gone. Pasting is synchronous in Bukkit, so it runs one at a time from a queue, after the
 * chunks were loaded asynchronously. The slots are stored in slots.yml; after a restart every used slot is pasted
 * again before anyone gets in.
 */
public final class SlotManager implements ArenaProvider {

    public enum State {
        /** nothing there */
        EMPTY,
        /** queued or being pasted */
        PASTING,
        /** pasted, free for the next round */
        READY,
        /** a round is running */
        IN_USE,
        /** being removed (template deleted or changed size) */
        CLEARING
    }

    /** A place in the grid. Main thread only. */
    public static final class Slot {
        private final int index;
        /** template whose blocks stand there now (null = nothing) */
        private String template;
        /** template queued to be pasted there (state PASTING) */
        private String pending;
        private int version;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private State state = State.EMPTY;
        private long lastPasteMillis;

        Slot(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }

        public String template() {
            return template;
        }

        public State state() {
            return state;
        }

        public long lastPasteMillis() {
            return lastPasteMillis;
        }
    }

    /** First slot row lies this far from 0/0 (the platform and the builtin arenas are on z = 0). */
    private static final int GRID_Z = 2000;
    private static final int PER_ROW = 32;
    private static final int BASE_Y = 100;
    /** Falling this far below the arena counts as out. */
    private static final int FALL_MARGIN = 6;

    private final MRPvp plugin;
    private final TemplateManager templates;
    private final ArenaProvider fallback;
    private final Map<Integer, Slot> slots = new TreeMap<>();
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private final Random random = new Random();
    private final ArenaMap maps = new ArenaMap();
    private boolean busy;
    private BukkitTask task;

    public SlotManager(MRPvp plugin, TemplateManager templates, ArenaProvider fallback) {
        this.plugin = plugin;
        this.templates = templates;
        this.fallback = fallback;
    }

    private World world() {
        return plugin.arenaWorld().world();
    }

    private File file() {
        return new File(plugin.getDataFolder(), "slots.yml");
    }

    // ------------------------------------------------------------------ start / stop

    /** Reads slots.yml, queues every used slot for a fresh paste and fills the pools. */
    public void start() {
        load();
        for (Slot slot : slots.values()) {
            ArenaTemplate t = templates.get(slot.template);
            if (slot.state == State.EMPTY) {
                continue;
            }
            if (t == null) {
                enqueueClear(slot);
            } else {
                // Unknown what happened to it (crash, round running at shutdown): paste it again.
                enqueuePaste(slot, t);
            }
        }
        fillPools();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::work, 2L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
        save();
    }

    private void load() {
        slots.clear();
        File file = file();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("slots.yml is invalid, starting with empty slots: " + e.getMessage());
            return;
        }
        ConfigurationSection section = y.getConfigurationSection("slots");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int index = Integer.parseInt(key);
                ConfigurationSection s = section.getConfigurationSection(key);
                Slot slot = new Slot(index);
                slot.template = s.getString("template");
                slot.version = s.getInt("version");
                List<Integer> size = s.getIntegerList("size");
                if (size.size() == 3) {
                    slot.sizeX = size.get(0);
                    slot.sizeY = size.get(1);
                    slot.sizeZ = size.get(2);
                }
                slot.state = slot.template == null ? State.EMPTY : State.PASTING;
                slots.put(index, slot);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("slots.yml: slot " + key + " is invalid - skipped.");
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRpvp - arena slots in the arena world. Managed by the plugin, do not edit."));
        for (Slot slot : slots.values()) {
            if (slot.template == null) {
                continue;
            }
            String path = "slots." + slot.index;
            y.set(path + ".template", slot.template);
            y.set(path + ".version", slot.version);
            y.set(path + ".size", List.of(slot.sizeX, slot.sizeY, slot.sizeZ));
        }
        try {
            Files.writeString(file().toPath(), y.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write slots.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ ArenaProvider

    @Override
    public ArenaInstance acquire(String templateId, String kit) {
        if (world() == null) {
            return null;
        }
        List<ArenaTemplate> candidates = new ArrayList<>();
        if (templateId != null) {
            ArenaTemplate t = templates.get(templateId);
            if (t != null && t.enabled() && t.fits(plugin.kits().get(kit))) {
                candidates.add(t);
            }
        } else {
            templates.enabled().stream().filter(t -> t.fits(plugin.kits().get(kit))).forEach(candidates::add);
            // Random arena: prefer templates with a ready slot (no paste needed).
            Collections.shuffle(candidates, random);
            candidates.sort((a, b) -> Boolean.compare(readySlot(b) != null, readySlot(a) != null));
        }
        if (candidates.isEmpty()) {
            // No template at all: the built-in arena keeps duels possible.
            return templates.enabled().isEmpty() && templateId == null ? fallback.acquire(null, kit) : null;
        }
        for (ArenaTemplate t : candidates) {
            Slot slot = readySlot(t);
            if (slot == null) {
                slot = newSlot();
                if (slot == null) {
                    continue;
                }
                // Nothing ready: paste right now (one short lag instead of no round).
                if (!pasteNow(slot, t)) {
                    continue;
                }
            }
            slot.state = State.IN_USE;
            fillPools();
            return instance(slot, t);
        }
        return null;
    }

    @Override
    public void release(ArenaInstance arena) {
        if (arena.slot() < 0) {
            fallback.release(arena);
            return;
        }
        Slot slot = slots.get(arena.slot());
        if (slot == null) {
            return;
        }
        ArenaTemplate t = templates.get(slot.template);
        if (t == null || !t.enabled()) {
            enqueueClear(slot);
        } else {
            enqueuePaste(slot, t);
        }
    }

    @Override
    public int inUse() {
        return (int) slots.values().stream().filter(s -> s.state == State.IN_USE).count() + fallback.inUse();
    }

    private ArenaInstance instance(Slot slot, ArenaTemplate t) {
        Location origin = origin(slot.index);
        Location spawn1 = at(origin, t.spawn1());
        Location spawn2 = at(origin, t.spawn2());
        BoundingBox region = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + t.sizeX(), top(origin.getY() + t.sizeY()), origin.getZ() + t.sizeZ());
        return new ArenaInstance(t.id(), t.version(), slot.index, spawn1, spawn2, region,
                origin.getY() - FALL_MARGIN, t.allowBuild(), origin.getY() + t.maxBuildHeight());
    }

    /** Top of the space a round may use: the arena plus the configured air above, never over the world's top. */
    private double top(double arenaTop) {
        double limit = world() == null ? arenaTop + 20 : world().getMaxHeight() - 2;
        return Math.min(arenaTop + plugin.settings().arenas().headroom(), Math.max(arenaTop, limit));
    }

    private static Location at(Location origin, ArenaTemplate.Spawn s) {
        return new Location(origin.getWorld(), origin.getX() + s.x(), origin.getY() + s.y(), origin.getZ() + s.z(),
                s.yaw(), s.pitch());
    }

    /** Lowest corner of a slot. */
    public Location origin(int index) {
        int spacing = plugin.settings().arenas().spacing();
        return new Location(world(), (index % PER_ROW) * spacing, BASE_Y, GRID_Z + (index / PER_ROW) * spacing);
    }

    // ------------------------------------------------------------------ pool

    private Slot readySlot(ArenaTemplate t) {
        for (Slot slot : slots.values()) {
            if (slot.state == State.READY && t.id().equals(slot.template) && slot.version == t.version()) {
                return slot;
            }
        }
        return null;
    }

    private int readyCount(ArenaTemplate t) {
        return (int) slots.values().stream().filter(s -> (s.state == State.READY && t.id().equals(s.template))
                || (s.state == State.PASTING && t.id().equals(s.pending))).count();
    }

    private Slot newSlot() {
        int used = (int) slots.values().stream().filter(s -> s.state != State.EMPTY).count();
        if (used >= plugin.settings().arenas().maxSlots()) {
            return null;
        }
        for (int i = 0; ; i++) {
            Slot slot = slots.get(i);
            if (slot == null) {
                slot = new Slot(i);
                slots.put(i, slot);
                return slot;
            }
            if (slot.state == State.EMPTY) {
                return slot;
            }
        }
    }

    /** Queues new slots until every enabled template has its pool of ready slots. */
    public void fillPools() {
        for (ArenaTemplate t : templates.enabled()) {
            while (readyCount(t) < t.pool()) {
                Slot slot = newSlot();
                if (slot == null) {
                    return;
                }
                enqueuePaste(slot, t);
            }
        }
    }

    /**
     * A template was saved, enabled, disabled or deleted: free slots of it are pasted again (new version) or
     * removed, slots in use follow when their round ends.
     */
    public void templateChanged(String id) {
        ArenaTemplate t = templates.get(id);
        for (Slot slot : slots.values()) {
            if (!id.equals(slot.template) || slot.state != State.READY) {
                continue;
            }
            if (t == null || !t.enabled()) {
                enqueueClear(slot);
            } else if (slot.version != t.version()) {
                enqueuePaste(slot, t);
            }
        }
        if (t != null && t.enabled()) {
            // Too many ready ones after lowering the pool: remove the rest.
            int surplus = readyCount(t) - t.pool();
            for (Slot slot : slots.values()) {
                if (surplus <= 0) {
                    break;
                }
                if (id.equals(slot.template) && slot.state == State.READY) {
                    enqueueClear(slot);
                    surplus--;
                }
            }
        }
        fillPools();
    }

    /** All slots pasted again (admin button) - those in use after their round. */
    public void rebuild(String id) {
        ArenaTemplate t = templates.get(id);
        if (t == null) {
            return;
        }
        for (Slot slot : slots.values()) {
            if (id.equals(slot.template) && slot.state == State.READY) {
                enqueuePaste(slot, t);
            }
        }
    }

    public List<Slot> slots() {
        return List.copyOf(slots.values());
    }

    /** (ready, in use) slots of a template. */
    public int[] counts(String id) {
        int ready = 0;
        int inUse = 0;
        for (Slot slot : slots.values()) {
            if (id.equals(slot.template)) {
                if (slot.state == State.READY) {
                    ready++;
                } else if (slot.state == State.IN_USE) {
                    inUse++;
                }
            }
        }
        return new int[] {ready, inUse};
    }

    /**
     * The mini map of a template (for the web interface), read from a pasted copy of its current version - or from
     * its build in the editor area. Null if neither exists.
     */
    public Map<String, Object> map(ArenaTemplate t) {
        Location from = null;
        for (Slot slot : slots.values()) {
            if (t.id().equals(slot.template) && slot.version == t.version()
                    && (slot.state == State.READY || slot.state == State.IN_USE)) {
                from = origin(slot.index);
                break;
            }
        }
        if (from == null && t.editorPlot() >= 0 && world() != null) {
            from = new Location(world(), t.editorX(), t.editorY(), t.editorZ());
        }
        if (from == null) {
            return null;
        }
        return maps.get(t.id() + ":" + t.version(), from.getWorld(), from.getBlockX(), from.getBlockY(), from.getBlockZ(),
                t.sizeX(), t.sizeY(), t.sizeZ());
    }

    /** Whether the location lies in the slot grid (not in the editor area or at the platform). */
    public boolean inGrid(Location at) {
        return at.getZ() >= GRID_Z && at.getX() >= 0;
    }

    /** The region of the slot a location lies in (for fluids, explosions ...), or null. */
    public BoundingBox regionAt(Location at) {
        int spacing = plugin.settings().arenas().spacing();
        double rz = at.getZ() - GRID_Z;
        if (rz < 0 || at.getX() < 0) {
            return null;
        }
        int index = (int) (rz / spacing) * PER_ROW + (int) (at.getX() / spacing);
        Slot slot = slots.get(index);
        if (slot == null || slot.template == null) {
            return null;
        }
        Location o = origin(index);
        return new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + slot.sizeX, top(o.getY() + slot.sizeY),
                o.getZ() + slot.sizeZ);
    }

    // ------------------------------------------------------------------ pasting

    private void enqueuePaste(Slot slot, ArenaTemplate t) {
        slot.state = State.PASTING;
        slot.pending = t.id();
        String id = t.id();
        queue.add(() -> {
            ArenaTemplate current = templates.get(id);
            if (current == null) {
                enqueueClear(slot);
                busy = false;
                return;
            }
            loadChunks(slot, Math.max(slot.sizeX, current.sizeX()), Math.max(slot.sizeZ, current.sizeZ()))
                    .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (slot.state != State.PASTING) {
                            // taken for a round or queued otherwise in the meantime
                        } else if (error != null || !pasteNow(slot, current)) {
                            slot.pending = null;
                            slot.state = slot.template == null ? State.EMPTY : State.CLEARING;
                            if (slot.template != null) {
                                enqueueClear(slot);
                            }
                        }
                        save();
                        busy = false;
                    }));
        });
    }

    private void enqueueClear(Slot slot) {
        slot.state = State.CLEARING;
        queue.add(() -> loadChunks(slot, slot.sizeX, slot.sizeZ).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Queued for a paste again in the meantime: the paste overwrites it anyway.
                    if (slot.state == State.CLEARING) {
                        clear(slot, slot.sizeX, slot.sizeY, slot.sizeZ);
                        slot.template = null;
                        slot.state = State.EMPTY;
                        save();
                    }
                    busy = false;
                })));
    }

    /** One job at a time: the next one starts when the previous paste is done. */
    private void work() {
        if (busy || queue.isEmpty() || world() == null) {
            return;
        }
        busy = true;
        queue.poll().run();
    }

    private CompletableFuture<Void> loadChunks(Slot slot, int sizeX, int sizeZ) {
        Location o = origin(slot.index);
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int cx = o.getBlockX() >> 4; cx <= (o.getBlockX() + sizeX) >> 4; cx++) {
            for (int cz = o.getBlockZ() >> 4; cz <= (o.getBlockZ() + sizeZ) >> 4; cz++) {
                chunks.add(world().getChunkAtAsync(cx, cz));
            }
        }
        return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new));
    }

    /** Pastes the template into the slot now (main thread). False if its blocks could not be read. */
    private boolean pasteNow(Slot slot, ArenaTemplate t) {
        Structure structure = templates.structure(t.id());
        if (structure == null) {
            return false;
        }
        long start = System.nanoTime();
        Location origin = origin(slot.index);
        // Usually loaded already (queue); a paste for a round that cannot wait loads them here.
        for (int cx = origin.getBlockX() >> 4; cx <= (origin.getBlockX() + t.sizeX()) >> 4; cx++) {
            for (int cz = origin.getBlockZ() >> 4; cz <= (origin.getBlockZ() + t.sizeZ()) >> 4; cz++) {
                origin.getWorld().getChunkAt(cx, cz);
            }
        }
        removeEntities(slot, origin);
        // A different (larger) template stood here before: remove what the new one does not cover.
        if (slot.template != null && (slot.sizeX > t.sizeX() || slot.sizeY > t.sizeY() || slot.sizeZ > t.sizeZ())) {
            clear(slot, slot.sizeX, slot.sizeY, slot.sizeZ);
        }
        structure.place(origin, false, StructureRotation.NONE, Mirror.NONE, 0, 1f, random);
        slot.pending = null;
        slot.template = t.id();
        slot.version = t.version();
        slot.sizeX = t.sizeX();
        slot.sizeY = t.sizeY();
        slot.sizeZ = t.sizeZ();
        slot.state = State.READY;
        slot.lastPasteMillis = (System.nanoTime() - start) / 1_000_000;
        if (slot.lastPasteMillis > 250) {
            plugin.getLogger().info("Pasting arena '" + t.id() + "' into slot " + slot.index + " took "
                    + slot.lastPasteMillis + " ms - a smaller arena or a larger pool keeps rounds lag-free.");
        }
        return true;
    }

    private void clear(Slot slot, int sizeX, int sizeY, int sizeZ) {
        Location o = origin(slot.index);
        removeEntities(slot, o);
        World w = o.getWorld();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    Block block = w.getBlockAt(o.getBlockX() + x, o.getBlockY() + y, o.getBlockZ() + z);
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /** Dropped items, arrows, tridents, XP orbs ... - everything but players. */
    private void removeEntities(Slot slot, Location o) {
        int sx = Math.max(slot.sizeX, 1);
        int sz = Math.max(slot.sizeZ, 1);
        BoundingBox box = new BoundingBox(o.getX() - 8, o.getY() - 64, o.getZ() - 8, o.getX() + sx + 8,
                o.getY() + Math.max(slot.sizeY, 1) + 64, o.getZ() + sz + 8);
        for (Entity entity : o.getWorld().getNearbyEntities(box)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }
}
