package at.marcelr.mrpvp.stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * SQL of the duel statistics (storage thread only, no Bukkit types - tested against a real SQLite file).
 * <p>
 * When a round ends {@link #record} decides once whether it counts and what it means for each side (win, loss,
 * draw, kill, death) and stores that with the round. Totals per player and kit live in player_stats (with the win
 * streaks, which need the order of the rounds); leaderboards over a time span are summed up from the rounds.
 */
public final class StatsStore {

    /** '' in player_stats.kit = all kits together. */
    public static final String ALL = "";
    private static final long DAY = 24L * 60 * 60 * 1000;
    /** Flags decided when the round is stored (the others come from the monitor). */
    public static final String FLAG_SHORT = "short";
    public static final String FLAG_LIMIT = "limit";
    public static final String REVIEW_PENDING = "PENDING";
    public static final String REVIEW_APPROVED = "APPROVED";
    public static final String REVIEW_REJECTED = "REJECTED";
    /** An admin took the counted round out of the statistics afterwards. */
    public static final String REVIEW_REMOVED = "REMOVED";

    /** One side of a round, as needed for the statistics. */
    public record Side(UUID uuid, String name, double damageDealt, double damageTaken, int arrowsShot, int arrowsHit,
                       int totems) {
    }

    /**
     * A decided round.
     *
     * @param result  Game.Result name (WIN, DRAW, ABORTED)
     * @param reason  Game.EndReason name
     * @param winner  null unless WIN
     * @param hits    hits and arrow hits of both together (a short round without any is conspicuous)
     */
    public record Round(long id, String kit, long endedAt, long durationMillis, String result, String reason,
                        UUID winner, Side one, Side two, int hits) {
    }

    /** Leaderboard sort orders. */
    public enum Sort { WINS, KD, WIN_RATE, BEST_STREAK }

    /** A leaderboard line. */
    public record Entry(UUID uuid, String name, int games, int wins, int losses, int draws, int kills, int deaths,
                        int bestStreak) {
        public double kd() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }

