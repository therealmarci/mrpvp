package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static at.marcelr.mrpvp.Messages.tag;

/** Save and/or leave the editor. */
public final class EditorExitMenu extends Menu {

    public EditorExitMenu(MRPvp plugin, Player viewer, EditorSession session) {
        super(plugin, viewer, 3, plugin.messages().get("gui-editor-exit-title", tag("id", session.id())));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(10, Items.of(Material.LIME_CONCRETE).name(m.item("gui-editor-save-leave")).build(), click -> {
            viewer.closeInventory();
            if (plugin.editor().save(viewer)) {
                plugin.editor().leave(viewer);
            }
        });
        set(12, Items.of(Material.EMERALD).name(m.item("gui-editor-save")).build(), click -> {
            viewer.closeInventory();
            plugin.editor().save(viewer);
        });
        set(14, Items.of(Material.RED_CONCRETE).name(m.item("gui-editor-discard"))
                .lore(m.lore("gui-editor-discard-lore")).build(), click -> {
            clickSound();
            new ConfirmMenu(plugin, viewer, m.get("gui-editor-discard"), () -> {
                viewer.closeInventory();
                plugin.editor().discard(viewer);
            }, this::open).open();
        });
        set(16, Items.of(Material.BRICKS).name(m.item("gui-editor-continue")).build(), click -> viewer.closeInventory());
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }
}
