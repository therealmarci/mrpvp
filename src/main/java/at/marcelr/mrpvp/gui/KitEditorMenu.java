package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.kit.KitRules;
import at.marcelr.mrpvp.kit.KitSlot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Kit editor in the layout of the player inventory. It only ever works with copies: clicking a slot with an item
 * on the cursor puts a copy into the kit (the item stays on the cursor), clicking with an empty hand removes it
 * from the kit (nothing comes out). Shift-click in the own inventory copies an item into the next free place.
 * So the editor can never create or duplicate real items.
 */
public final class KitEditorMenu extends Menu {

    /** Layout: 0-26 inventory 9-35, 27-35 hotbar, 36-39 helmet..boots, 40 offhand. */
    private static final int EDIT_SLOTS = 41;

    /** The kit being edited (main thread only). */
    static final class Draft {
        final String id;
        String name;
        Material icon;
        List<String> description;
        String permission;
        KitRules rules = KitRules.NONE;
        final Map<KitSlot, ItemStack> items = new LinkedHashMap<>();
        /** Name and description in all languages - the editor changes only those of {@link #language}. */
        final Map<String, String> names = new LinkedHashMap<>();
        final Map<String, List<String>> descriptions = new LinkedHashMap<>();
        /** The language of whoever edits ("de", "en" ...). */
        final String language = at.marcelr.mrpvp.Messages.language().split("_", 2)[0];

        private Draft(String id) {
            this.id = id;
        }

        static Draft of(Kit kit) {
            Draft d = new Draft(kit.id());
            d.names.putAll(kit.names());
            d.descriptions.putAll(kit.descriptions());
            d.name = kit.name();
            d.icon = kit.icon();
            d.description = new ArrayList<>(kit.description());
            d.permission = kit.permission();
            d.rules = kit.rules();
            kit.items().forEach((slot, item) -> d.items.put(slot, item.clone()));
            return d;
        }

        static Draft empty(String id) {
            Draft d = new Draft(id);
            d.name = "<white>" + id;
            d.icon = Material.IRON_SWORD;
            d.description = new ArrayList<>();
            d.permission = "";
            return d;
        }

        Kit toKit() {
            return new Kit(id, withLanguage(names, name), icon, withLanguage(descriptions, List.copyOf(description)),
                    permission, rules, new LinkedHashMap<>(items));
        }

        /** The value replaces the one of the editor's language - or the one for every language if there is only that. */
        private <T> Map<String, T> withLanguage(Map<String, T> values, T value) {
            Map<String, T> result = new LinkedHashMap<>(values);
            if (result.isEmpty() || (result.size() == 1 && result.containsKey(Kit.ALL))) {
                result.clear();
                result.put(Kit.ALL, value);
            } else {
                result.put(language, value);
            }
            return result;
        }
    }

    private final Draft draft;
    private final boolean isNew;

    public KitEditorMenu(MRPvp plugin, Player viewer, Draft draft, boolean isNew) {
        super(plugin, viewer, 6, plugin.messages().get("gui-kiteditor-title", tag("id", draft.id)));
        this.draft = draft;
        this.isNew = isNew;
    }

