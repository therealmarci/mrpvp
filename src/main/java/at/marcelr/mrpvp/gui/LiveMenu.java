package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Fighter;
import at.marcelr.mrpvp.game.Game;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.game.Spectators;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/** Running rounds with both players' health, live (the menu refreshes itself). Click: watch, shift-click: stop. */
public final class LiveMenu extends Menu {

    public LiveMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-live-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<Game> games = plugin.games().games();
        if (games.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-live-empty")).build());
        }
        for (int i = 0; i < games.size() && i < 45; i++) {
            Game game = games.get(i);
            set(i, Items.of(game.kit().icon()).flags()
                    .name(m.item("gui-live-entry", tag("id", game.id()), tag("one", game.one().name()),
                            tag("two", game.two().name())))
                    .lore(m.lore("gui-live-entry-lore", tag("state", m.get("gui-live-state-" + game.state().name()
                                    .toLowerCase(Locale.ROOT))),
                            tag("one", game.one().name()), tag("health1", health(game.one())),
                            tag("two", game.two().name()), tag("health2", health(game.two())),
                            tag("time", GameManager.clock(game.elapsed())), tag("kit", m.mini(game.kit().name())),
                            tag("arena", plugin.games().arenaName(game.arena().name())), tag("slot", game.arena().slot())))
                    .build(), click -> {
                if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                    if (allowed(AdminMenu.PERM_STOP)) {
                        new ConfirmMenu(plugin, viewer, m.item("gui-live-stop", tag("id", game.id())), () -> {
                            plugin.games().stop(game, viewer.getName());
                            new LiveMenu(plugin, viewer).open();
                        }, this::open).open();
                    }
                } else if (allowed(Spectators.PERM_SPECTATE)) {
                    viewer.closeInventory();
                    plugin.spectators().watch(viewer, game);
                }
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new AdminMenu(plugin, viewer).open();
        });
        fillRow(5);
    }

    private String health(Fighter f) {
        Player player = Bukkit.getPlayer(f.uuid());
        return player == null ? "-" : decimal(player.getHealth() / 2, 1);
    }
}