        public double winRate() {
            return games == 0 ? 0 : 100.0 * wins / games;
        }
    }

    private StatsStore() {
    }

    // ------------------------------------------------------------------ recording

    /** What became of a round. */
    public enum Verdict {
        /** in the statistics */
        COUNTED,
        /** conspicuous - waits for an admin (or the automatic decision after some days) */
        REVIEW,
        /** aborted rounds never count */
        NOT_COUNTED
    }

    /** The verdict and every flag of the round (also those found here). */
    public record Recorded(Verdict verdict, Set<String> flags) {
    }

    /**
     * Stores the outcome per side and decides what the round is: conspicuous rounds (a flag in {@code hold}) wait
     * for an admin and do not count yet, all others count right away. Besides the {@code flags} found while
     * playing, two are decided here: {@link #FLAG_SHORT} (shorter than the minimum without a single hit) and
     * {@link #FLAG_LIMIT} (too many counted rounds against the same opponent within 24 hours). All flags are stored
     * with the round.
     */
    public static Recorded record(Connection c, Round r, StatsRules rules, Set<String> flags, Set<String> hold)
            throws SQLException {
        Set<String> all = new TreeSet<>(flags);
        boolean aborted = "ABORTED".equals(r.result());
        if (!aborted) {
            if (r.durationMillis() < rules.minDurationMillis() && r.hits() == 0) {
                all.add(FLAG_SHORT);
            }
            if (rules.maxPerOpponentPerDay() > 0 && countedAgainst(c, r.one().uuid(), r.two().uuid(),
                    r.endedAt() - DAY, r.id()) >= rules.maxPerOpponentPerDay()) {
                all.add(FLAG_LIMIT);
            }
        }
        boolean held = !aborted && all.stream().anyMatch(hold::contains);
        boolean counts = !aborted && !held;
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            for (Side side : new Side[] {r.one(), r.two()}) {
                StatsRules.Score score = "ABORTED".equals(r.result()) ? new StatsRules.Score(null, 0, 0)
                        : rules.score(r.result(), r.reason(), side.uuid().equals(r.winner()));
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE game_players SET outcome = ?, kills = ?, deaths = ? WHERE game_id = ? AND uuid = ?")) {
                    ps.setString(1, score.outcome());
                    ps.setInt(2, score.kills());
                    ps.setInt(3, score.deaths());
                    ps.setLong(4, r.id());
                    ps.setString(5, side.uuid().toString());
                    ps.executeUpdate();
                }
                if (counts) {
                    add(c, side, ALL, score, r);
                    add(c, side, r.kit(), score, r);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE games SET counted = ?, flags = ?, review = ?, duration = ? WHERE id = ?")) {
                ps.setInt(1, counts ? 1 : 0);
                ps.setString(2, all.isEmpty() ? null : String.join(",", all));
                ps.setString(3, held ? REVIEW_PENDING : null);
                ps.setLong(4, r.durationMillis());
                ps.setLong(5, r.id());
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
        return new Recorded(aborted ? Verdict.NOT_COUNTED : held ? Verdict.REVIEW : Verdict.COUNTED, Set.copyOf(all));
    }

    // ------------------------------------------------------------------ review

    /** A round waiting for an admin. */
    public record Pending(long id, String kit, long endedAt, String result, String reason, String flags,
                          UUID one, String oneName, UUID two, String twoName, UUID winner) {
    }

    /** Rounds waiting for a decision, newest first. */
    public static List<Pending> pending(Connection c, int limit) throws SQLException {
        List<Pending> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT g.id, g.kit, g.ended_at, g.result, g.end_reason, "
                + "g.flags, g.winner, p1.uuid, p1.name, p2.uuid, p2.name FROM games g "
                + "JOIN game_players p1 ON p1.game_id = g.id AND p1.side = 1 "
                + "JOIN game_players p2 ON p2.game_id = g.id AND p2.side = 2 "
                + "WHERE g.review = ? ORDER BY g.ended_at DESC LIMIT ?")) {
            ps.setString(1, REVIEW_PENDING);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String winner = rs.getString(7);
                    list.add(new Pending(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), UUID.fromString(rs.getString(8)), rs.getString(9),
                            UUID.fromString(rs.getString(10)), rs.getString(11),
                            winner == null ? null : UUID.fromString(winner)));
                }
            }
        }
        return list;
    }

    public static int pendingCount(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM games WHERE review = ?")) {
            ps.setString(1, REVIEW_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Decides a waiting round: counted (the statistics of both players are built again, so streaks stay in the
     * right order) or not.
     *
     * @param by admin name, "auto" for the automatic decision
     * @return false if the round was not waiting (any more)
     */
    public static boolean decide(Connection c, long id, boolean count, String by, long now) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int changed;
            try (PreparedStatement ps = c.prepareStatement("UPDATE games SET counted = ?, review = ?, reviewed_by = ?, "
                    + "reviewed_at = ? WHERE id = ? AND review = ?")) {
                ps.setInt(1, count ? 1 : 0);
                ps.setString(2, count ? REVIEW_APPROVED : REVIEW_REJECTED);
                ps.setString(3, by);
                ps.setLong(4, now);
                ps.setLong(5, id);
                ps.setString(6, REVIEW_PENDING);
                changed = ps.executeUpdate();
            }
            if (changed == 0) {
                c.rollback();
                return false;
            }
            if (count) {
                try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM game_players WHERE game_id = ?")) {
                    ps.setLong(1, id);
                    List<UUID> players = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            players.add(UUID.fromString(rs.getString(1)));
                        }
                    }
                    for (UUID player : players) {
                        rebuild(c, player);
                    }
                }
            }
            c.commit();
            return true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /**
     * An admin takes a counted round out of the statistics ({@code count} false), or counts a removed or discarded one
     * again. The statistics of both players are built again, so streaks stay in the right order.
     *
     * @param note optional reason, null = none
     * @return false if the round was not in that state (any more)
     */
    public static boolean setCounted(Connection c, long id, boolean count, String by, String note, long now)
            throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int changed;
            try (PreparedStatement ps = c.prepareStatement("UPDATE games SET counted = ?, review = ?, reviewed_by = ?, "
                    + "reviewed_at = ?, review_note = ? WHERE id = ? AND ended_at IS NOT NULL AND "
                    + (count ? "counted = 0 AND review IN (?, ?)" : "counted = 1"))) {
                ps.setInt(1, count ? 1 : 0);
                ps.setString(2, count ? REVIEW_APPROVED : REVIEW_REMOVED);
                ps.setString(3, by);
                ps.setLong(4, now);
                ps.setString(5, note == null || note.isBlank() ? null : note);
                ps.setLong(6, id);
                if (count) {
                    ps.setString(7, REVIEW_REMOVED);
                    ps.setString(8, REVIEW_REJECTED);
                }
                changed = ps.executeUpdate();
            }
            if (changed == 0) {
                c.rollback();
                return false;
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM game_players WHERE game_id = ?")) {
                ps.setLong(1, id);
                List<UUID> players = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        players.add(UUID.fromString(rs.getString(1)));
                    }
                }
                for (UUID player : players) {
                    rebuild(c, player);
                }
            }
            c.commit();
            return true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** Ids of rounds that waited longer than {@code before} (for the automatic decision). */
    public static List<Long> pendingBefore(Connection c, long before) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM games WHERE review = ? AND ended_at < ? ORDER BY ended_at")) {
            ps.setString(1, REVIEW_PENDING);
            ps.setLong(2, before);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    /** The totals of a player built again from their counted rounds (after the last reset), in order. */
    static void rebuild(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM player_stats WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT g.id, g.kit, g.ended_at, "
                + "COALESCE(g.duration, g.ended_at - g.started_at), p.name, p.outcome, p.kills, p.deaths, "
                + "p.damage_dealt, p.damage_taken, p.arrows_shot, p.arrows_hit, p.totems "
                + "FROM games g JOIN game_players p ON p.game_id = g.id "
                + "WHERE p.uuid = ? AND g.counted = 1 AND " + afterReset("p", "g") + " ORDER BY g.ended_at, g.id")) {
            ps.setString(1, player.toString());
            List<Object[]> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Side side = new Side(player, rs.getString(5), rs.getDouble(9), rs.getDouble(10), rs.getInt(11),
                            rs.getInt(12), rs.getInt(13));
                    StatsRules.Score score = new StatsRules.Score(rs.getString(6), rs.getInt(7), rs.getInt(8));
                    Round round = new Round(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), null, null,
                            null, side, side, 0);
                    rows.add(new Object[] {side, score, round});
                }
            }
            for (Object[] row : rows) {
                Round round = (Round) row[2];
                add(c, (Side) row[0], ALL, (StatsRules.Score) row[1], round);
                add(c, (Side) row[0], round.kit(), (StatsRules.Score) row[1], round);
            }
        }
    }

    /** Counted rounds between the two players since {@code since} (without the round {@code except}). */
    static int countedAgainst(Connection c, UUID a, UUID b, long since, long except) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM games g "
                + "JOIN game_players pa ON pa.game_id = g.id AND pa.uuid = ? "
                + "JOIN game_players pb ON pb.game_id = g.id AND pb.uuid = ? "
                + "WHERE g.counted = 1 AND g.ended_at >= ? AND g.id <> ?")) {
            ps.setString(1, a.toString());
            ps.setString(2, b.toString());
            ps.setLong(3, since);
            ps.setLong(4, except);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void add(Connection c, Side side, String kit, StatsRules.Score score, Round r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO player_stats (uuid, kit, name) VALUES (?, ?, ?)")) {
            ps.setString(1, side.uuid().toString());
            ps.setString(2, kit);
            ps.setString(3, side.name());
            ps.executeUpdate();
        }
        boolean win = "WIN".equals(score.outcome());
        boolean loss = "LOSS".equals(score.outcome());
        // A draw keeps the streak as it is.
        String streak = win ? "streak + 1" : loss ? "0" : "streak";
        try (PreparedStatement ps = c.prepareStatement("UPDATE player_stats SET name = ?, games = games + 1, "
                + "wins = wins + ?, losses = losses + ?, draws = draws + ?, kills = kills + ?, deaths = deaths + ?, "
                + "damage_dealt = damage_dealt + ?, damage_taken = damage_taken + ?, arrows_shot = arrows_shot + ?, "
                + "arrows_hit = arrows_hit + ?, totems = totems + ?, duration = duration + ?, "
                + "streak = " + streak + ", best_streak = MAX(best_streak, " + streak + "), last_game = ? "
                + "WHERE uuid = ? AND kit = ?")) {
            int i = 1;
            ps.setString(i++, side.name());
            ps.setInt(i++, win ? 1 : 0);
            ps.setInt(i++, loss ? 1 : 0);
            ps.setInt(i++, "DRAW".equals(score.outcome()) ? 1 : 0);
            ps.setInt(i++, score.kills());
            ps.setInt(i++, score.deaths());
            ps.setDouble(i++, side.damageDealt());
            ps.setDouble(i++, side.damageTaken());
            ps.setInt(i++, side.arrowsShot());
            ps.setInt(i++, side.arrowsHit());
            ps.setInt(i++, side.totems());
            ps.setLong(i++, r.durationMillis());
            ps.setLong(i++, r.id());
            ps.setString(i++, side.uuid().toString());
            ps.setString(i, kit);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ reading

    /** Totals of a player (all kits with {@link #ALL}); empty if they never played a counted round. */
    public static PlayerStats stats(Connection c, UUID player, String name, String kit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM player_stats WHERE uuid = ? AND kit = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return PlayerStats.empty(player, name, kit);
                }
                return new PlayerStats(player, rs.getString("name"), kit, rs.getInt("games"), rs.getInt("wins"),
                        rs.getInt("losses"), rs.getInt("draws"), rs.getInt("kills"), rs.getInt("deaths"),
                        rs.getDouble("damage_dealt"), rs.getDouble("damage_taken"), rs.getInt("arrows_shot"),
                        rs.getInt("arrows_hit"), rs.getInt("totems"), rs.getLong("duration"), rs.getInt("streak"),
                        rs.getInt("best_streak"));
            }
        }
    }

    /** Kits the player has counted rounds with (for the per-kit pages). */
    public static List<String> kitsOf(Connection c, UUID player) throws SQLException {
        List<String> kits = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT kit FROM player_stats WHERE uuid = ? AND kit <> '' ORDER BY games DESC")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    kits.add(rs.getString(1));
                }
            }
        }
        return kits;
    }

    /** Most played value of a games column ("kit" or "arena") in counted rounds, or null. */
    public static String favourite(Connection c, UUID player, String column) throws SQLException {
        if (!column.equals("kit") && !column.equals("arena")) {
            throw new IllegalArgumentException(column);
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT g." + column + ", COUNT(*) n FROM games g "
                + "JOIN game_players p ON p.game_id = g.id WHERE p.uuid = ? AND g.counted = 1 AND " + afterReset("p", "g")
                + " GROUP BY g." + column + " ORDER BY n DESC LIMIT 1")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Head to head: {wins of a, wins of b, draws} in counted rounds. */
    public static int[] headToHead(Connection c, UUID a, UUID b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pa.outcome, COUNT(*) FROM games g "
                + "JOIN game_players pa ON pa.game_id = g.id AND pa.uuid = ? "
                + "JOIN game_players pb ON pb.game_id = g.id AND pb.uuid = ? "
                + "WHERE g.counted = 1 AND " + afterReset("pa", "g") + " AND " + afterReset("pb", "g")
                + " GROUP BY pa.outcome")) {
            ps.setString(1, a.toString());
            ps.setString(2, b.toString());
            int[] result = new int[3];
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String outcome = rs.getString(1);
                    int n = rs.getInt(2);
                    if ("WIN".equals(outcome)) {
                        result[0] += n;
                    } else if ("LOSS".equals(outcome)) {
                        result[1] += n;
                    } else if ("DRAW".equals(outcome)) {
                        result[2] += n;
                    }
                }
            }
            return result;
        }
    }

    /**
     * Everyone with counted rounds, sorted. For ratios only players with at least {@code minGames} rounds.
     *
     * @param kit   {@link #ALL} or a kit id
     * @param since epoch ms (0 = all time)
     */
    public static List<Entry> leaderboard(Connection c, Sort sort, String kit, long since, int minGames) throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT p.uuid, MAX(p.name), COUNT(*), "
                + "SUM(p.outcome = 'WIN'), SUM(p.outcome = 'LOSS'), SUM(p.outcome = 'DRAW'), SUM(p.kills), SUM(p.deaths), "
                + "COALESCE((SELECT best_streak FROM player_stats s WHERE s.uuid = p.uuid AND s.kit = ?), 0) "
                + "FROM game_players p JOIN games g ON g.id = p.game_id "
                + "WHERE g.counted = 1 AND g.ended_at >= ? AND (? = '' OR g.kit = ?) AND " + afterReset("p", "g")
                + " GROUP BY p.uuid")) {
            ps.setString(1, kit);
            ps.setLong(2, since);
            ps.setString(3, kit);
            ps.setString(4, kit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new Entry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4),
                            rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9)));
                }
            }
        }
        boolean ratio = sort == Sort.KD || sort == Sort.WIN_RATE;
        if (ratio) {
            entries.removeIf(e -> e.games() < minGames);
        }
        Comparator<Entry> order = switch (sort) {
            case WINS -> Comparator.comparingInt(Entry::wins);
            case KD -> Comparator.comparingDouble(Entry::kd);
            case WIN_RATE -> Comparator.comparingDouble(Entry::winRate);
            case BEST_STREAK -> Comparator.comparingInt(Entry::bestStreak);
        };
        entries.sort(order.reversed().thenComparing(Comparator.comparingInt(Entry::games).reversed())
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return entries;
    }

    /** Rounds are only counted after the player's (or everyone's) last statistics reset. */
    private static String afterReset(String player, String game) {
        return game + ".ended_at > COALESCE((SELECT at FROM stats_resets r WHERE r.uuid = " + player + ".uuid), 0)"
                + " AND " + game + ".ended_at > COALESCE((SELECT at FROM stats_resets r WHERE r.uuid = '*'), 0)";
    }

    /** Starts the statistics of a player (or everyone with null) from zero. The rounds themselves stay. */
    public static void reset(Connection c, UUID player, long now) throws SQLException {
        String who = player == null ? "*" : player.toString();
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO stats_resets (uuid, at) VALUES (?, ?)")) {
            ps.setString(1, who);
            ps.setLong(2, now);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(player == null ? "DELETE FROM player_stats"
                : "DELETE FROM player_stats WHERE uuid = ?")) {
            if (player != null) {
                ps.setString(1, who);
            }
            ps.executeUpdate();
        }
    }
}