    static KitSlot slotOf(int menuSlot) {
        if (menuSlot < 27) {
            return new KitSlot(menuSlot + 9, null);
        }
        if (menuSlot < 36) {
            return new KitSlot(menuSlot - 27, null);
        }
        return switch (menuSlot) {
            case 36 -> new KitSlot(-1, KitSlot.Special.HELMET);
            case 37 -> new KitSlot(-1, KitSlot.Special.CHESTPLATE);
            case 38 -> new KitSlot(-1, KitSlot.Special.LEGGINGS);
            case 39 -> new KitSlot(-1, KitSlot.Special.BOOTS);
            case 40 -> new KitSlot(-1, KitSlot.Special.OFFHAND);
            default -> null;
        };
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        for (int slot = 0; slot < EDIT_SLOTS; slot++) {
            KitSlot kitSlot = slotOf(slot);
            ItemStack item = draft.items.get(kitSlot);
            int s = slot;
            if (item != null) {
                set(slot, item.clone(), click -> edit(s, click));
            } else if (slot >= 36) {
                set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(m.item("gui-kiteditor-empty-" + kitSlot.special().name().toLowerCase(java.util.Locale.ROOT)))
                        .build(), click -> edit(s, click));
            } else {
                // Empty slots need an action too, so a click with an item on the cursor lands there.
                set(slot, null, click -> edit(s, click));
            }
        }
        set(42, Items.of(Material.NAME_TAG).name(m.item("gui-kiteditor-name"))
                .lore(m.lore("gui-template-name-lore", tag("name", m.mini(draft.name)))).build(), click ->
                TextPrompt.ask(plugin, viewer, "prompt-kit-name", draft.name, 64, value -> {
                    if (!value.isEmpty()) {
                        draft.name = value;
                    }
                    open();
                }, this::open));
        set(43, Items.of(draft.icon).flags().name(m.item("gui-template-icon")).lore(m.lore("gui-template-icon-lore")).build(),
                click -> {
                    Material hand = viewer.getInventory().getItemInMainHand().getType();
                    if (hand.isAir() || !hand.isItem()) {
                        m.send(viewer, "editor-icon-empty-hand");
                        return;
                    }
                    draft.icon = hand;
                    clickSound();
                    render();
                });
        boolean restricted = !draft.permission.isEmpty();
        set(44, Items.of(restricted ? Material.IRON_DOOR : Material.OAK_DOOR)
                .name(m.item(restricted ? "gui-kiteditor-permission-on" : "gui-kiteditor-permission-off",
                        tag("permission", "mrpvp.kit." + draft.id)))
                .lore(m.lore("gui-kiteditor-permission-lore")).build(), click -> {
            draft.permission = restricted ? "" : "mrpvp.kit." + draft.id;
            clickSound();
            render();
        });

