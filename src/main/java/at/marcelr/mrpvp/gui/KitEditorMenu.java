package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.kit.KitRules;
import at.marcelr.mrpvp.kit.KitSlot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * One kit (admins): everything about it on one symmetric page. The items are not edited in this menu but in the own
 * inventory - "edit inventory" ({@link KitInventoryEditor}) saves the admin's state (also the one of MRadmin's admin
 * mode), gives creative mode and puts the kit into the real inventory. Nothing is stored before "save".
 * <pre>
 *  . . . . [kit] . . . .
 *  . [inventory] . [rules] . [name] . [description] .
 *  . . [icon] . [clear] . [permission] . .
 *  . . . [back][save][delete] . . .
 * </pre>
 */
public final class KitEditorMenu extends Menu {

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
        /** Not saved yet (a new kit, or changes since opening). */
        boolean changed;

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
            d.changed = true;
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
        super(plugin, viewer, 4, plugin.messages().get("gui-kiteditor-title", tag("id", draft.id)));
        this.draft = draft;
        this.isNew = isNew;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(4, Items.of(draft.icon).flags().name(m.item("gui-kiteditor-kit", tag("name", m.mini(draft.name))))
                .lore(m.lore(draft.changed ? "gui-kiteditor-kit-lore-changed" : "gui-kiteditor-kit-lore",
                        tag("id", draft.id), tag("count", draft.items.size()))).build());

        set(10, Items.of(Material.CHEST).name(m.item("gui-kiteditor-inventory"))
                .lore(m.lore("gui-kiteditor-inventory-lore", tag("count", draft.items.size()))).build(), click -> {
            clickSound();
            viewer.closeInventory();
            plugin.kitEditor().start(viewer, draft, isNew);
        });
        set(12, Items.of(Material.COMPARATOR).name(m.item("gui-kiteditor-rules"))
                .lore(m.lore("gui-kiteditor-rules-lore")).build(), click -> {
            clickSound();
            new KitRulesMenu(plugin, viewer, draft, this::open).open();
        });
        set(14, Items.of(Material.NAME_TAG).name(m.item("gui-kiteditor-name"))
                .lore(m.lore("gui-template-name-lore", tag("name", m.mini(draft.name)))).build(), click ->
                TextPrompt.ask(plugin, viewer, "prompt-kit-name", draft.name, 64, value -> {
                    if (!value.isEmpty()) {
                        draft.name = value;
                        draft.changed = true;
                    }
                    open();
                }, this::open));
        set(16, Items.of(Material.WRITABLE_BOOK).name(m.item("gui-kiteditor-description"))
                .lore(m.lore("gui-kiteditor-description-lore", tag("text", String.join(" | ", draft.description)))).build(),
                click -> TextPrompt.ask(plugin, viewer, "prompt-kit-description", String.join(" | ", draft.description), 200,
                        value -> {
                            draft.description = new ArrayList<>(Arrays.stream(value.split("\\|"))
                                    .map(String::trim).filter(s -> !s.isEmpty()).map(s -> "<gray>" + s).toList());
                            draft.changed = true;
                            open();
                        }, this::open));

        set(20, Items.of(draft.icon).flags().name(m.item("gui-kiteditor-icon")).lore(m.lore("gui-kiteditor-icon-lore")).build(),
                click -> {
                    nextIcon();
                    clickSound();
                    render();
                });
        set(22, Items.of(Material.BUCKET).name(m.item("gui-kiteditor-clear"))
                .lore(m.lore("gui-kiteditor-clear-lore", tag("count", draft.items.size()))).build(), click -> {
            if (draft.items.isEmpty()) {
                return;
            }
            new ConfirmMenu(plugin, viewer, m.item("gui-kiteditor-clear"), () -> {
                draft.items.clear();
                draft.changed = true;
                open();
            }, this::open).open();
        });
        boolean restricted = !draft.permission.isEmpty();
        set(24, Items.of(restricted ? Material.IRON_DOOR : Material.OAK_DOOR)
                .name(m.item(restricted ? "gui-kiteditor-permission-on" : "gui-kiteditor-permission-off",
                        tag("permission", "mrpvp.kit." + draft.id)))
                .lore(m.lore("gui-kiteditor-permission-lore")).build(), click -> {
            draft.permission = restricted ? "" : "mrpvp.kit." + draft.id;
            draft.changed = true;
            clickSound();
            render();
        });

        set(30, Items.of(Material.ARROW).name(m.item("gui-kiteditor-back")).build(), click -> {
            if (!draft.changed) {
                clickSound();
                new KitListMenu(plugin, viewer).open();
                return;
            }
            new ConfirmMenu(plugin, viewer, m.item("gui-kiteditor-back-confirm"), () -> new KitListMenu(plugin, viewer).open(),
                    this::open).open();
        });
        set(31, Items.of(Material.LIME_CONCRETE).name(m.item("gui-kiteditor-save"))
                .lore(m.lore("gui-kiteditor-save-lore", tag("count", draft.items.size()))).glint(draft.changed).build(),
                click -> save());
        if (isNew) {
            locked(32, m.item("gui-kiteditor-delete"));
        } else {
            set(32, Items.of(Material.LAVA_BUCKET).name(m.item("gui-kiteditor-delete")).build(), click ->
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
        for (int row = 0; row < 4; row++) {
            fillRow(row);
        }
    }

    /** The icon: the next kind of item in the kit (in the inventory editor also "icon = item in hand"). */
    private void nextIcon() {
        List<Material> kinds = draft.items.values().stream().map(ItemStack::getType).distinct().toList();
        if (kinds.isEmpty()) {
            return;
        }
        int at = kinds.indexOf(draft.icon);
        draft.icon = kinds.get((at + 1) % kinds.size());
        draft.changed = true;
    }

    /** A copy without MRpvp's kit marker (that is set again for each round). */
    static ItemStack clean(MRPvp plugin, ItemStack item) {
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
            draft.changed = false;
            m.send(viewer, "kit-saved", tag("id", draft.id), tag("count", draft.items.size()));
            plugin.getLogger().info(viewer.getName() + " saved kit '" + draft.id + "'.");
            successSound();
            new KitListMenu(plugin, viewer).open();
        } catch (IOException e) {
            m.send(viewer, "editor-error-io", tag("error", e.getMessage()));
        }
    }
}
