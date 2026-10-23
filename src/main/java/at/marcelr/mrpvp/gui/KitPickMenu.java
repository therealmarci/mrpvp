package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** The kit choice of {@link DuelSetupMenu}; a click chooses and goes back. */
final class KitPickMenu extends Menu {

    private final DuelSetupMenu setup;

    KitPickMenu(MRPvp plugin, Player viewer, DuelSetupMenu setup) {
        super(plugin, viewer, rows(setup.kits().size()), plugin.messages().get("gui-pick-kit-title"));
        this.setup = setup;
    }

    private static int rows(int kits) {
        return Math.clamp((kits + 8) / 9 + 1, 2, 6);
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<Kit> kits = setup.kits();
        int last = getInventory().getSize() - 9;
        for (int i = 0; i < kits.size() && i < last; i++) {
            Kit k = kits.get(i);
            boolean chosen = setup.kit() != null && setup.kit().id().equals(k.id());
            set(i, KitIcon.of(plugin, k).glint(chosen)
                    .lore(m.lore(chosen ? "gui-setup-kit-chosen" : "gui-setup-kit-choose")).build(), click -> {
                setup.choose(k);
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
