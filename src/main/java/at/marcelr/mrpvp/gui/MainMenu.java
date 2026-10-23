package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Game;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.stats.StatsStore;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static at.marcelr.mrpvp.Messages.tag;

/** /duel - the start of everything for players. */
public final class MainMenu extends Menu {

    public MainMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 3, plugin.messages().get("gui-main-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        GameManager games = plugin.games();
        Game game = games.gameOf(viewer);
        if (game != null) {
            set(13, Items.of(Material.WHITE_BANNER).name(m.item("gui-main-surrender-name"))
                    .lore(m.lore("gui-main-surrender-lore", tag("id", game.id()))).build(), click -> {
                viewer.closeInventory();
                games.surrender(viewer);
            });
        } else if (plugin.spectators().watching(viewer)) {
            set(13, Items.of(Material.ENDER_EYE).name(m.item("gui-main-stop-watching")).build(), click -> {
                viewer.closeInventory();
                plugin.spectators().stop(viewer);
            });
        } else {
            set(10, Items.of(Material.IRON_SWORD).name(m.item("gui-main-challenge-name"))
                    .lore(m.lore("gui-main-challenge-lore")).flags().build(), click -> {
                clickSound();
                new PlayerSelectMenu(plugin, viewer, 0).open();
            });
            int open = games.invites().to(viewer.getUniqueId(), System.currentTimeMillis()).size();
            set(11, Items.of(open > 0 ? Material.BELL : Material.PAPER).amount(Math.max(1, open)).glint(open > 0)
                    .name(m.item("gui-main-invites-name", tag("count", open)))
                    .lore(m.lore(open > 0 ? "gui-main-invites-lore" : "gui-main-invites-none")).build(),
                    click -> {
                        if (open > 0) {
                            clickSound();
                            new InvitesMenu(plugin, viewer).open();
                        }
                    });
        }
        set(14, Items.head(viewer).name(m.item("gui-main-stats")).lore(m.lore("gui-main-stats-lore")).build(), click -> {
            clickSound();
            new StatsMenu(plugin, viewer, viewer.getUniqueId(), viewer.getName(), StatsStore.ALL, this::open).open();
        });
        set(15, Items.of(Material.GOLDEN_HELMET).name(m.item("gui-main-leaderboard"))
                .lore(m.lore("gui-main-leaderboard-lore")).flags().build(), click -> {
            clickSound();
            new LeaderboardMenu(plugin, viewer).open();
        });
        set(16, Items.of(Material.BOOK).name(m.item("gui-main-games")).lore(m.lore("gui-main-games-lore")).build(),
                click -> {
                    clickSound();
                    new GamesMenu(plugin, viewer, viewer.getUniqueId(), viewer.getName(), false, this::open).open();
                });
        if (viewer.hasPermission(AdminMenu.PERM_GUI)) {
            set(18, Items.of(Material.COMMAND_BLOCK).name(m.item("gui-main-admin")).build(), click -> {
                clickSound();
                new AdminMenu(plugin, viewer).open();
            });
        }
        set(26, Items.of(Material.BARRIER).name(m.item("gui-close")).build(), click -> viewer.closeInventory());
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }
}
