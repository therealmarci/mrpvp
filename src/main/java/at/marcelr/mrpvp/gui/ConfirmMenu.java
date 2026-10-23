package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** "Really?" - for anything that cannot be undone. */
public final class ConfirmMenu extends Menu {

    private final Component question;
    private final Runnable yes;
    private final Runnable no;

    public ConfirmMenu(MRPvp plugin, Player viewer, Component question, Runnable yes, Runnable no) {
        super(plugin, viewer, 3, plugin.messages().get("gui-confirm-title"));
        this.question = question;
        this.yes = yes;
        this.no = no;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(4, Items.of(Material.PAPER).name(question).build());
        set(11, Items.of(Material.LIME_CONCRETE).name(m.item("gui-confirm-yes")).build(), click -> {
            successSound();
            yes.run();
        });
        set(15, Items.of(Material.RED_CONCRETE).name(m.item("gui-confirm-no")).build(), click -> {
            clickSound();
            no.run();
        });
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }
}
