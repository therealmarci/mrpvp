package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.DefaultArena;
import at.marcelr.mrpvp.editor.EditorManager;
import at.marcelr.mrpvp.game.Spectators;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;

import static at.marcelr.mrpvp.Messages.tag;

/** Admin start page (/duel admin or the button in the main menu). Every area has its own permission. */
public final class AdminMenu extends Menu {

    public static final String PERM_GUI = "mrpvp.admin.gui";
    public static final String PERM_GAMES = "mrpvp.admin.games";
    public static final String PERM_RESTORE = "mrpvp.admin.restore";
    public static final String PERM_STOP = "mrpvp.admin.stop";
    public static final String PERM_KITS = "mrpvp.admin.kits";
    public static final String PERM_PLAYERS = "mrpvp.admin.players";
    public static final String PERM_BAN = "mrpvp.admin.ban";
    public static final String PERM_RESET = "mrpvp.admin.reset";
    public static final String PERM_DEFAULTS = "mrpvp.admin.defaults";

    public AdminMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 3, plugin.messages().get("gui-admin-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(4, Items.of(Material.CLOCK).name(m.item("gui-admin-status"))
                .lore(m.lore("gui-admin-status-lore", tag("games", plugin.games().games().size()),
                        tag("arenas", plugin.slots().inUse()), tag("templates", plugin.templates().all().size()),
                        tag("bans", plugin.bans().all().size()))).build());
        set(10, Items.of(Material.ENDER_EYE).name(m.item("gui-admin-live", tag("count", plugin.games().games().size())))
                .lore(m.lore("gui-admin-live-lore")).build(), click -> {
            if (allowed(Spectators.PERM_SPECTATE) || viewer.hasPermission(PERM_STOP)) {
                clickSound();
                new LiveMenu(plugin, viewer).open();
            }
        });
        set(11, Items.of(Material.BOOK).name(m.item("gui-admin-games")).lore(m.lore("gui-admin-games-lore")).build(), click -> {
            if (allowed(PERM_GAMES)) {
                clickSound();
                new GamesMenu(plugin, viewer, null, null, true, this::open).open();
            }
        });
        set(12, Items.of(Material.STONE_BRICKS).name(m.item("gui-admin-arenas")).lore(m.lore("gui-admin-arenas-lore")).build(),
                click -> {
                    if (allowed(EditorManager.PERM_ARENA)) {
                        clickSound();
                        new ArenaListMenu(plugin, viewer).open();
                    }
                });
        set(13, Items.of(Material.IRON_CHESTPLATE).flags().name(m.item("gui-admin-kits")).lore(m.lore("gui-admin-kits-lore")).build(),
                click -> {
                    if (allowed(PERM_KITS)) {
                        clickSound();
                        new KitListMenu(plugin, viewer).open();
                    }
                });
        set(14, Items.of(Material.PLAYER_HEAD).name(m.item("gui-admin-players")).lore(m.lore("gui-admin-players-lore")).build(),
                click -> {
                    if (allowed(PERM_PLAYERS)) {
                        clickSound();
                        new PlayersMenu(plugin, viewer, 0).open();
                    }
                });
        int alerts = plugin.monitor().recent().size();
        set(15, Items.of(alerts > 0 ? Material.BELL : Material.PAPER).amount(Math.max(1, alerts)).glint(alerts > 0)
                .name(m.item("gui-admin-alerts", tag("count", alerts))).lore(m.lore("gui-admin-alerts-lore")).build(), click -> {
            clickSound();
            new AlertsMenu(plugin, viewer).open();
        });
        set(16, Items.of(Material.RECOVERY_COMPASS).name(m.item("gui-admin-defaults")).lore(m.lore("gui-admin-defaults-lore")).build(),
                click -> {
                    if (allowed(PERM_DEFAULTS)) {
                        new ConfirmMenu(plugin, viewer, m.item("gui-admin-defaults"), this::restoreDefaults, this::open).open();
                    }
                });
        int reviews = plugin.reviews().pending();
        set(22, Items.of(reviews > 0 ? Material.WRITABLE_BOOK : Material.BOOK).amount(Math.max(1, Math.min(64, reviews)))
                .glint(reviews > 0).name(m.item("gui-admin-reviews", tag("count", reviews)))
                .lore(m.lore("gui-admin-reviews-lore")).build(), click -> {
            if (allowed(at.marcelr.mrpvp.game.Reviews.PERM_REVIEW)) {
                clickSound();
                new ReviewsMenu(plugin, viewer).open();
            }
        });
        set(18, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new MainMenu(plugin, viewer).open();
        });
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }

    /** Missing default kits back into kits.yml, deleted default arenas built again. */
    private void restoreDefaults() {
        var m = plugin.messages();
        try {
            List<String> kits = plugin.kits().restoreDefaults();
            List<String> arenas = DefaultArena.restore(plugin);
            if (!arenas.isEmpty()) {
                plugin.slots().fillPools();
            }
            m.send(viewer, "defaults-restored", tag("kits", kits.isEmpty() ? "-" : String.join(", ", kits)),
                    tag("arena", arenas.isEmpty() ? "-" : String.join(", ", arenas)));
            plugin.getLogger().info(viewer.getName() + " restored defaults (kits: " + kits + ", arenas: " + arenas + ").");
        } catch (IOException e) {
            m.send(viewer, "editor-error-io", tag("error", e.getMessage()));
        }
        open();
    }
}
