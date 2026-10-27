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
 * Notices conspicuous rounds when they end and keeps them for a day: the same two players again and again
 * (statistics farming), leaving mid-fight, many tries to leave the arena, a single hit far stronger than a kit
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
    private static final long HOUR = 60L * 60 * 1000;
    private static final int MAX_ALERTS = 200;

    private final MRPvp plugin;
    private final Deque<Alert> alerts = new ArrayDeque<>();
    /** (time, "uuidA|uuidB") of rounds of the last hour, for farming */
    private final Deque<Object[]> pairs = new ArrayDeque<>();

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

        String pair = pairKey(game);
        pairs.add(new Object[] {now, pair});
        pairs.removeIf(p -> now - (long) p[0] > HOUR);
        long together = pairs.stream().filter(p -> p[1].equals(pair)).count();
        if (together == s.samePairPerHour()) {
            add(flags, now, game, Type.FARMING, one, two, String.valueOf(together));
        } else if (together > s.samePairPerHour()) {
            // Reported once, but every further round of the two is conspicuous too.
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

    private static String pairKey(Game game) {
        String a = game.one().uuid().toString();
        String b = game.two().uuid().toString();
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
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
