package at.marcelr.mrpvp.api;

import at.marcelr.mradmin.api.MRadminService;
import at.marcelr.mradmin.api.SelfArea;
import at.marcelr.mradmin.api.SelfBlock;
import at.marcelr.mradmin.api.SelfRanking;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Messages;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.stats.GameQueries;
import at.marcelr.mrpvp.stats.PlayerStats;
import at.marcelr.mrpvp.stats.StatsStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MRpvp on the player's own page in MRadmin's web interface (/web): their duel statistics and the last rounds, plus
 * the duel rankings for the statistics page. Read only - duels happen in game. Only loaded while MRadmin runs.
 */
final class SelfPage {

    static final String AREA = "duels";
    private static final String USE = "mrpvp.use";
    /** Rounds in the list (the web interface shows them in pages). */
    private static final int HISTORY = 100;
    private static final int RANKING_SIZE = 500;

    private SelfPage() {
    }

    static List<SelfArea> areas(MRPvp plugin, SelfViewer viewer) {
        if (!viewer.has(USE)) {
            return List.of();
        }
        return Messages.in(viewer.lang(), () -> {
            try {
                return List.of(area(plugin, viewer));
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not read the duel statistics of " + viewer.name() + ": " + e.getMessage());
                return List.of();
            }
        });
    }

