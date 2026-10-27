package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Messages;
import at.marcelr.mrpvp.Settings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Notices conspicuous rounds when they end and keeps them for a day: one-sided series of the same two players
 * (statistics farming, see {@link FarmingCheck}), leaving mid-fight, many tries to leave the arena, a single hit far stronger than a kit
 * allows, a long round without any hit. Shown in the admin menu / MRadmin (server health, web) and - if wanted -
 * in MRadmin's Discord admin log. Main thread only.
 */
public final class Monitor {

    /** What was noticed - stored as text, never rename. */
    public enum Type { FARMING, QUIT, BORDER, HIT, IDLE }

    /** One noticed round. */
    public record Alert(long time, long gameId, Type type, String one, String two, String detail) {
    }

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final int MAX_ALERTS = 200;

    private final MRPvp plugin;
    private final Deque<Alert> alerts = new ArrayDeque<>();
    private final FarmingCheck farming = new FarmingCheck();
    /** Pairs whose farming series was already reported (once per series; further rounds are only flagged). */
    private final java.util.Set<String> farmingReported = new java.util.HashSet<>();

    public Monitor(MRPvp plugin) {
        this.plugin = plugin;
    }

    /** Checks a round that just ended; returns what was noticed (lower case type names, for the review). */
    java.util.Set<String> check(Game game, Game.EndReason reason, Fighter winner) {
        java.util.Set<String> flags = new java.util.TreeSet<>();
        Settings.MonitorSettings s = plugin.settings().monitor();
        if (!s.enabled()) {
            return flags;
        }
        long now = System.currentTimeMillis();
        String one = game.one().name();
        String two = game.two().name();

        String pair = FarmingCheck.key(game.one().uuid(), game.two().uuid());
        int series = farming.add(now, game.one().uuid(), game.one().damageDealt(), game.two().uuid(),
                game.two().damageDealt(), winner == null ? null : winner.uuid(), s.farming());
        if (series == 0) {
            farmingReported.remove(pair);
        } else if (farmingReported.add(pair)) {
            add(flags, now, game, Type.FARMING, one, two, String.valueOf(series));
        } else {
            // Reported once, but every further round of the series is conspicuous too.
            flags.add(Type.FARMING.name().toLowerCase(Locale.ROOT));
        }
        if (reason == Game.EndReason.QUIT && winner != null) {
            Fighter quitter = winner == game.one() ? game.two() : game.one();
            add(flags, now, game, Type.QUIT, one, two, quitter.name());
        }
        for (Fighter f : List.of(game.one(), game.two())) {
            if (f.borders() >= s.borderTries()) {
                add(flags, now, game, Type.BORDER, one, two, f.name() + " " + f.borders());
            }
            if (f.maxHit() / 2 >= s.maxHitHearts() && !"MACE".equals(f.maxHitWeapon())) {
                add(flags, now, game, Type.HIT, one, two, f.name() + " " + Messages.decimal(f.maxHit() / 2, 1) + "❤ "
                        + String.valueOf(f.maxHitWeapon()).toLowerCase(Locale.ROOT));
            }
        }
        if (game.elapsed() >= s.idleSeconds() * 1000L && game.one().hits() + game.two().hits() == 0
                && game.one().arrowsHit() + game.two().arrowsHit() == 0) {
            add(flags, now, game, Type.IDLE, one, two, GameManager.clock(game.elapsed()));
        }
        return flags;
    }

    private void add(java.util.Set<String> flags, long now, Game game, Type type, String one, String two, String detail) {
        flags.add(type.name().toLowerCase(Locale.ROOT));
        Alert alert = new Alert(now, game.id(), type, one, two, detail);
        alerts.addFirst(alert);
        while (alerts.size() > MAX_ALERTS) {
            alerts.removeLast();
        }
        String key = "monitor-" + type.name().toLowerCase(Locale.ROOT);
        Object[] values = {"id", game.id(), "one", one, "two", two, "detail", detail};
        // Console always English, Discord in the server's language.
        plugin.getLogger().info("Monitor: " + plugin.messages().console(key, values));
        if (plugin.settings().monitor().discord()) {
            plugin.admin().adminLog(plugin.messages().server(() -> plugin.messages().plain(key, values)));
        }
    }

    /** Alerts of the last 24 hours, newest first. */
    public List<Alert> recent() {
        long now = System.currentTimeMillis();
        Iterator<Alert> it = alerts.iterator();
        while (it.hasNext()) {
            if (now - it.next().time() > DAY) {
                it.remove();
            }
        }
        return new ArrayList<>(alerts);
    }
}
