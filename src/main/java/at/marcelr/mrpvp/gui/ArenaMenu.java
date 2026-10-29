package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.io.IOException;

import static at.marcelr.mrpvp.Messages.tag;

/** One arena template: edit, enable, pool size, rebuild, delete. */
public final class ArenaMenu extends Menu {

    private final String id;

    public ArenaMenu(MRPvp plugin, Player viewer, String id) {
        super(plugin, viewer, 3, plugin.messages().get("gui-arena-title", tag("id", id)));
        this.id = id;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        ArenaTemplate t = plugin.templates().get(id);
        if (t == null) {
            set(13, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-arena-gone")).build(),
                    click -> new ArenaListMenu(plugin, viewer).open());
            return;
        }
        set(4, ArenaIcon.admin(plugin, t).build());
        EditorSession draft = plugin.editor().draft(id);
        if (draft == null) {
            set(10, Items.of(Material.BRUSH).name(m.item("gui-arena-edit")).lore(m.lore("gui-arena-edit-lore")).build(),
                    click -> {
                        viewer.closeInventory();
                        plugin.editor().edit(viewer, t);
                    });
        } else {
            set(10, Items.of(Material.SCAFFOLDING).name(m.item("gui-arena-join"))
                    .lore(m.lore("gui-arena-join-lore", tag("builders", ArenaIcon.builders(plugin, draft)))).build(), click -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    new ConfirmMenu(plugin, viewer, m.item("gui-arenas-draft-discard", tag("id", id)), () -> {
                        plugin.editor().discard(viewer, draft);
                        open();
                    }, this::open).open();
                    return;
                }
                viewer.closeInventory();
                plugin.editor().join(viewer, draft);
            });
        }
        set(12, Items.of(t.enabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(m.item(t.enabled() ? "gui-arena-enabled-name" : "gui-arena-disabled-name"))
                .lore(m.lore("gui-arena-toggle-lore")).build(), click -> {
            write(t.withEnabled(!t.enabled()));
            clickSound();
            render();
        });
        set(13, Items.of(Material.HOPPER).amount(Math.max(1, t.pool())).name(m.item("gui-arena-pool", tag("pool", t.pool())))
                .lore(m.lore("gui-arena-pool-lore")).build(), click -> {
            boolean less = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
            write(t.withPool(Math.clamp(t.pool() + (less ? -1 : 1), 0, 16)));
            clickSound();
            render();
        });
        set(14, Items.of(Material.ANVIL).name(m.item("gui-arena-rebuild")).lore(m.lore("gui-arena-rebuild-lore")).build(),
                click -> {
                    plugin.slots().rebuild(id);
                    successSound();
                    m.send(viewer, "arena-rebuild-queued", tag("id", id));
                });
        set(16, Items.of(Material.LAVA_BUCKET).name(m.item("gui-arena-delete")).lore(m.lore("gui-arena-delete-lore")).build(),
                click -> new ConfirmMenu(plugin, viewer, m.item("gui-arena-delete-confirm", tag("id", id)), () -> {
                    plugin.editor().templateDeleted(viewer, id);
                    plugin.editor().clearSource(t);
                    plugin.templates().delete(id);
                    plugin.slots().templateChanged(id);
                    plugin.getLogger().info(viewer.getName() + " deleted arena template '" + id + "'.");
                    m.send(viewer, "arena-deleted", tag("id", id));
                    new ArenaListMenu(plugin, viewer).open();
                }, this::open).open());
        set(18, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new ArenaListMenu(plugin, viewer).open();
        });
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }

    private void write(ArenaTemplate changed) {
        try {
            plugin.templates().write(changed);
            plugin.slots().templateChanged(changed.id());
        } catch (IOException e) {
            plugin.messages().send(viewer, "editor-error-io", tag("error", e.getMessage()));
        }
    }
}
