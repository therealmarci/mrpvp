package at.marcelr.mrpvp.stats;

import at.marcelr.mrpvp.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsStoreTest {

    private static final StatsRules RULES = new StatsRules(10_000, 2, true, false, true, 3);
    private static final long HOUR = 3_600_000L;
    private static final Set<String> HOLD = Set.of(StatsStore.FLAG_SHORT, StatsStore.FLAG_LIMIT, "farming");

    @TempDir
    File folder;
    private Database db;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private long nextId = 1;

    @BeforeEach
    void open() throws SQLException {
        db = new Database(new File(folder, "mrpvp.db"), Logger.getLogger("test"));
        db.open();
    }

    @AfterEach
    void close() {
        db.close();
    }

    /** Inserts a finished round (as GameStore does) and records it. */
    private boolean round(UUID one, UUID two, String result, String reason, UUID winner, long endedAt, long duration)
            throws SQLException {
        return verdict(one, two, result, reason, winner, endedAt, duration, 5, Set.of()) == StatsStore.Verdict.COUNTED;
    }

    private StatsStore.Verdict verdict(UUID one, UUID two, String result, String reason, UUID winner, long endedAt,
                                       long duration, int hits, Set<String> flags)
            throws SQLException {
        long id = nextId++;
        return db.call(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO games (id, kit, started_at, ended_at, result, "
                    + "end_reason, winner, plugin_version) VALUES (?, 'klassisch', ?, ?, ?, ?, ?, 'test')")) {
                ps.setLong(1, id);
                ps.setLong(2, endedAt - duration);
                ps.setLong(3, endedAt);
                ps.setString(4, result);
                ps.setString(5, reason);
                ps.setString(6, winner == null ? null : winner.toString());
                ps.executeUpdate();
            }
            int side = 1;
            for (UUID u : new UUID[] {one, two}) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO game_players (game_id, uuid, name, side, "
                        + "damage_dealt, damage_taken, hits, crits, arrows_shot, arrows_hit, totems, pearls) "
                        + "VALUES (?, ?, ?, ?, 10, 5, 3, 1, 4, 2, 0, 0)")) {
                    ps.setLong(1, id);
                    ps.setString(2, u.toString());
                    ps.setString(3, name(u));
                    ps.setInt(4, side++);
                    ps.executeUpdate();
                }
            }
            return StatsStore.record(conn, new StatsStore.Round(id, "klassisch", endedAt, duration, result, reason,
                    winner, side(one), side(two), hits), RULES, flags, HOLD).verdict();
        });
    }

    private StatsStore.Side side(UUID u) {
        return new StatsStore.Side(u, name(u), 10, 5, 4, 2, 0);
    }

    private String name(UUID u) {
        return u.equals(a) ? "Alice" : u.equals(b) ? "Bob" : "Carl";
    }

    private PlayerStats stats(UUID u) throws SQLException {
        return db.call(conn -> StatsStore.stats(conn, u, name(u), StatsStore.ALL));
    }

    @Test
    void scoring() {
        assertEquals("WIN", RULES.score("WIN", "KILL", true).outcome());
        assertEquals(1, RULES.score("WIN", "KILL", true).kills());
        assertEquals(1, RULES.score("WIN", "KILL", false).deaths());
        assertEquals(1, RULES.score("WIN", "BORDER", true).kills(), "border counts as kill");
        assertEquals(1, RULES.score("WIN", "QUIT", false).deaths(), "quit counts as death");
        assertEquals(0, RULES.score("WIN", "SURRENDER", false).deaths(), "surrender does not (in these rules)");
        assertEquals(0, RULES.score("WIN", "SURRENDER", true).kills());
        assertEquals("DRAW", RULES.score("DRAW", "TIMEOUT", false).outcome());
    }

    @Test
    void streaksAndTotals() throws SQLException {
        long t = 100 * HOUR;
        assertTrue(round(a, b, "WIN", "KILL", a, t, 60_000));
        assertTrue(round(a, c, "WIN", "KILL", a, t + 1, 60_000));
        assertTrue(round(a, c, "DRAW", "TIMEOUT", null, t + 2, 600_000));
        // a day later - otherwise the third round against Carl within 24 h would not count
        assertTrue(round(c, a, "WIN", "KILL", c, t + 25 * HOUR, 60_000));

        PlayerStats alice = stats(a);
        assertEquals(4, alice.games());
        assertEquals(2, alice.wins());
        assertEquals(1, alice.losses());
        assertEquals(1, alice.draws());
        assertEquals(2, alice.kills());
        assertEquals(1, alice.deaths());
        assertEquals(0, alice.streak(), "loss ends the streak");
        assertEquals(2, alice.bestStreak(), "the draw kept it at 2");
        assertEquals(2.0, alice.kd());
        assertEquals(40, alice.damageDealt());
    }

    @Test
    void conspicuousRoundsWaitForReview() throws SQLException {
        long t = 100 * HOUR;
        assertEquals(StatsStore.Verdict.REVIEW, verdict(a, b, "WIN", "KILL", a, t, 5_000, 0, Set.of()),
                "short without a hit");
        assertTrue(round(a, b, "WIN", "KILL", a, t + 5, 5_000), "short with hits (sumo) is fine");
        assertTrue(round(a, b, "WIN", "KILL", a, t + 10, 60_000));
        assertEquals(StatsStore.Verdict.REVIEW, verdict(a, b, "WIN", "KILL", a, t + 30, 60_000, 5, Set.of()),
                "third against the same opponent within a day");
        assertTrue(round(a, c, "WIN", "KILL", a, t + 40, 60_000), "another opponent is fine");
        assertEquals(StatsStore.Verdict.REVIEW, verdict(a, c, "WIN", "KILL", a, t + 50, 60_000, 5, Set.of("farming")));
        assertEquals(StatsStore.Verdict.COUNTED, verdict(a, c, "WIN", "KILL", a, t + 60, 60_000, 5, Set.of("quit")),
                "a flag that is only reported");
        assertEquals(StatsStore.Verdict.NOT_COUNTED, verdict(a, c, "ABORTED", "SHUTDOWN", null, t + 70, 60_000, 5, Set.of()));
        assertEquals(4, stats(a).games());

        List<StatsStore.Pending> pending = db.call(conn -> StatsStore.pending(conn, 10));
        assertEquals(3, pending.size());
        assertEquals("farming", pending.getFirst().flags(), "newest first");
        assertEquals(List.of(1L), db.call(conn -> StatsStore.pendingBefore(conn, t + 1)));

        // The first round (short, a won) counts after all - Alice's statistics are built again in order.
        assertTrue(db.<Boolean>call(conn -> StatsStore.decide(conn, 1, true, "Admin", t + HOUR)));
        assertFalse(db.<Boolean>call(conn -> StatsStore.decide(conn, 1, false, "Admin", t + HOUR)), "already decided");
        assertTrue(db.<Boolean>call(conn -> StatsStore.decide(conn, pending.getFirst().id(), false, "Admin", t + HOUR)));
        assertEquals(5, stats(a).games());
        assertEquals(5, stats(a).wins());
        assertEquals(5, stats(a).bestStreak());
        assertEquals(5, stats(b).losses() + stats(c).losses());
        assertEquals(1, db.call(StatsStore::pendingCount));
    }

    @Test
    void leaderboardHeadToHeadAndReset() throws SQLException {
        long t = 100 * HOUR;
        round(a, b, "WIN", "KILL", a, t, 60_000);
        round(b, c, "WIN", "KILL", b, t + 1, 60_000);
        round(c, b, "WIN", "KILL", c, t + 2, 60_000);
        round(a, c, "WIN", "KILL", a, t + 3, 60_000);

        List<StatsStore.Entry> wins = db.call(conn -> StatsStore.leaderboard(conn, StatsStore.Sort.WINS, StatsStore.ALL, 0, 3));
        assertEquals(List.of("Alice", "Bob", "Carl"), wins.stream().map(StatsStore.Entry::name).toList());
        assertEquals(2, wins.getFirst().wins());

        List<StatsStore.Entry> kd = db.call(conn -> StatsStore.leaderboard(conn, StatsStore.Sort.KD, StatsStore.ALL, 0, 3));
        assertEquals(List.of("Bob", "Carl"), kd.stream().map(StatsStore.Entry::name).toList(), "Alice has only 2 rounds");

        List<StatsStore.Entry> recent = db.call(conn -> StatsStore.leaderboard(conn, StatsStore.Sort.WINS, StatsStore.ALL, t + 2, 0));
        // the last two rounds: Carl beat Bob, Alice beat Carl - one win each, more rounds first on a tie
        assertEquals(List.of("Carl", "Alice", "Bob"), recent.stream().map(StatsStore.Entry::name).toList());
        assertEquals(1, recent.getFirst().wins());
        assertEquals(1, recent.getFirst().losses());

        assertArrayEquals(new int[] {0, 1, 0}, db.call(conn -> StatsStore.headToHead(conn, b, a)));
        assertArrayEquals(new int[] {1, 1, 0}, db.call(conn -> StatsStore.headToHead(conn, b, c)));

        db.call(conn -> {
            StatsStore.reset(conn, a, t + 10);
            return null;
        });
        assertEquals(0, stats(a).games());
        List<StatsStore.Entry> after = db.call(conn -> StatsStore.leaderboard(conn, StatsStore.Sort.WINS, StatsStore.ALL, 0, 0));
        assertEquals(List.of("Bob", "Carl"), after.stream().map(StatsStore.Entry::name).toList());
        assertEquals(1, stats(b).wins(), "Bob keeps his");
    }
}
