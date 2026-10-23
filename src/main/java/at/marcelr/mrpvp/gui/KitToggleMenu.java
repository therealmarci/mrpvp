package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.editor.EditorSession;
import at.marcelr.mrpvp.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Which kits may be used in the arena (none marked = all). */
public final class KitToggleMenu extends Menu {

    private final EditorSession session;

    public KitToggleMenu(MRPvp plugin, Player viewer, EditorSession session) {
        super(plugin, viewer, 6, plugin.messages().get("gui-template-kits"));
        this.session = session;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<Kit> kits = List.copyOf(plugin.kits().all());
        for (int i = 0; i < kits.size() && i < 45; i++) {
            Kit kit = kits.get(i);
            boolean on = session.kits().contains(kit.id());
            set(i, Items.of(kit.icon()).name(m.miniItem(kit.name())).glint(on).flags()
                    .lore(m.lore(on ? "gui-kit-toggle-on" : session.kits().isEmpty() ? "gui-kit-toggle-all"
                            : "gui-kit-toggle-off")).build(), click -> {
                if (on) {
                    session.kits().remove(kit.id());
                } else {
                    session.kits().add(kit.id());
                }
                clickSound();
                render();
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new TemplateSettingsMenu(plugin, viewer, session).open();
        });
        set(49, Items.of(Material.BUCKET).name(m.item("gui-kit-toggle-clear")).build(), click -> {
            session.kits().clear();
            clickSound();
            render();
        });
        fillRow(5);
    }
}
