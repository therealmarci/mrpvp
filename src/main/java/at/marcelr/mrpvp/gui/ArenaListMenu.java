package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.arena.TemplateManager;
import at.marcelr.mrpvp.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.tag;

/** All arena templates, plus "new arena". */
public final class ArenaListMenu extends Menu {

    public ArenaListMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-arenas-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<ArenaTemplate> all = List.copyOf(plugin.templates().all());
        int slot = 0;
        for (ArenaTemplate t : all) {
            if (slot >= 45) {
                break;
            }
            Items icon = ArenaIcon.admin(plugin, t);
            set(slot++, icon.lore(m.lore("gui-arenas-click")).build(), click -> {
                clickSound();
                new ArenaMenu(plugin, viewer, t.id()).open();
            });
        }
        // New arenas under construction: not playable yet, only here.
        for (EditorSession s : plugin.editor().drafts()) {
            if (slot >= 45) {
                break;
            }
            if (plugin.templates().get(s.id()) != null) {
                continue;
            }
            set(slot++, ArenaIcon.draft(plugin, s).build(), click -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    new ConfirmMenu(plugin, viewer, m.item("gui-arenas-draft-discard", tag("id", s.id())), () -> {
                        plugin.editor().discard(viewer, s);
                        new ArenaListMenu(plugin, viewer).open();
                    }, this::open).open();
                    return;
                }
                viewer.closeInventory();
                plugin.editor().join(viewer, s);
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new AdminMenu(plugin, viewer).open();
        });
        set(49, Items.of(Material.WRITABLE_BOOK).name(m.item("gui-arenas-new"))
                .lore(m.lore("gui-arenas-new-lore")).build(), click -> askId(""));
        fillRow(5);
    }

    private void askId(String initial) {
        TextPrompt.ask(plugin, viewer, "prompt-arena-id", initial, 32, value -> {
            String id = value.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!TemplateManager.ID.matcher(id).matches()) {
                plugin.messages().send(viewer, "editor-id-invalid");
                askId(id);
                return;
            }
            plugin.editor().create(viewer, id);
        }, this::open);
    }
}
