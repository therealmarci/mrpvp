package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.snapshot.Snapshot;
import at.marcelr.mrpvp.snapshot.ToolbarBelts;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/**
 * A saved player state, read-only, laid out like the player inventory: main inventory, hotbar, armor, offhand and
 * the numbers - plus pages for the ender chest and MRtoolbar's belts. Nothing can be taken out (the menu cancels
 * every click), so this can never duplicate an item.
 */
public final class SnapshotMenu extends Menu {

    private enum Page { INVENTORY, ENDERCHEST, BELTS }

    private final Snapshot snapshot;
    private final Runnable back;
    private Page page = Page.INVENTORY;
    private ItemStack[] inventory;
    private ItemStack[] armor;
    private ItemStack[] offhand;
    private ItemStack[] enderchest;

    public SnapshotMenu(MRPvp plugin, Player viewer, Snapshot snapshot, Runnable back) {
        super(plugin, viewer, 6, plugin.messages().get(snapshot.phase() == Snapshot.Phase.JOIN
                ? "gui-snapshot-title-join" : "gui-snapshot-title-end", tag("player", snapshot.name())));
        this.snapshot = snapshot;
        this.back = back;
        try {
            inventory = ItemStack.deserializeItemsFromBytes(snapshot.inventory());
            armor = ItemStack.deserializeItemsFromBytes(snapshot.armor());
            offhand = ItemStack.deserializeItemsFromBytes(snapshot.offhand());
            enderchest = ItemStack.deserializeItemsFromBytes(snapshot.enderchest());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Snapshot " + snapshot.id() + " has unreadable items: " + e);
            inventory = armor = offhand = enderchest = new ItemStack[0];
        }
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        switch (page) {
            case INVENTORY -> drawInventory();
            case ENDERCHEST -> {
                for (int i = 0; i < enderchest.length && i < 27; i++) {
                    item(i, enderchest[i]);
                }
            }
            case BELTS -> drawBelts();
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        set(48, Items.of(Material.CHEST).glint(page == Page.INVENTORY).name(m.item("gui-snapshot-inventory")).build(),
                click -> show(Page.INVENTORY));
        set(49, Items.of(Material.ENDER_CHEST).glint(page == Page.ENDERCHEST).name(m.item("gui-snapshot-enderchest"))
                .lore(m.lore("gui-snapshot-enderchest-lore")).build(), click -> show(Page.ENDERCHEST));
        set(50, Items.of(Material.IRON_PICKAXE).flags().glint(page == Page.BELTS).name(m.item("gui-snapshot-belts")).build(),
                click -> show(Page.BELTS));
        set(53, Items.of(Material.CLOCK).name(m.item("gui-snapshot-meta", tag("id", snapshot.id())))
                .lore(m.lore("gui-snapshot-meta-lore", tag("taken", m.dateTime(snapshot.takenAt())),
                        tag("purpose", snapshot.purpose().name().toLowerCase(Locale.ROOT)),
                        tag("state", snapshot.phase() == Snapshot.Phase.END ? m.get("debug-snapshot-end")
                                : snapshot.restoredAt() == null ? m.get("debug-snapshot-owed")
                                : m.get("debug-snapshot-restored", tag("time", m.dateTime(snapshot.restoredAt())))))).build());
        fillRow(5);
    }

    private void show(Page next) {
        page = next;
        clickSound();
        render();
    }

    private void drawInventory() {
        var m = plugin.messages();
        // rows 0-2: main inventory (slots 9-35), row 3: hotbar (0-8)
        for (int i = 9; i < 36 && i < inventory.length; i++) {
            item(i - 9, inventory[i]);
        }
        for (int i = 0; i < 9 && i < inventory.length; i++) {
            item(27 + i, inventory[i]);
        }
        // armor is stored boots ... helmet - shown helmet first
        for (int i = 0; i < 4 && i < armor.length; i++) {
            item(36 + i, armor[3 - i]);
        }
        if (offhand.length > 0) {
            item(40, offhand[0]);
        }
        set(42, Items.of(Material.EXPERIENCE_BOTTLE).name(m.item("gui-snapshot-stats"))
                .lore(m.lore("gui-snapshot-stats-lore", tag("level", snapshot.level()),
                        tag("health", decimal(snapshot.health() / 2, 1)), tag("food", snapshot.food()),
                        tag("saturation", decimal(snapshot.saturation(), 1)), tag("mode",
                                snapshot.gameMode().toLowerCase(Locale.ROOT)))).build());
        List<PotionEffect> effects = effects();
        Items effectItem = Items.of(Material.POTION).flags().name(m.item("gui-snapshot-effects", tag("count", effects.size())));
        for (PotionEffect effect : effects) {
            effectItem.lore(m.item("gui-snapshot-effect", tag("effect", effect.getType().getKey().getKey()),
                    tag("level", effect.getAmplifier() + 1), tag("seconds", effect.getDuration() / 20)));
        }
        set(43, effectItem.build());
        set(44, Items.of(Material.COMPASS).name(m.item("gui-snapshot-position"))
                .lore(m.lore("gui-snapshot-position-lore", tag("world", snapshot.world()),
                        tag("x", Math.round(snapshot.x())), tag("y", Math.round(snapshot.y())),
                        tag("z", Math.round(snapshot.z())))).build());
    }

    private void drawBelts() {
        var m = plugin.messages();
        List<ToolbarBelts.Belt> belts = ToolbarBelts.read(snapshot.toolbarBelts());
        if (belts == null) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-snapshot-belts-none")).build());
            return;
        }
        // One row per tool slot: the number of the hotbar slot, then the tools (the selected one shines).
        for (int row = 0; row < belts.size() && row < 5; row++) {
            ToolbarBelts.Belt belt = belts.get(row);
            set(row * 9, Items.of(Material.ITEM_FRAME).amount(belt.slot() + 1)
                    .name(m.item("gui-snapshot-belt", tag("slot", belt.slot() + 1))).build());
            for (int i = 0; i < belt.items().size() && i < 8; i++) {
                ItemStack tool = belt.items().get(i);
                if (tool != null && !tool.isEmpty()) {
                    set(row * 9 + 1 + i, Items.copy(tool).glint(i == belt.selected()).build());
                }
            }
        }
    }

    private void item(int slot, ItemStack item) {
        if (item != null && !item.isEmpty()) {
            set(slot, item.clone());
        }
    }

    private List<PotionEffect> effects() {
        List<PotionEffect> result = new ArrayList<>();
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.loadFromString(snapshot.effects());
            List<?> list = yaml.getList("effects");
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof PotionEffect effect) {
                        result.add(effect);
                    }
                }
            }
        } catch (org.bukkit.configuration.InvalidConfigurationException ignored) {
            // shown as no effects
        }
        return result;
    }
}
