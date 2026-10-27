package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.snapshot.Snapshot;
import at.marcelr.mrpvp.snapshot.SnapshotStore;
import at.marcelr.mrpvp.stats.GameQueries;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/**
 * One round by its Game-ID: both players with their numbers, the combat log as a book and - for admins - the saved
 * inventories (on entering and at the end) and "give back" if a state is still owed.
 */
public final class GameMenu extends Menu {

    private static final int LINES_PER_PAGE = 13;

    private final long id;
    private final boolean admin;
    private final Runnable back;
    private boolean loaded;
    private GameQueries.GameRow row;
    private List<Snapshot> snapshots = List.of();

    public GameMenu(MRPvp plugin, Player viewer, long id, boolean admin, Runnable back) {
        super(plugin, viewer, 5, plugin.messages().get("gui-game-title", tag("id", id)));
        this.id = id;
        this.admin = admin;
        this.back = back;
        reload();
    }

    private void reload() {
        boolean withSnapshots = admin;
        load(c -> new Object[] {GameQueries.get(c, id), withSnapshots ? SnapshotStore.ofGame(c, id) : List.of()}, data -> {
            row = (GameQueries.GameRow) data[0];
            @SuppressWarnings("unchecked")
            List<Snapshot> list = (List<Snapshot>) data[1];
            snapshots = list;
            loaded = true;
        });
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(36, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        fillRow(4);
        if (!loaded) {
            set(22, loading());
            return;
        }
        // Players may only look at their own rounds.
        if (row == null || (!admin && row.of(viewer.getUniqueId()) == null)) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-game-missing", tag("id", id))).build());
            return;
        }
        boolean running = row.endedAt() == null;
        String reason = row.reason() == null ? "running" : row.reason().toLowerCase(Locale.ROOT);
        set(4, Items.of(Material.PAPER).name(m.item("gui-game-info", tag("id", id)))
                .lore(m.lore("gui-game-info-lore",
                        tag("result", m.get(running ? "outcome-running" : "outcome-" + String.valueOf(row.result()).toLowerCase(Locale.ROOT))),
                        tag("reason", running ? "-" : m.plain("end-reason-" + reason)),
                        tag("kit", kitName(row.kit())), tag("arena", plugin.games().arenaName(row.arena())),
                        tag("date", m.dateTime(row.startedAt())), tag("time", GameManager.clock(row.duration())),
                        tag("counted", row.pending() ? m.get("gui-games-review", tag("flags",
                                plugin.reviews().flagNames(at.marcelr.mrpvp.game.Reviews.flags(row.flags()))))
                                : m.get(row.counted() ? "gui-games-counted" : "gui-games-not-counted")),
                        tag("stopped", row.stoppedBy() == null ? "-" : row.stoppedBy()))).build());
        player(10, row.side(1));
        player(16, row.side(2));
        set(13, Items.of(row.winner() == null ? Material.WHITE_BANNER : Material.GOLDEN_HELMET).flags()
                .name(row.winner() == null ? m.item("gui-game-no-winner")
                        : m.item("gui-game-winner", tag("player", nameOf(row.winner())))).build());
        set(22, Items.of(Material.WRITABLE_BOOK).name(m.item("gui-game-log")).lore(m.lore("gui-game-log-lore")).build(),
                click -> {
                    clickSound();
                    load(c -> GameQueries.events(c, id), this::openLog);
                });
        if (admin) {
            snapshotButtons(28, row.side(1));
            snapshotButtons(33, row.side(2));
        }
        if (admin && row.pending() && viewer.hasPermission(at.marcelr.mrpvp.game.Reviews.PERM_REVIEW)) {
            reviewButtons();
        } else if (admin && viewer.hasPermission(at.marcelr.mrpvp.game.Reviews.PERM_REVIEW)
                && (row.counted() || row.mayCountAgain())) {
            countButton();
        }
    }

