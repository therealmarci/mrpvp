package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Fighter;
import at.marcelr.mrpvp.game.Game;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.game.Spectators;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Controls one running duel (admin menu → Live → a round). Layout (4 rows):
 * <pre>
 *  . . . . [info] . . . .
 *  . . [to one] . [watch] . [to two] . .
 *  . . [one wins] . [pause/resume] . [two wins] . .
 *  [back] . . . [stop] . . . .
 * </pre>
 * Watching needs mrpvp.admin.spectate, pausing mrpvp.admin.pause, a winner or stopping mrpvp.admin.stop. Ending the
 * round asks first.
 */
public final class LiveGameMenu extends Menu {

    private final long id;

    public LiveGameMenu(MRPvp plugin, Player viewer, long id) {
        super(plugin, viewer, 4, plugin.messages().get("gui-livegame-title", tag("id", id)));
        this.id = id;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        Game game = plugin.games().game(id);
        if (game == null || game.state() == Game.State.ENDING) {
            // Over meanwhile: back to the list on the next tick, not while drawing.
            Bukkit.getScheduler().runTask(plugin, () -> m.run(viewer, () -> new LiveMenu(plugin, viewer).open()));
            return;
        }
        Fighter one = game.one();
        Fighter two = game.two();
        String state = game.adminPause() != null ? "admin_paused" : game.state().name().toLowerCase(Locale.ROOT);
        set(4, Items.of(game.kit().icon()).flags()
                .name(m.item("gui-live-entry", tag("id", game.id()), tag("one", one.name()), tag("two", two.name())))
                .lore(m.lore("gui-livegame-info", tag("state", m.get("gui-live-state-" + state)),
                        tag("one", one.name()), tag("health1", LiveMenu.health(one)),
                        tag("two", two.name()), tag("health2", LiveMenu.health(two)),
                        tag("time", GameManager.clock(game.elapsed())), tag("kit", m.mini(game.kit().name())),
                        tag("arena", plugin.games().arenaName(game.arena().name())), tag("slot", game.arena().slot()),
                        tag("round", game.match().round()), tag("admin", game.adminPause() == null ? "" : game.adminPause())))
                .build());

        set(11, head(one).name(m.item("gui-livegame-watch-at", tag("player", one.name())))
                .lore(m.lore("gui-livegame-watch-at-lore")).build(), click -> watch(game, one));
        set(13, Items.of(Material.ENDER_EYE).name(m.item("gui-livegame-watch"))
                .lore(m.lore("gui-livegame-watch-lore")).build(), click -> watch(game, null));
        set(15, head(two).name(m.item("gui-livegame-watch-at", tag("player", two.name())))
                .lore(m.lore("gui-livegame-watch-at-lore")).build(), click -> watch(game, two));

        winButton(20, game, one);
        if (game.adminPause() != null) {
            set(22, Items.of(Material.LIME_DYE).name(m.item("gui-livegame-resume"))
                    .lore(m.lore("gui-livegame-resume-lore", tag("admin", game.adminPause()))).build(), click -> {
                if (allowed(AdminMenu.PERM_PAUSE)) {
                    result(plugin.games().adminResume(game, viewer.getName()), "admin-resumed");
                }
            });
        } else {
            set(22, Items.of(Material.CLOCK).name(m.item("gui-livegame-pause"))
                    .lore(m.lore("gui-livegame-pause-lore")).build(), click -> {
                if (allowed(AdminMenu.PERM_PAUSE)) {
                    result(plugin.games().adminPause(game, viewer.getName()), "admin-paused");
                }
            });
        }
        winButton(24, game, two);

        set(27, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new LiveMenu(plugin, viewer).open();
        });
        set(31, Items.of(Material.TNT).name(m.item("gui-livegame-stop"))
                .lore(m.lore("gui-livegame-stop-lore")).build(), click -> {
            if (allowed(AdminMenu.PERM_STOP)) {
                new ConfirmMenu(plugin, viewer, m.item("gui-live-stop", tag("id", game.id())), () -> {
                    plugin.games().stop(game, viewer.getName());
                    new LiveMenu(plugin, viewer).open();
                }, this::open).open();
            }
        });
        for (int row = 0; row < 4; row++) {
            fillRow(row);
        }
    }

    /** The admin declares a winner: the match is over, the round counts as a win (no kill, no death). */
    private void winButton(int slot, Game game, Fighter winner) {
        var m = plugin.messages();
        set(slot, Items.of(Material.GOLDEN_SWORD).flags().name(m.item("gui-livegame-win", tag("player", winner.name())))
                .lore(m.lore("gui-livegame-win-lore", tag("player", winner.name()))).build(), click -> {
            if (allowed(AdminMenu.PERM_STOP)) {
                new ConfirmMenu(plugin, viewer, m.item("gui-livegame-win-confirm", tag("player", winner.name())), () -> {
                    plugin.games().adminWin(game, winner, viewer.getName());
                    new LiveMenu(plugin, viewer).open();
                }, this::open).open();
            }
        });
    }

    private void watch(Game game, Fighter at) {
        if (allowed(Spectators.PERM_SPECTATE)) {
            viewer.closeInventory();
            plugin.spectators().watchAt(viewer, game, at);
        }
    }

    private void result(String error, String done) {
        if (error != null) {
            plugin.messages().send(viewer, error);
            sound(Sound.ENTITY_VILLAGER_NO);
        } else {
            plugin.messages().send(viewer, done, tag("id", id));
            successSound();
        }
    }

    private static Items head(Fighter f) {
        Player online = Bukkit.getPlayer(f.uuid());
        return online != null ? Items.head(online) : Items.head(Bukkit.getOfflinePlayer(f.uuid()));
    }
}
