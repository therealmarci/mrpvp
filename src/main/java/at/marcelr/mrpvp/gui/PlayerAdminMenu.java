package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Bans;
import at.marcelr.mrpvp.game.Game;
import at.marcelr.mrpvp.snapshot.Snapshot;
import at.marcelr.mrpvp.snapshot.SnapshotStore;
import at.marcelr.mrpvp.stats.StatsStore;
import at.marcelr.mrpvp.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.tag;

/** Admin view of one player: statistics, rounds, duel ban, statistics reset, owed states. */
public final class PlayerAdminMenu extends Menu {

    private final UUID target;
    private final String name;
    private final Runnable back;
    private List<Snapshot> owed;

    public PlayerAdminMenu(MRPvp plugin, Player viewer, UUID target, String name) {
        this(plugin, viewer, target, name, null);
    }

    /** @param back where "back" leads (MRadmin), null = the player list */
    public PlayerAdminMenu(MRPvp plugin, Player viewer, UUID target, String name, Runnable back) {
        super(plugin, viewer, 3, plugin.messages().get("gui-player-admin-title", tag("player", name)));
        this.target = target;
        this.name = name;
        this.back = back != null ? back : () -> new PlayersMenu(plugin, viewer, 0).open();
        load(c -> SnapshotStore.owed(c, target), list -> owed = list);
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        Player online = Bukkit.getPlayer(target);
        Game game = online == null ? null : plugin.games().gameOf(online);
        set(4, (online != null ? Items.head(online) : Items.head(Bukkit.getOfflinePlayer(target)))
                .name(m.item("gui-players-name", tag("player", name)))
                .lore(m.lore("gui-player-admin-head", tag("state", m.get(online == null ? "gui-player-admin-offline"
                        : game != null ? "gui-player-admin-ingame" : plugin.stateKeeper().inside(online)
                        ? "gui-player-admin-inside" : "gui-player-admin-online", tag("id", game == null ? 0 : game.id())))))
                .build());
        set(10, Items.of(Material.IRON_SWORD).flags().name(m.item("gui-main-stats")).build(), click -> {
            clickSound();
            new StatsMenu(plugin, viewer, target, name, StatsStore.ALL, this::open).open();
        });
        set(11, Items.of(Material.BOOK).name(m.item("gui-stats-games-button")).build(), click -> {
            if (allowed(AdminMenu.PERM_GAMES)) {
                clickSound();
                new GamesMenu(plugin, viewer, target, name, true, this::open).open();
            }
        });

        Bans.Ban ban = plugin.bans().get(target);
        if (ban == null) {
            set(13, Items.of(Material.IRON_BARS).name(m.item("gui-player-admin-ban")).lore(m.lore("gui-player-admin-ban-lore")).build(),
                    click -> {
                        if (allowed(AdminMenu.PERM_BAN)) {
                            askBan();
                        }
                    });
        } else {
            set(13, Items.of(Material.BARRIER).name(m.item("gui-player-admin-unban"))
                    .lore(m.lore("gui-bans-lore", tag("until", ban.until() == null ? m.plain("ban-permanent")
                                    : m.dateTime(ban.until())), tag("reason", ban.reason() == null ? "-" : ban.reason()),
                            tag("by", ban.by()), tag("date", m.dateTime(ban.createdAt())))).build(), click -> {
                if (allowed(AdminMenu.PERM_BAN)) {
                    plugin.bans().unban(target);
                    m.send(viewer, "unbanned", tag("player", name));
                    plugin.getLogger().info(viewer.getName() + " lifted the duel ban of " + name + ".");
                    successSound();
                    render();
                }
            });
        }
        set(14, Items.of(Material.TNT).name(m.item("gui-player-admin-reset")).lore(m.lore("gui-player-admin-reset-lore")).build(),
                click -> {
                    if (allowed(AdminMenu.PERM_RESET)) {
                        new ConfirmMenu(plugin, viewer, m.item("gui-player-admin-reset-confirm", tag("player", name)), () -> {
                            long now = System.currentTimeMillis();
                            plugin.database().submit(c -> {
                                StatsStore.reset(c, target, now);
                                return null;
                            });
                            m.send(viewer, "stats-reset", tag("player", name));
                            plugin.getLogger().info(viewer.getName() + " reset the duel statistics of " + name + ".");
                            open();
                        }, this::open).open();
                    }
                });
        if (owed == null) {
            set(16, loading());
        } else if (owed.isEmpty()) {
            set(16, Items.of(Material.LIME_DYE).name(m.item("gui-player-admin-owed-none")).build());
        } else {
            Snapshot first = owed.getFirst();
            set(16, Items.of(Material.HOPPER).name(m.item("gui-player-admin-owed", tag("count", owed.size())))
                    .lore(m.lore("gui-player-admin-owed-lore", tag("id", first.id()), tag("date", m.dateTime(first.takenAt())),
                            tag("purpose", first.purpose().name().toLowerCase(java.util.Locale.ROOT)))).build(), click -> {
                clickSound();
                new SnapshotMenu(plugin, viewer, first, this::open).open();
            });
        }
        set(18, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }

    /** Duration ("7d", "12h", empty = permanent), then the reason. */
    private void askBan() {
        TextPrompt.ask(plugin, viewer, "prompt-ban-duration", "7d", 16, duration -> {
            long millis = duration.isBlank() ? 0 : DurationUtil.parse(duration);
            if (millis < 0) {
                plugin.messages().send(viewer, "ban-duration-invalid");
                askBan();
                return;
            }
            TextPrompt.ask(plugin, viewer, "prompt-ban-reason", "", 200, reason -> {
                Long until = millis == 0 ? null : System.currentTimeMillis() + millis;
                plugin.bans().ban(target, name, until, reason, viewer.getName());
                plugin.games().invites().removeAll(target);
                plugin.messages().send(viewer, "banned", tag("player", name), tag("until", until == null
                        ? plugin.messages().plain("ban-permanent") : plugin.messages().dateTime(until)));
                plugin.getLogger().info(viewer.getName() + " banned " + name + " from duels"
                        + (until == null ? " permanently" : " until " + until) + ".");
                open();
            }, this::open);
        }, this::open);
    }
}
