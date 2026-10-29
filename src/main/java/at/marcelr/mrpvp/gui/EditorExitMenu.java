package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static at.marcelr.mrpvp.Messages.tag;

/** Publish, leave (the draft stays under construction) or discard the draft. */
public final class EditorExitMenu extends Menu {

    private final EditorSession session;

    public EditorExitMenu(MRPvp plugin, Player viewer, EditorSession session) {
        super(plugin, viewer, 3, plugin.messages().get("gui-editor-exit-title", tag("id", session.id())));
        this.session = session;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        ArenaTemplate playable = plugin.templates().get(session.id());
        set(4, Items.of(Material.SCAFFOLDING).name(m.miniItem(session.name()))
                .lore(m.lore("gui-editor-info-lore", tag("id", session.id()),
                        tag("builders", ArenaIcon.builders(plugin, session)),
                        tag("playable", playable == null ? m.plain("gui-editor-info-new")
                                : m.plain("gui-editor-info-version", "version", playable.version())))).build());
        set(10, Items.of(Material.LIME_CONCRETE).name(m.item("gui-editor-publish"))
                .lore(m.lore("gui-editor-publish-lore")).build(), click -> {
            viewer.closeInventory();
            plugin.editor().publish(viewer);
        });
        set(12, Items.of(Material.OAK_DOOR).name(m.item("gui-editor-leave"))
                .lore(m.lore("gui-editor-leave-lore")).build(), click -> {
            viewer.closeInventory();
            plugin.editor().leave(viewer);
        });
        set(14, Items.of(Material.RED_CONCRETE).name(m.item("gui-editor-discard"))
                .lore(m.lore("gui-editor-discard-lore")).build(), click -> {
            clickSound();
            new ConfirmMenu(plugin, viewer, m.get("gui-editor-discard"), () -> {
                viewer.closeInventory();
                plugin.editor().discard(viewer, session);
            }, this::open).open();
        });
        set(16, Items.of(Material.BRICKS).name(m.item("gui-editor-continue")).build(), click -> viewer.closeInventory());
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }
}
