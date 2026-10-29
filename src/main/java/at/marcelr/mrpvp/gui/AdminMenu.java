package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.DefaultArena;
import at.marcelr.mrpvp.editor.EditorManager;
import at.marcelr.mrpvp.game.Reviews;
import at.marcelr.mrpvp.game.Spectators;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.List;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Admin start page (/duel admin or the button in the main menu), symmetric in groups: watching on the left, players
 * on the right, the content (kits, arenas) below. Every area has its own permission; an area without it stays in its
 * place as a grey lock, so the menu looks the same for everyone in the team.
 * <pre>
 *  . . . . [status] . . . .
 *  . [live][review][alerts] . [rounds][players][bans] .
 *  . . [kits] . . . [arenas] . .
 *  . . . [back][defaults][close] . . .
 * </pre>
 */
public final class AdminMenu extends Menu {

    public static final String PERM_GUI = "mrpvp.admin.gui";
    public static final String PERM_GAMES = "mrpvp.admin.games";
    public static final String PERM_RESTORE = "mrpvp.admin.restore";
    public static final String PERM_STOP = "mrpvp.admin.stop";
    public static final String PERM_PAUSE = "mrpvp.admin.pause";
    public static final String PERM_KITS = "mrpvp.admin.kits";
    public static final String PERM_PLAYERS = "mrpvp.admin.players";
    public static final String PERM_BAN = "mrpvp.admin.ban";
    public static final String PERM_RESET = "mrpvp.admin.reset";
    public static final String PERM_DEFAULTS = "mrpvp.admin.defaults";

    public AdminMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 4, plugin.messages().get("gui-admin-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(4, Items.of(Material.CLOCK).name(m.item("gui-admin-status"))
                .lore(m.lore("gui-admin-status-lore", tag("games", plugin.games().games().size()),
                        tag("arenas", plugin.slots().inUse()), tag("templates", plugin.templates().all().size()),
                        tag("bans", plugin.bans().all().size()))).build());

        // Watching
        boolean live = viewer.hasPermission(Spectators.PERM_SPECTATE) || viewer.hasPermission(PERM_STOP)
                || viewer.hasPermission(PERM_PAUSE);
        area(10, live, Items.of(Material.ENDER_EYE).name(m.item("gui-admin-live", tag("count", plugin.games().games().size())))
                .lore(m.lore("gui-admin-live-lore")).build(), () -> new LiveMenu(plugin, viewer).open());
        int reviews = plugin.reviews().pending();
        area(11, viewer.hasPermission(Reviews.PERM_REVIEW), Items.of(reviews > 0 ? Material.WRITABLE_BOOK : Material.BOOK)
                .amount(Math.max(1, Math.min(64, reviews))).glint(reviews > 0).name(m.item("gui-admin-reviews", tag("count", reviews)))
                .lore(m.lore("gui-admin-reviews-lore")).build(), () -> new ReviewsMenu(plugin, viewer).open());
        int alerts = plugin.monitor().recent().size();
        area(12, true, Items.of(alerts > 0 ? Material.BELL : Material.PAPER).amount(Math.max(1, alerts)).glint(alerts > 0)
                .name(m.item("gui-admin-alerts", tag("count", alerts))).lore(m.lore("gui-admin-alerts-lore")).build(),
                () -> new AlertsMenu(plugin, viewer).open());

        // Players
        area(14, viewer.hasPermission(PERM_GAMES), Items.of(Material.KNOWLEDGE_BOOK).name(m.item("gui-admin-games"))
                .lore(m.lore("gui-admin-games-lore")).build(), () -> new GamesMenu(plugin, viewer, null, null, true, this::open).open());
        area(15, viewer.hasPermission(PERM_PLAYERS), Items.of(Material.PLAYER_HEAD).name(m.item("gui-admin-players"))
                .lore(m.lore("gui-admin-players-lore")).build(), () -> new PlayersMenu(plugin, viewer, 0).open());
        area(16, viewer.hasPermission(PERM_PLAYERS), Items.of(Material.IRON_DOOR)
                .name(m.item("gui-admin-bans", tag("count", plugin.bans().all().size()))).lore(m.lore("gui-admin-bans-lore")).build(),
                () -> new BansMenu(plugin, viewer, this::open).open());

        // Content
        area(20, viewer.hasPermission(PERM_KITS), Items.of(Material.IRON_CHESTPLATE).flags().name(m.item("gui-admin-kits"))
                .lore(m.lore("gui-admin-kits-lore")).build(), () -> new KitListMenu(plugin, viewer).open());
        area(24, viewer.hasPermission(EditorManager.PERM_ARENA), Items.of(Material.STONE_BRICKS).name(m.item("gui-admin-arenas"))
                .lore(m.lore("gui-admin-arenas-lore")).build(), () -> new ArenaListMenu(plugin, viewer).open());

        set(30, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new MainMenu(plugin, viewer).open();
        });
        if (viewer.hasPermission(PERM_DEFAULTS)) {
            set(31, Items.of(Material.RECOVERY_COMPASS).name(m.item("gui-admin-defaults")).lore(m.lore("gui-admin-defaults-lore")).build(),
                    click -> new ConfirmMenu(plugin, viewer, m.item("gui-admin-defaults"), this::restoreDefaults, this::open).open());
        } else {
            locked(31, m.item("gui-admin-defaults"));
        }
        set(32, Items.of(Material.BARRIER).name(m.item("gui-close")).build(), click -> viewer.closeInventory());
        for (int row = 0; row < 4; row++) {
            fillRow(row);
        }
    }

    /** An area of the menu: the button if the viewer may use it, otherwise a grey lock in its place. */
    private void area(int slot, boolean may, ItemStack item, Runnable open) {
        if (!may) {
            Component name = item.getItemMeta() == null ? Component.empty() : item.getItemMeta().displayName();
            locked(slot, name == null ? Component.empty() : name);
            return;
        }
        set(slot, item, click -> {
            clickSound();
            open.run();
        });
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
