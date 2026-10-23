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

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * The arena editor. An admin's own state is saved (like before a round), they get creative mode and a tool hotbar
 * and build in their own plot of the editor area. Every template keeps its plot - the build there is the source
 * of the template, so changing it later starts from exactly what was saved.
 */
public final class EditorManager implements Listener {

    public static final String PERM_ARENA = "mrpvp.admin.arena";

    /** The editor area lies far away from the slots. */
    private static final int EDITOR_Z = -20000;
    private static final int BASE_Y = 100;
    private static final int PARTICLE_TICKS = 10;

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
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private BukkitTask task;

    public EditorManager(MRPvp plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "editor-tool");
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

    // ------------------------------------------------------------------ enter / leave

    /** Opens the editor for a new template with the given id. */
    public void create(Player player, String id) {
        if (templates().get(id) != null || sessions.values().stream().anyMatch(s -> s.id.equals(id))) {
            messages().send(player, "editor-id-taken", tag("id", id));
            return;
        }
        EditorSession session = new EditorSession(player.getUniqueId(), id, true, freePlot());
        session.name = "<white>" + id;
        session.icon = Material.STONE_BRICKS.name();
        session.pool = plugin.settings().arenas().pool();
        Location origin = plotOrigin(session.plot);
        if (!enter(player, session)) {
            return;
        }
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
        messages().send(player, "editor-started-new", tag("id", id));
    }

    /** Opens the editor for an existing template. */
    public void edit(Player player, ArenaTemplate t) {
        if (sessions.values().stream().anyMatch(s -> s.id.equals(t.id()))) {
            messages().send(player, "editor-in-use", tag("id", t.id()));
            return;
        }
        int plot = t.editorPlot() >= 0 ? t.editorPlot() : freePlot();
        EditorSession session = new EditorSession(player.getUniqueId(), t.id(), false, plot);
        session.name = t.name();
        session.icon = t.icon();
        session.allowBuild = t.allowBuild();
        session.elytraOnly = t.elytraOnly();
        session.maxBuildHeight = t.maxBuildHeight();
        session.kits = new ArrayList<>(t.kits());
        session.pool = t.pool();
        session.enabled = t.enabled();
        session.version = t.version();
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
        if (looksEmpty(origin, t)) {
            // The source build is gone (new world, plot never used): start from the saved blocks.
            Structure structure = templates().structure(t.id());
            if (structure != null) {
                structure.place(origin, false, StructureRotation.NONE, Mirror.NONE, 0, 1f, new Random());
            }
        }
        player.teleport(session.spawn1);
        messages().send(player, "editor-started", tag("id", t.id()));
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
        player.setAllowFlight(true);
        player.setFlying(true);
        giveTools(player);
        return true;
    }

    /** Leaves the editor: own state back (inventory, position ...). */
    public void leave(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            plugin.stateKeeper().leave(player);
        }
    }

    /** Leaves without saving: an existing template's build is put back to the saved state. */
    public void discard(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        ArenaTemplate t = templates().get(session.id);
        World w = plugin.arenaWorld().world();
        if (t != null && t.editorPlot() >= 0) {
            Structure structure = templates().structure(t.id());
            if (structure != null) {
                structure.place(new Location(w, t.editorX(), t.editorY(), t.editorZ()), false, StructureRotation.NONE,
                        Mirror.NONE, 0, 1f, new Random());
            }
        } else if (t == null) {
            clearPlot(session.plot);
        }
        leave(player);
        messages().send(player, "editor-discarded");
    }

    /** Server stop: everyone leaves the editor (their own state comes back through StateKeeper). */
    public void closeAll() {
        sessions.clear();
    }

    private int freePlot() {
        int plot = templates().freePlot();
        while (true) {
            int p = plot;
            if (sessions.values().stream().noneMatch(s -> s.plot == p)) {
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
     * Saves the template from the selection. Texts tell the player what is missing.
     *
     * @return whether it was saved
     */
    public boolean save(Player player) {
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
        ArenaTemplate t = new ArenaTemplate(s.id, s.name, s.icon, s.version + 1,
                size.getBlockX(), size.getBlockY(), size.getBlockZ(),
                relative(origin, s.spawn1), relative(origin, s.spawn2), s.allowBuild,
                s.maxBuildHeight <= 0 ? size.getBlockY() : Math.min(s.maxBuildHeight, size.getBlockY()),
                List.copyOf(s.kits), s.elytraOnly, s.enabled, s.pool, s.plot, min.getBlockX(), min.getBlockY(), min.getBlockZ());
        try {
            templates().save(t, structure);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arena template " + s.id, e);
            m.send(player, "editor-error-io", tag("error", e.getMessage()));
            return false;
        }
        s.version = t.version();
        s.isNew = false;
        plugin.slots().templateChanged(t.id());
        plugin.getLogger().info(player.getName() + " saved arena template '" + t.id() + "' (version " + t.version()
                + ", " + format(size) + ").");
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
        // Unsaved changes stay in the plot; the own state comes back on the next login (StateKeeper).
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ display

    private void show() {
        for (EditorSession s : sessions.values()) {
            Player player = Bukkit.getPlayer(s.player);
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