        set(45, Items.of(Material.ARROW).name(m.item("gui-kiteditor-back")).build(), click -> {
            clickSound();
            new KitListMenu(plugin, viewer).open();
        });
        set(46, Items.of(Material.CHEST).name(m.item("gui-kiteditor-copy")).lore(m.lore("gui-kiteditor-copy-lore")).build(),
                click -> {
                    copyInventory();
                    clickSound();
                    render();
                });
        set(47, Items.of(Material.BUCKET).name(m.item("gui-kiteditor-clear")).build(), click -> {
            draft.items.clear();
            clickSound();
            render();
        });
        set(49, Items.of(Material.WRITABLE_BOOK).name(m.item("gui-kiteditor-description"))
                .lore(m.lore("gui-kiteditor-description-lore", tag("text", String.join(" | ", draft.description)))).build(),
                click -> TextPrompt.ask(plugin, viewer, "prompt-kit-description", String.join(" | ", draft.description), 200,
                        value -> {
                            draft.description = new ArrayList<>(Arrays.stream(value.split("\\|"))
                                    .map(String::trim).filter(s -> !s.isEmpty()).map(s -> "<gray>" + s).toList());
                            open();
                        }, this::open));
        if (!isNew) {
            set(51, Items.of(Material.LAVA_BUCKET).name(m.item("gui-kiteditor-delete")).build(), click ->
                    new ConfirmMenu(plugin, viewer, m.item("gui-kiteditor-delete-confirm", tag("id", draft.id)), () -> {
                        try {
                            plugin.kits().delete(draft.id);
                            m.send(viewer, "kit-deleted", tag("id", draft.id));
                            plugin.getLogger().info(viewer.getName() + " deleted kit '" + draft.id + "'.");
                        } catch (IOException e) {
                            m.send(viewer, "editor-error-io", tag("error", e.getMessage()));
                        }
                        new KitListMenu(plugin, viewer).open();
                    }, this::open).open());
        }
        set(53, Items.of(Material.LIME_CONCRETE).name(m.item("gui-kiteditor-save"))
                .lore(m.lore("gui-kiteditor-save-lore", tag("count", draft.items.size()))).build(), click -> save());
        set(50, Items.of(Material.COMPARATOR).name(m.item("gui-kiteditor-rules"))
                .lore(m.lore("gui-kiteditor-rules-lore")).build(), click -> {
            clickSound();
            new KitRulesMenu(plugin, viewer, draft, this::open).open();
        });
        set(48, Items.of(Material.KNOWLEDGE_BOOK).name(m.item("gui-kiteditor-help")).lore(m.lore("gui-kiteditor-help-lore")).build());
        fillRow(5);
        set(41, Items.filler());
    }

    private void edit(int slot, ClickType click) {
        KitSlot kitSlot = slotOf(slot);
        ItemStack cursor = viewer.getItemOnCursor();
        if (!cursor.isEmpty()) {
            ItemStack copy = clean(cursor);
            if (click == ClickType.RIGHT) {
                copy.setAmount(1);
            }
            draft.items.put(kitSlot, copy);
        } else if (click == ClickType.RIGHT && draft.items.get(kitSlot) instanceof ItemStack item && item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            draft.items.remove(kitSlot);
        }
        clickSound();
        render();
    }

    /** Shift-click in the own inventory: a copy into the right armor slot or the next free place. */
    @Override
    protected void clickBottom(int slot, ClickType type) {
        if (!type.isShiftClick()) {
            return;
        }
        ItemStack item = viewer.getInventory().getItem(slot);
        if (item == null || item.isEmpty()) {
            return;
        }
        KitSlot target = armorSlot(item.getType());
        if (target == null || draft.items.containsKey(target)) {
            target = null;
            // hotbar first, then the inventory
            for (int s = 27; s < 36 && target == null; s++) {
                if (!draft.items.containsKey(slotOf(s))) {
                    target = slotOf(s);
                }
            }
            for (int s = 0; s < 27 && target == null; s++) {
                if (!draft.items.containsKey(slotOf(s))) {
                    target = slotOf(s);
                }
            }
        }
        if (target != null) {
            draft.items.put(target, clean(item));
            clickSound();
            render();
        }
    }

    private static KitSlot armorSlot(Material type) {
        String name = type.name();
        if (name.endsWith("_HELMET")) {
            return new KitSlot(-1, KitSlot.Special.HELMET);
        }
        if (name.endsWith("_CHESTPLATE") || type == Material.ELYTRA) {
            return new KitSlot(-1, KitSlot.Special.CHESTPLATE);
        }
        if (name.endsWith("_LEGGINGS")) {
            return new KitSlot(-1, KitSlot.Special.LEGGINGS);
        }
        if (name.endsWith("_BOOTS")) {
            return new KitSlot(-1, KitSlot.Special.BOOTS);
        }
        return type == Material.SHIELD ? new KitSlot(-1, KitSlot.Special.OFFHAND) : null;
    }

    /** "Take over my inventory": copies of everything the admin carries, in the same places. */
    private void copyInventory() {
        draft.items.clear();
        PlayerInventory inv = viewer.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.isEmpty()) {
                draft.items.put(new KitSlot(i, null), clean(item));
            }
        }
        putIf(KitSlot.Special.HELMET, inv.getHelmet());
        putIf(KitSlot.Special.CHESTPLATE, inv.getChestplate());
        putIf(KitSlot.Special.LEGGINGS, inv.getLeggings());
        putIf(KitSlot.Special.BOOTS, inv.getBoots());
        putIf(KitSlot.Special.OFFHAND, inv.getItemInOffHand());
    }

    private void putIf(KitSlot.Special special, ItemStack item) {
        if (item != null && !item.isEmpty()) {
            draft.items.put(new KitSlot(-1, special), clean(item));
        }
    }

    /** A copy without MRpvp's kit marker (that is set again for each round). */
    private ItemStack clean(ItemStack item) {
        ItemStack copy = item.clone();
        copy.editPersistentDataContainer(pdc -> pdc.remove(new NamespacedKey(plugin, "kit")));
        return copy;
    }

    private void save() {
        var m = plugin.messages();
        // Without items only with own rules (sumo: bare hands, only knockback).
        if (draft.items.isEmpty() && draft.rules.isDefault()) {
            m.send(viewer, "kit-empty");
            return;
        }
        try {
            plugin.kits().save(draft.toKit());
            m.send(viewer, "kit-saved", tag("id", draft.id), tag("count", draft.items.size()));
            plugin.getLogger().info(viewer.getName() + " saved kit '" + draft.id + "'.");
            successSound();
            new KitListMenu(plugin, viewer).open();
        } catch (IOException e) {
            m.send(viewer, "editor-error-io", tag("error", e.getMessage()));
        }
    }
}
