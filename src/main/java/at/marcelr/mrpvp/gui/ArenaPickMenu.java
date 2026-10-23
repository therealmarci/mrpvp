package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/** The map choice of {@link DuelSetupMenu}: random and every arena the chosen kit fits. */
final class ArenaPickMenu extends Menu {

    private final DuelSetupMenu setup;

    ArenaPickMenu(MRPvp plugin, Player viewer, DuelSetupMenu setup) {
        super(plugin, viewer, rows(setup.arenas(setup.kit()).size()), plugin.messages().get("gui-pick-map-title"));
        this.setup = setup;
    }

    private static int rows(int arenas) {
        return Math.clamp((arenas + 8) / 9 + 1, 2, 6);
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<String> arenas = setup.arenas(setup.kit());
        int last = getInventory().getSize() - 9;
        for (int i = 0; i < arenas.size() && i < last; i++) {
            String id = arenas.get(i);
            ArenaTemplate t = id == null ? null : plugin.templates().get(id);
            boolean chosen = Objects.equals(id, setup.arena());
            Items icon = t == null ? Items.of(Material.ENDER_EYE).name(m.item("gui-arena-random"))
                    : ArenaIcon.player(plugin, t);
            set(i, icon.glint(chosen).lore(m.lore(chosen ? "gui-setup-kit-chosen" : "gui-setup-kit-choose")).build(),
                    click -> {
                        setup.chooseArena(id);
                        clickSound();
                        setup.open();
                    });
        }
        set(last, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            setup.open();
        });
        fillRow(last / 9);
    }
}
