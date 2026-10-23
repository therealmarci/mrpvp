package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.stats.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/**
 * The leaderboard by wins, K/D, win rate or best streak - all kits or one, all time / 30 / 7 days. The first three
 * stand on a podium (heads on gold, iron and copper), the next ones are lined up below:
 * <pre>
 *  . . . . 1 . . . .
 *  . . 2 . # . 3 . .     1 = head, # = podium block
 *  . . # . # . # . .
 *  4 5 6 7 8 9 ...       places 4 - 21
 * </pre>
 */
public final class LeaderboardMenu extends Menu {

    private static final int[] PERIODS = {0, 30, 7};
    /** head slot and podium slots of places 1, 2, 3 */
    private static final int[][] PODIUM = {{4, 13, 22}, {11, 20}, {15, 24}};
    private static final Material[] PODIUM_BLOCKS = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};
    private static final int FIRST_ROW_SLOT = 27;
    private static final int ROW_SLOTS = 18;

    private StatsStore.Sort sort = StatsStore.Sort.WINS;
    private String kit = StatsStore.ALL;
    private int period;
    private List<StatsStore.Entry> entries;

    public LeaderboardMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-top-title"));
        reload();
    }

    private void reload() {
        entries = null;
        StatsStore.Sort s = sort;
        String k = kit;
        int days = PERIODS[period];
        long since = days == 0 ? 0 : System.currentTimeMillis() - days * 86_400_000L;
        int minGames = plugin.settings().stats().minGamesForRatio();
        load(c -> StatsStore.leaderboard(c, s, k, since, minGames), list -> entries = list);
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        if (entries == null) {
            set(22, loading());
        } else if (entries.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-top-empty")).build());
        } else {
            for (int i = 0; i < 3 && i < entries.size(); i++) {
                StatsStore.Entry e = entries.get(i);
                set(PODIUM[i][0], entry(i + 1, e).build());
                for (int p = 1; p < PODIUM[i].length; p++) {
                    set(PODIUM[i][p], describe(Items.of(PODIUM_BLOCKS[i]), i + 1, e).build());
                }
            }
            for (int i = 3; i < entries.size() && i < 3 + ROW_SLOTS; i++) {
                set(FIRST_ROW_SLOT + i - 3, entry(i + 1, entries.get(i)).build());
            }
            for (int row = 0; row < 3; row++) {
                fillRow(row);
            }
        }

        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new MainMenu(plugin, viewer).open();
        });
        set(47, Items.of(Material.COMPARATOR).name(m.item("gui-top-sort",
                        tag("sort", m.get("gui-top-sort-" + sort.name().toLowerCase(Locale.ROOT).replace('_', '-')))))
                .lore(m.lore(sort == StatsStore.Sort.KD || sort == StatsStore.Sort.WIN_RATE ? "gui-top-sort-lore-ratio"
                        : "gui-top-sort-lore", tag("games", plugin.settings().stats().minGamesForRatio()))).build(), click -> {
            StatsStore.Sort[] all = StatsStore.Sort.values();
            int step = click == ClickType.RIGHT ? all.length - 1 : 1;
            sort = all[(sort.ordinal() + step) % all.length];
            clickSound();
            reload();
            render();
        });
        List<String> kits = new ArrayList<>();
        kits.add(StatsStore.ALL);
        plugin.kits().all().forEach(k -> kits.add(k.id()));
        Kit current = plugin.kits().get(kit);
        set(48, Items.of(current == null ? Material.CHEST : current.icon()).flags()
                .name(m.item("gui-top-kit", tag("kit", current == null ? m.get("gui-stats-all-kits") : m.mini(current.name()))))
                .lore(m.lore("gui-top-switch-lore")).build(), click -> {
            int index = Math.max(0, kits.indexOf(kit));
            int step = click == ClickType.RIGHT ? kits.size() - 1 : 1;
            kit = kits.get((index + step) % kits.size());
            clickSound();
            reload();
            render();
        });
        set(49, Items.of(Material.CLOCK).name(m.item("gui-top-period", tag("period", m.get("gui-top-period-" + PERIODS[period]))))
                .lore(m.lore("gui-top-switch-lore")).build(), click -> {
            int step = click == ClickType.RIGHT ? PERIODS.length - 1 : 1;
            period = (period + step) % PERIODS.length;
            clickSound();
            reload();
            render();
        });
        if (entries != null) {
            int own = -1;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).uuid().equals(viewer.getUniqueId())) {
                    own = i;
                    break;
                }
            }
            if (own >= 0) {
                set(51, entry(own + 1, entries.get(own)).name(m.item("gui-top-own", tag("place", own + 1))).build());
            } else {
                set(51, Items.head(viewer).name(m.item("gui-top-own-none")).build());
            }
        }
        fillRow(5);
    }

    private Items entry(int place, StatsStore.Entry e) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(e.uuid());
        return describe(Items.head(player).amount(Math.min(place, 64)), place, e);
    }

    private Items describe(Items item, int place, StatsStore.Entry e) {
        var m = plugin.messages();
        return item.name(m.item("gui-top-entry", tag("place", place), tag("player", e.name())))
                .lore(m.lore("gui-top-entry-lore", tag("wins", e.wins()), tag("losses", e.losses()),
                        tag("draws", e.draws()), tag("kd", decimal(e.kd(), 2)), tag("rate", decimal(e.winRate(), 1)),
                        tag("streak", e.bestStreak()), tag("games", e.games())));
    }
}