    private static SelfArea area(MRPvp plugin, SelfViewer viewer) throws SQLException {
        Messages m = plugin.messages();
        UUID player = viewer.player();
        PlayerStats s = plugin.database().call(c -> StatsStore.stats(c, player, viewer.name(), StatsStore.ALL));
        Locale locale = locale(viewer.lang());
        List<SelfBlock.Stat> stats = List.of(
                new SelfBlock.Stat(m.plain("self-games"), String.valueOf(s.games()), ""),
                new SelfBlock.Stat(m.plain("self-wins"), String.valueOf(s.wins()), s.wins() > 0 ? "good" : ""),
                new SelfBlock.Stat(m.plain("self-losses"), String.valueOf(s.losses()), ""),
                new SelfBlock.Stat(m.plain("self-win-rate"), String.format(locale, "%.0f %%", s.winRate()), ""),
                new SelfBlock.Stat(m.plain("self-kd"), String.format(locale, "%.2f", s.kd()), ""),
                new SelfBlock.Stat(m.plain("self-streak"), s.streak() + " / " + s.bestStreak(), ""),
                new SelfBlock.Stat(m.plain("self-accuracy"), String.format(locale, "%.0f %%", s.accuracy()), ""));

        List<SelfBlock.Row> rows = new ArrayList<>();
        List<SelfBlock.Card> cards = new ArrayList<>();
        // Cards need MRadmin with API 5; an older one gets the table.
        MRadminService service = MRadminService.get();
        boolean asCards = service != null && service.apiVersion() >= 5;
        DateTimeFormatter date = DateTimeFormatter.ofPattern("de".equals(viewer.lang()) ? "dd.MM.yyyy HH:mm" : "yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());
        for (GameQueries.GameRow row : plugin.database().call(c -> GameQueries.list(c, player, GameQueries.ResultFilter.ALL, 0, HISTORY))) {
            if (row.endedAt() == null) {
                continue; // still running
            }
            if (asCards) {
                cards.add(card(plugin, row, player, date, locale));
                continue;
            }
            GameQueries.PlayerLine me = row.of(player);
            GameQueries.PlayerLine other = row.players().stream().filter(p -> !p.uuid().equals(player)).findFirst().orElse(null);
            String outcome = me == null || me.outcome() == null ? "" : me.outcome();
            String result = switch (outcome) {
                case "WIN" -> m.plain("self-result-win");
                case "LOSS" -> m.plain("self-result-loss");
                case "DRAW" -> m.plain("self-result-draw");
                default -> m.plain("self-result-none");
            };
            if (!row.counted()) {
                result += " · " + m.plain("self-not-counted");
            }
            Kit kit = plugin.kits().get(row.kit());
            String kitName = kit == null ? row.kit() : plain(kit.name());
            rows.add(new SelfBlock.Row(List.of(date.format(Instant.ofEpochMilli(row.endedAt())),
                    other == null ? "-" : other.name(), kitName, result, duration(row.duration())),
                    "WIN".equals(outcome) ? "good" : "LOSS".equals(outcome) ? "bad" : "", List.of()));
        }
        SelfBlock history = asCards ? new SelfBlock.Cards(m.plain("self-history"), cards, m.plain("self-history-empty"))
                : new SelfBlock.Table(m.plain("self-history"), List.of(m.plain("self-col-date"), m.plain("self-col-opponent"),
                        m.plain("self-col-kit"), m.plain("self-col-result"), m.plain("self-col-duration")), rows,
                        m.plain("self-history-empty"));
        List<SelfBlock> blocks = List.of(new SelfBlock.Stats(stats), history);
        // Start page tile: wins big, then games, win rate and K/D.
        List<SelfBlock> summary = List.of(new SelfBlock.Stats(List.of(stats.get(1), stats.get(0), stats.get(3), stats.get(4))));
        return new SelfArea(AREA, m.plain("self-title"), "⚔️", summary, blocks);
    }

    /**
     * One round as a card (MRadmin with API 5): "vs opponent", when and with which kit, the result as badge and colour
     * (won green, lost red, draw yellow, aborted grey), duration and damage on it; opened: all numbers of both sides.
     */
    private static SelfBlock.Card card(MRPvp plugin, GameQueries.GameRow row, UUID player, DateTimeFormatter date,
                                       Locale locale) {
        Messages m = plugin.messages();
        GameQueries.PlayerLine me = row.of(player);
        GameQueries.PlayerLine other = row.players().stream().filter(p -> !p.uuid().equals(player)).findFirst().orElse(null);
        String outcome = me == null || me.outcome() == null ? "" : me.outcome();
        String badge = switch (outcome) {
            case "WIN" -> m.plain("self-result-win");
            case "LOSS" -> m.plain("self-result-loss");
            case "DRAW" -> m.plain("self-result-draw");
            default -> m.plain("self-result-none");
        };
        String tone = switch (outcome) {
            case "WIN" -> "good";
            case "LOSS" -> "bad";
            case "DRAW" -> "warn";
            default -> "";
        };
        Kit kit = plugin.kits().get(row.kit());
        String kitName = kit == null ? row.kit() : plain(kit.name());
        String subtitle = date.format(Instant.ofEpochMilli(row.endedAt())) + " · " + kitName
                + (row.counted() ? "" : " · " + m.plain("self-not-counted"));
        List<SelfBlock.Stat> stats = new ArrayList<>();
        stats.add(new SelfBlock.Stat(m.plain("self-col-duration"), duration(row.duration()), ""));
        if (me != null) {
            stats.add(new SelfBlock.Stat(m.plain("self-card-damage"), hearts(locale, me.damageDealt()) + " / "
                    + hearts(locale, me.damageTaken()), ""));
        }
        // Opened: both sides next to each other, the reason and the arena.
        List<SelfBlock> details = new ArrayList<>();
        ArenaTemplate arena = plugin.templates().get(row.arena());
        String reason = row.reason() == null ? "" : m.plain("end-reason-" + row.reason().toLowerCase(Locale.ROOT));
        details.add(new SelfBlock.Stats(List.of(
                new SelfBlock.Stat(m.plain("self-card-reason"), reason, ""),
                new SelfBlock.Stat(m.plain("self-card-arena"), arena == null ? row.arena() : plain(arena.name()), ""),
                new SelfBlock.Stat(m.plain("self-col-kit"), kitName, ""),
                new SelfBlock.Stat(m.plain("self-card-game"), "#" + row.id(), ""))));
        if (me != null && other != null) {
            details.add(sideMeters(m.plain("self-card-me"), me, other, locale, m));
            details.add(sideMeters(m.plain("self-card-opponent", "name", other.name()), other, me, locale, m));
        }
        if (row.removed()) {
            details.add(new SelfBlock.Text(m.plain("self-card-removed"), "warn"));
        }
        return new SelfBlock.Card(String.valueOf(row.id()), m.plain("self-card-title", "name", other == null ? "?" : other.name()),
                subtitle, badge, tone, stats, List.of(), null, List.of(), details);
    }

    /** One side of a round as bars against the other side (damage, hits) plus the rest as text. */
    private static SelfBlock sideMeters(String title, GameQueries.PlayerLine p, GameQueries.PlayerLine o, Locale locale,
                                        Messages m) {
        double damage = Math.max(1, Math.max(p.damageDealt(), o.damageDealt()));
        int hits = Math.max(1, Math.max(p.hits(), o.hits()));
        List<SelfBlock.Meter> meters = new ArrayList<>();
        meters.add(new SelfBlock.Meter(m.plain("self-card-dealt"), hearts(locale, p.damageDealt()),
                p.damageDealt() / damage, -1, "", ""));
        meters.add(new SelfBlock.Meter(m.plain("self-card-hits"), p.hits() + " (" + m.plain("self-card-crits", "crits", p.crits()) + ")",
                (double) p.hits() / hits, -1, "", ""));
        if (p.healthEnd() != null) {
            meters.add(new SelfBlock.Meter(m.plain("self-card-health"), hearts(locale, p.healthEnd()), p.healthEnd() / 20.0, -1,
                    "", p.healthEnd() <= 0 ? "bad" : p.healthEnd() < 6 ? "warn" : "good"));
        }
        List<String> extra = new ArrayList<>();
        if (p.arrowsShot() > 0) {
            extra.add(m.plain("self-card-arrows", "hit", p.arrowsHit(), "shot", p.arrowsShot()));
        }
        if (p.totems() > 0) {
            extra.add(m.plain("self-card-totems", "count", p.totems()));
        }
        if (p.pearls() > 0) {
            extra.add(m.plain("self-card-pearls", "count", p.pearls()));
        }
        return new SelfBlock.Meters(title, String.join(" · ", extra), meters);
    }

    /** Damage and health in hearts (2 points = 1 heart), e.g. "12.5 ❤". */
    private static String hearts(Locale locale, double points) {
        return String.format(locale, "%.1f ❤", points / 2);
    }

    /** Most duels won, best win rate (from stats.min-games-for-ratio rounds) and the best streak. */
    static List<SelfRanking> rankings(MRPvp plugin, String lang) {
        return Messages.in(lang, () -> {
            Messages m = plugin.messages();
            Locale locale = locale(lang);
            int minGames = plugin.settings().stats().minGamesForRatio();
            List<SelfRanking> result = new ArrayList<>();
            try {
                result.add(ranking(plugin, StatsStore.Sort.WINS, minGames, "wins", m.plain("self-ranking-wins"),
                        e -> String.valueOf(e.wins())));
                result.add(ranking(plugin, StatsStore.Sort.WIN_RATE, minGames, "win-rate",
                        m.plain("self-ranking-win-rate", "games", minGames), e -> String.format(locale, "%.0f %%", e.winRate())));
                result.add(ranking(plugin, StatsStore.Sort.BEST_STREAK, minGames, "streak", m.plain("self-ranking-streak"),
                        e -> String.valueOf(e.bestStreak())));
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not read the duel rankings: " + e.getMessage());
            }
            return result;
        });
    }

    private static SelfRanking ranking(MRPvp plugin, StatsStore.Sort sort, int minGames, String id, String title,
                                       java.util.function.Function<StatsStore.Entry, String> value) throws SQLException {
        List<SelfRanking.Entry> entries = new ArrayList<>();
        for (StatsStore.Entry e : plugin.database().call(c -> StatsStore.leaderboard(c, sort, StatsStore.ALL, 0, minGames))) {
            if (entries.size() >= RANKING_SIZE) {
                break;
            }
            if (sort == StatsStore.Sort.BEST_STREAK && e.bestStreak() == 0 || sort == StatsStore.Sort.WINS && e.wins() == 0) {
                continue;
            }
            String name = e.name() != null ? e.name() : Bukkit.getOfflinePlayer(e.uuid()).getName();
            entries.add(new SelfRanking.Entry(e.uuid(), name == null ? "?" : name, value.apply(e)));
        }
        return new SelfRanking(id, title, entries);
    }

    private static Locale locale(String lang) {
        return "de".equals(lang) ? Locale.GERMANY : Locale.UK;
    }

    /** "3:25" (minutes:seconds). */
    private static String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