    /** Count or discard a round that waits for a review. */
    private void reviewButtons() {
        var m = plugin.messages();
        var flags = at.marcelr.mrpvp.game.Reviews.flags(row.flags());
        set(39, Items.of(Material.LIME_CONCRETE).name(m.item("gui-review-count"))
                .lore(m.lore("gui-review-count-lore", tag("flags", plugin.reviews().flagNames(flags)))).build(),
                click -> decide(true));
        set(41, Items.of(Material.RED_CONCRETE).name(m.item("gui-review-discard"))
                .lore(m.lore("gui-review-discard-lore")).build(), click -> decide(false));
    }

    /** Afterwards: take a counted round out of the statistics (with an optional reason) or count it again. */
    private void countButton() {
        var m = plugin.messages();
        String by = row.reviewedBy() == null ? "-" : row.reviewedBy();
        String note = row.reviewNote() == null ? "-" : row.reviewNote();
        if (row.counted()) {
            set(40, Items.of(Material.RED_DYE).name(m.item("gui-game-uncount"))
                    .lore(m.lore("gui-game-uncount-lore")).build(), click -> {
                clickSound();
                TextPrompt.ask(plugin, viewer, "prompt-uncount", "", 200,
                        reason -> setCounted(false, reason), this::open);
            });
        } else {
            set(40, Items.of(Material.LIME_DYE).name(m.item("gui-game-recount"))
                    .lore(m.lore(row.removed() ? "gui-game-recount-lore" : "gui-game-recount-rejected-lore",
                            tag("admin", by), tag("note", note))).build(),
                    click -> new ConfirmMenu(plugin, viewer, m.item("gui-game-recount-confirm", tag("id", id)),
                            () -> setCounted(true, null), this::open).open());
        }
    }

    private void setCounted(boolean count, String note) {
        plugin.reviews().setCounted(id, count, viewer.getName(), note).whenComplete((changed, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().run(viewer, () -> {
                    if (error != null || !Boolean.TRUE.equals(changed)) {
                        plugin.messages().send(viewer, "game-count-failed");
                    } else {
                        plugin.messages().send(viewer, count ? "game-recounted" : "game-uncounted", tag("id", id));
                        successSound();
                    }
                    reload();
                    open();
                })));
    }

