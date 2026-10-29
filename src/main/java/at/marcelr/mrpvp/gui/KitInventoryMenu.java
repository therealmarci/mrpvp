package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * While a kit is edited in the own inventory, /duel opens this instead of the main menu.
 * <pre>
 *  . . . . [kit] . . . .
 *  . . [done] . [icon] . [cancel] . .
 *  . . . . [close] . . . .
 * </pre>
 */
public final class KitInventoryMenu extends Menu {

    public KitInventoryMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 3, plugin.messages().get("gui-kitinv-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        KitInventoryEditor editor = plugin.kitEditor();
        KitEditorMenu.Draft draft = editor.draft(viewer);
        if (draft == null) {
            viewer.closeInventory();
            return;
        }
        set(4, Items.of(draft.icon).flags().name(m.item("gui-kiteditor-kit", tag("name", m.mini(draft.name))))
                .lore(m.lore("gui-kitinv-info", tag("id", draft.id))).build());
        set(11, Items.of(Material.LIME_CONCRETE).name(m.item("gui-kitinv-done")).lore(m.lore("gui-kitinv-done-lore")).build(), click -> {
            viewer.closeInventory();
            editor.finish(viewer, true);
        });
        set(13, Items.of(Material.ITEM_FRAME).name(m.item("gui-kitinv-icon")).lore(m.lore("gui-kitinv-icon-lore")).build(), click -> {
            editor.iconFromHand(viewer, draft);
            clickSound();
            render();
        });
        set(15, Items.of(Material.RED_CONCRETE).name(m.item("gui-kitinv-cancel")).lore(m.lore("gui-kitinv-cancel-lore")).build(),
                click -> new ConfirmMenu(plugin, viewer, m.item("gui-kitinv-cancel"), () -> {
                    viewer.closeInventory();
                    editor.finish(viewer, false);
                }, this::open).open());
        set(22, Items.of(Material.BARRIER).name(m.item("gui-kitinv-continue")).build(), click -> viewer.closeInventory());
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }
}
