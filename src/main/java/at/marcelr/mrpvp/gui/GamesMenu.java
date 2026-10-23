package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.stats.GameQueries;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Finished rounds, newest first: a player's own ("my games"), another player's, or all of them (admins, with result
 * filter and search by Game-ID).
 */
public final class GamesMenu extends Menu {

    private static final int PER_PAGE = 45;

    /** null = all players (admin) */
    private final UUID player;
    private final boolean admin;
    private final Runnable back;
    private GameQueries.ResultFilter filter = GameQueries.ResultFilter.ALL;
    private int page;
    private List<GameQueries.GameRow> rows;

    /**
     * @param admin shows admin details (snapshots, all filters); players only ever see their own rounds
     */
    public GamesMenu(MRPvp plugin, Player viewer, UUID player, String name, boolean admin, Runnable back) {
        super(plugin, viewer, 6, player == null ? plugin.messages().get("gui-games-title-all")
                : plugin.messages().get("gui-games-title", tag("player", name)));
        this.player = player;
        this.admin = admin;
        this.back = back;
        reload();
    }

    private void reload() {
        rows = null;
        UUID p = player;
        GameQueries.ResultFilter f = filter;
        int offset = page * PER_PAGE;
        // One more than shown: tells whether there is a next page.
        load(c -> GameQueries.list(c, p, f, offset, PER_PAGE + 1), list -> rows = list);
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        if (rows == null) {
            set(22, loading());
        } else if (rows.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-games-empty")).build());
        } else {
            for (int i = 0; i < rows.size() && i < PER_PAGE; i++) {
                GameQueries.GameRow row = rows.get(i);
                set(i, icon(row).build(), click -> {
                    clickSound();
                    new GameMenu(plugin, viewer, row.id(), admin, this::open).open();
                });
            }
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        if (page > 0) {
            set(48, Items.of(Material.SPECTRAL_ARROW).name(m.item("gui-page-previous")).build(), click -> {
                page--;
                clickSound();
                reload();
                render();
            });
        }
        set(49, Items.of(Material.PAPER).name(m.item("gui-page-only", tag("page", page + 1))).build());
        if (rows != null && rows.size() > PER_PAGE) {
            set(50, Items.of(Material.SPECTRAL_ARROW).name(m.item("gui-page-next")).build(), click -> {
                page++;
                clickSound();
                reload();
                render();
            });
        }
        if (admin) {
            set(52, Items.of(Material.HOPPER).name(m.item("gui-games-filter",
                    tag("filter", m.get("gui-games-filter-" + filter.name().toLowerCase(Locale.ROOT)))))
                    .lore(m.lore("gui-top-switch-lore")).build(), click -> {
                GameQueries.ResultFilter[] all = GameQueries.ResultFilter.values();
                int step = click == ClickType.RIGHT ? all.length - 1 : 1;
                filter = all[(filter.ordinal() + step) % all.length];
                page = 0;
                clickSound();
                reload();
                render();
            });
            set(53, Items.of(Material.NAME_TAG).name(m.item("gui-games-search")).build(), click ->
                    TextPrompt.ask(plugin, viewer, "prompt-game-id", "", 12, value -> {
                        try {
                            long id = Long.parseLong(value.replace("#", "").trim());
                            new GameMenu(plugin, viewer, id, true, this::open).open();
                        } catch (NumberFormatException e) {
                            plugin.messages().send(viewer, "game-id-invalid");
                            open();
                        }
                    }, this::open));
        }
        fillRow(5);
    }

    private Items icon(GameQueries.GameRow row) {
        var m = plugin.messages();
        GameQueries.PlayerLine self = player == null ? null : row.of(player);
        String outcome = self != null && self.outcome() != null ? self.outcome()
                : "ABORTED".equals(row.result()) ? "ABORTED" : "DRAW".equals(row.result()) ? "DRAW" : "WIN";
        Material material = switch (outcome) {
            case "LOSS" -> Material.RED_STAINED_GLASS;
            case "DRAW" -> Material.YELLOW_STAINED_GLASS;
            case "ABORTED" -> Material.GRAY_STAINED_GLASS;
            default -> self == null ? Material.IRON_SWORD : Material.LIME_STAINED_GLASS;
        };
        GameQueries.PlayerLine one = row.side(1);
        GameQueries.PlayerLine two = row.side(2);
        Component opponent;
        if (self != null) {
            GameQueries.PlayerLine other = self.side() == 1 ? two : one;
            opponent = Component.text(other == null ? "?" : other.name());
        } else {
            opponent = Component.text((one == null ? "?" : one.name()) + " vs " + (two == null ? "?" : two.name()));
        }
        return Items.of(material).flags()
                .name(m.item("gui-games-entry", tag("id", row.id()), tag("outcome", m.get("outcome-" + outcome.toLowerCase(Locale.ROOT)))))
                .lore(m.lore("gui-games-entry-lore", tag("opponent", opponent),
                        tag("reason", m.plain("end-reason-" + String.valueOf(row.reason()).toLowerCase(Locale.ROOT))),
                        tag("kit", kitName(row.kit())), tag("arena", plugin.games().arenaName(row.arena())),
                        tag("time", GameManager.clock(row.duration())), tag("date", m.dateTime(row.startedAt())),
                        tag("counted", m.get(row.counted() ? "gui-games-counted" : "gui-games-not-counted"))));
    }

    private Component kitName(String id) {
        var kit = plugin.kits().get(id);
        return kit == null ? Component.text(String.valueOf(id)) : plugin.messages().mini(kit.name());
    }
}
