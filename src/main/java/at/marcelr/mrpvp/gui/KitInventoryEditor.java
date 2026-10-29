package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.KitSlot;
import at.marcelr.mrpvp.snapshot.Snapshot;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Editing the items of a kit in the own inventory ("the inventory is the kit"). The admin's state is saved like before
 * a round (also the inventory of MRadmin's admin mode), they get creative mode and the kit's items in their places -
 * and edit them like any inventory, with the creative inventory, anvils, commands. "Done" takes the inventory over into
 * the kit (not saved yet - that is "save" in the kit menu), "cancel" leaves it as it was; both give the own state back.
 * Nothing can leave the editor: dropping, picking up and opening containers are off, and a quit simply gives the state
 * back on the next login (the changes are lost).
 */
public final class KitInventoryEditor implements Listener {

    /** A kit being edited in someone's inventory. */
    private record Session(KitEditorMenu.Draft draft, boolean isNew) {
    }

    private final MRPvp plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public KitInventoryEditor(MRPvp plugin) {
        this.plugin = plugin;
    }

    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Starts editing: own state away, creative mode, the kit's items into the inventory. */
    void start(Player player, KitEditorMenu.Draft draft, boolean isNew) {
        var m = plugin.messages();
        if (editing(player) || plugin.games().inGame(player.getUniqueId()) || plugin.stateKeeper().inside(player)
                || plugin.editor().session(player) != null) {
            m.send(player, "invite-error-busy-self");
            return;
        }
        if (plugin.stateKeeper().enter(player, Snapshot.Purpose.KIT, null, GameMode.CREATIVE) == null) {
            m.send(player, "state-save-failed");
            return;
        }
        sessions.put(player.getUniqueId(), new Session(draft, isNew));
        PlayerInventory inv = player.getInventory();
        draft.items.forEach((slot, item) -> put(inv, slot, item.clone()));
        m.send(player, "kit-inventory-started", tag("id", draft.id));
    }

    /** /duel kit &lt;action&gt;: done, cancel, icon (the item in the hand becomes the kit's icon). */
    public void command(Player player, String action) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.messages().send(player, "kit-inventory-none");
            return;
        }
        switch (action) {
            case "done" -> finish(player, true);
            case "cancel" -> finish(player, false);
            case "icon" -> iconFromHand(player, session.draft());
            default -> new KitInventoryMenu(plugin, player).open();
        }
    }

    /** Ends editing: with {@code keep} the inventory becomes the kit's items; the own state comes back. */
    void finish(Player player, boolean keep) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        KitEditorMenu.Draft draft = session.draft();
        if (keep) {
            draft.items.clear();
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < 36; i++) {
                take(draft, new KitSlot(i, null), inv.getItem(i));
            }
            take(draft, new KitSlot(-1, KitSlot.Special.HELMET), inv.getHelmet());
            take(draft, new KitSlot(-1, KitSlot.Special.CHESTPLATE), inv.getChestplate());
            take(draft, new KitSlot(-1, KitSlot.Special.LEGGINGS), inv.getLeggings());
            take(draft, new KitSlot(-1, KitSlot.Special.BOOTS), inv.getBoots());
            take(draft, new KitSlot(-1, KitSlot.Special.OFFHAND), inv.getItemInOffHand());
            draft.changed = true;
        }
        player.setItemOnCursor(null);
        plugin.stateKeeper().leave(player);
        plugin.messages().send(player, keep ? "kit-inventory-done" : "kit-inventory-cancelled", tag("id", draft.id),
                tag("count", draft.items.size()));
        new KitEditorMenu(plugin, player, draft, session.isNew()).open();
    }

    void iconFromHand(Player player, KitEditorMenu.Draft draft) {
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand.isAir() || !hand.isItem()) {
            plugin.messages().send(player, "editor-icon-empty-hand");
            return;
        }
        draft.icon = hand;
        draft.changed = true;
        plugin.messages().send(player, "kit-inventory-icon", tag("item", hand.getKey().getKey()));
    }

    KitEditorMenu.Draft draft(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session == null ? null : session.draft();
    }

    /** Server stop: everyone gets their state back (the changes in the inventory are lost). */
    public void closeAll() {
        for (UUID id : java.util.List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            sessions.remove(id);
            if (player != null) {
                plugin.stateKeeper().leave(player);
            }
        }
    }

    private static void put(PlayerInventory inv, KitSlot slot, ItemStack item) {
        if (slot.special() == null) {
            inv.setItem(slot.index(), item);
            return;
        }
        switch (slot.special()) {
            case HELMET -> inv.setHelmet(item);
            case CHESTPLATE -> inv.setChestplate(item);
            case LEGGINGS -> inv.setLeggings(item);
            case BOOTS -> inv.setBoots(item);
            case OFFHAND -> inv.setItemInOffHand(item);
        }
    }

    private void take(KitEditorMenu.Draft draft, KitSlot slot, ItemStack item) {
        if (item != null && !item.isEmpty()) {
            draft.items.put(slot, KitEditorMenu.clean(plugin, item));
        }
    }

    // ------------------------------------------------------------------ nothing leaves the editor

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && editing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        // Only MRpvp's own menus (and the own inventory) - no chests, ender chests, shulkers ...
        if (event.getPlayer() instanceof Player player && editing(player)
                && !(event.getInventory().getHolder(false) instanceof Menu)) {
            event.setCancelled(true);
            plugin.messages().send(player, "kit-inventory-no-containers");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        // The state stays owed and comes back on the next login (StateKeeper); the changes are lost.
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
