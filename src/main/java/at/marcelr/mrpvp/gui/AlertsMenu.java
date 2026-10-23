package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Monitor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.tag;

/** Conspicuous rounds of the last 24 hours; click opens the round. */
public final class AlertsMenu extends Menu {

    public AlertsMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-alerts-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<Monitor.Alert> alerts = plugin.monitor().recent();
        if (alerts.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-alerts-empty")).build());
        }
        for (int i = 0; i < alerts.size() && i < 45; i++) {
            Monitor.Alert a = alerts.get(i);
            Material icon = switch (a.type()) {
                case FARMING -> Material.WHEAT;
                case QUIT -> Material.OAK_DOOR;
                case BORDER -> Material.BARRIER;
                case HIT -> Material.NETHERITE_SWORD;
                case IDLE -> Material.CLOCK;
            };
            set(i, Items.of(icon).flags().name(m.item("mradmin-alert", tag("id", a.gameId()),
                            tag("type", m.plain("alert-" + a.type().name().toLowerCase(Locale.ROOT)))))
                    .lore(m.lore("mradmin-alert-lore", tag("one", a.one()), tag("two", a.two()),
                            tag("detail", a.detail()), tag("date", m.dateTime(a.time())))).build(), click -> {
                if (allowed(AdminMenu.PERM_GAMES)) {
                    clickSound();
                    new GameMenu(plugin, viewer, a.gameId(), true, this::open).open();
                }
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new AdminMenu(plugin, viewer).open();
        });
        fillRow(5);
    }
}