    private void decide(boolean count) {
        plugin.reviews().decide(id, count, viewer.getName()).whenComplete((changed, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || !Boolean.TRUE.equals(changed)) {
                        plugin.messages().send(viewer, "review-already");
                    } else {
                        plugin.messages().send(viewer, count ? "review-counted" : "review-discarded", tag("id", id));
                        successSound();
                    }
                    reload();
                }));
    }

    private void player(int slot, GameQueries.PlayerLine p) {
        var m = plugin.messages();
        if (p == null) {
            return;
        }
        var head = Bukkit.getPlayer(p.uuid()) instanceof Player online ? Items.head(online)
                : Items.head(Bukkit.getOfflinePlayer(p.uuid()));
        set(slot, head.name(m.item("gui-game-player", tag("player", p.name()),
                        tag("outcome", m.get(p.outcome() == null ? "outcome-aborted" : "outcome-" + p.outcome().toLowerCase(Locale.ROOT)))))
                .lore(m.lore("gui-game-player-lore", tag("dealt", decimal(p.damageDealt() / 2, 1)),
                        tag("taken", decimal(p.damageTaken() / 2, 1)), tag("hits", p.hits()), tag("crits", p.crits()),
                        tag("shot", p.arrowsShot()), tag("hit", p.arrowsHit()), tag("totems", p.totems()),
                        tag("pearls", p.pearls()),
                        tag("health", p.healthEnd() == null ? "-" : decimal(p.healthEnd() / 2, 1)))).build());
    }

    /** Inventory on entering, at the end, and "give back" while owed. */
    private void snapshotButtons(int slot, GameQueries.PlayerLine p) {
        var m = plugin.messages();
        if (p == null) {
            return;
        }
        Snapshot join = find(p.uuid(), Snapshot.Phase.JOIN);
        Snapshot end = find(p.uuid(), Snapshot.Phase.END);
        if (join != null) {
            set(slot, Items.of(Material.CHEST).name(m.item("gui-game-join", tag("player", p.name())))
                    .lore(m.lore(join.restoredAt() == null ? "gui-game-join-owed" : "gui-game-join-restored",
                            tag("date", m.dateTime(join.restoredAt() == null ? 0 : join.restoredAt())))).build(), click -> {
                clickSound();
                new SnapshotMenu(plugin, viewer, join, this::open).open();
            });
        } else {
            set(slot, Items.of(Material.GRAY_DYE).name(m.item("gui-game-snapshot-gone")).build());
        }
        if (end != null) {
            set(slot + 1, Items.of(Material.ENDER_CHEST).name(m.item("gui-game-end", tag("player", p.name()))).build(), click -> {
                clickSound();
                new SnapshotMenu(plugin, viewer, end, this::open).open();
            });
        }
        if (join != null && join.owed()) {
            Player online = Bukkit.getPlayer(p.uuid());
            set(slot + 2, Items.of(Material.HOPPER).name(m.item("gui-game-give-back", tag("player", p.name())))
                    .lore(m.lore(online == null ? "gui-game-give-back-offline" : "gui-game-give-back-lore")).build(), click -> {
                if (!allowed(AdminMenu.PERM_RESTORE) || online == null) {
                    return;
                }
                new ConfirmMenu(plugin, viewer, m.item("gui-game-give-back", tag("player", p.name())), () -> {
                    if (plugin.games().inGame(online.getUniqueId()) || plugin.stateKeeper().inside(online)) {
                        m.send(viewer, "give-back-busy", tag("player", online.getName()));
                    } else if (plugin.stateKeeper().giveBackNow(online, join)) {
                        m.send(viewer, "give-back-done", tag("player", online.getName()));
                        plugin.getLogger().info(viewer.getName() + " gave " + online.getName() + " snapshot " + join.id() + " back.");
                    }
                    reload();
                    open();
                }, this::open).open();
            });
        }
    }

    private Snapshot find(UUID player, Snapshot.Phase phase) {
        return snapshots.stream().filter(s -> s.uuid().equals(player) && s.phase() == phase).findFirst().orElse(null);
    }

    private String nameOf(UUID uuid) {
        GameQueries.PlayerLine p = row.of(uuid);
        return p == null ? "?" : p.name();
    }

    private Component kitName(String kit) {
        var k = plugin.kits().get(kit);
        return k == null ? Component.text(String.valueOf(kit)) : plugin.messages().mini(k.name());
    }

    /** The combat log as a book: one line per hit, shot, pearl ... */
    private void openLog(List<GameQueries.EventRow> events) {
        var m = plugin.messages();
        List<Component> lines = new ArrayList<>();
        for (GameQueries.EventRow e : events) {
            lines.add(line(e));
        }
        if (lines.isEmpty()) {
            lines.add(m.get("gui-game-log-empty"));
        }
        List<Component> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
            pages.add(Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(),
                    lines.subList(i, Math.min(lines.size(), i + LINES_PER_PAGE))));
        }
        viewer.closeInventory();
        viewer.openBook(Book.book(m.get("gui-game-log"), Component.text("MRpvp"), pages));
    }

    private Component line(GameQueries.EventRow e) {
        var m = plugin.messages();
        Component time = Component.text(GameManager.clock(e.t()) + " ", NamedTextColor.DARK_GRAY);
        String actor = e.actor() == null ? "" : nameOf(e.actor());
        String target = e.target() == null ? "" : nameOf(e.target());
        String damage = e.damage() == null ? "" : decimal(e.damage() / 2, 1);
        String key = "log-" + e.type().toLowerCase(Locale.ROOT);
        return time.append(m.get(key, tag("actor", actor), tag("target", target), tag("damage", damage),
                tag("cause", e.cause() == null ? "" : e.cause().toLowerCase(Locale.ROOT)),
                tag("weapon", e.weapon() == null ? "" : e.weapon().toLowerCase(Locale.ROOT))));
    }
}
