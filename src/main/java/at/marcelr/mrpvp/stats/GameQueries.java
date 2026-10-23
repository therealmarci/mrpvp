package at.marcelr.mrpvp.stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reading rounds for the menus and the web: lists, one round with both players, its combat log. Storage thread. */
public final class GameQueries {

    /** One side of a round as stored. */
    public record PlayerLine(UUID uuid, String name, int side, Double healthEnd, double damageDealt, double damageTaken,
                             int hits, int crits, int arrowsShot, int arrowsHit, int totems, int pearls, String outcome,
                             int kills, int deaths) {
    }

    /**
     * A round. {@code endedAt}, {@code result} and {@code reason} are null while it runs.
     */
    public record GameRow(long id, String arena, int slot, String kit, long startedAt, Long endedAt, String result, String reason,
                          UUID winner, String stoppedBy, boolean counted, String review, String flags,
                          String reviewedBy, List<PlayerLine> players) {

        /** Waits for an admin. */
        public boolean pending() {
            return "PENDING".equals(review);
        }

        public PlayerLine side(int side) {
            return players.stream().filter(p -> p.side() == side).findFirst().orElse(null);
        }

        public PlayerLine of(UUID player) {
            return players.stream().filter(p -> p.uuid().equals(player)).findFirst().orElse(null);
        }

        public long duration() {
            return endedAt == null ? 0 : endedAt - startedAt;
        }
    }

    /** A combat log line; t = ms since the fight started. */
    public record EventRow(long t, String type, UUID actor, UUID target, Double damage, String cause, String weapon,
                           double x, double y, double z) {
    }

    /** Filter for {@link #list}. */
    public enum ResultFilter { ALL, WIN, DRAW, ABORTED }

    private GameQueries() {
    }

    /**
     * Rounds, newest first.
     *
     * @param player only rounds of this player, or null
     */
    public static List<GameRow> list(Connection c, UUID player, ResultFilter filter, int offset, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT g.* FROM games g WHERE g.ended_at IS NOT NULL");
        if (player != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = g.id AND p.uuid = ?)");
        }
        if (filter != ResultFilter.ALL) {
            sql.append(" AND g.result = ?");
        }
        sql.append(" ORDER BY g.id DESC LIMIT ? OFFSET ?");
        List<GameRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            if (player != null) {
                ps.setString(i++, player.toString());
            }
            if (filter != ResultFilter.ALL) {
                ps.setString(i++, filter.name());
            }
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(row(rs, List.of()));
                }
            }
        }
        List<GameRow> withPlayers = new ArrayList<>();
        for (GameRow row : rows) {
            withPlayers.add(new GameRow(row.id(), row.arena(), row.slot(), row.kit(), row.startedAt(), row.endedAt(), row.result(),
                    row.reason(), row.winner(), row.stoppedBy(), row.counted(), row.review(), row.flags(), row.reviewedBy(),
                    players(c, row.id())));
        }
        return withPlayers;
    }

    /** One round with both players, or null. */
    public static GameRow get(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM games WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs, players(c, id)) : null;
            }
        }
    }

    /** A sampled position for the mini map; t = ms since the fight started. */
    public record PositionRow(long t, int side, double x, double y, double z, float yaw, double health) {
    }

    public static List<PositionRow> positions(Connection c, long id) throws SQLException {
        List<PositionRow> positions = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM positions WHERE game_id = ? ORDER BY t, side")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    positions.add(new PositionRow(rs.getLong("t"), rs.getInt("side"), rs.getDouble("x"),
                            rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getDouble("health")));
                }
            }
        }
        return positions;
    }

    public static List<EventRow> events(Connection c, long id) throws SQLException {
        List<EventRow> events = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM events WHERE game_id = ? ORDER BY t")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double damage = rs.getDouble("damage");
                    Double d = rs.wasNull() ? null : damage;
                    events.add(new EventRow(rs.getLong("t"), rs.getString("type"), uuid(rs.getString("actor")),
                            uuid(rs.getString("target")), d, rs.getString("cause"), rs.getString("weapon"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")));
                }
            }
        }
        return events;
    }

    private static List<PlayerLine> players(Connection c, long id) throws SQLException {
        List<PlayerLine> players = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM game_players WHERE game_id = ? ORDER BY side")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double health = rs.getDouble("health_end");
                    Double healthEnd = rs.wasNull() ? null : health;
                    players.add(new PlayerLine(UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                            rs.getInt("side"), healthEnd, rs.getDouble("damage_dealt"), rs.getDouble("damage_taken"),
                            rs.getInt("hits"), rs.getInt("crits"), rs.getInt("arrows_shot"), rs.getInt("arrows_hit"),
                            rs.getInt("totems"), rs.getInt("pearls"), rs.getString("outcome"), rs.getInt("kills"),
                            rs.getInt("deaths")));
                }
            }
        }
        return players;
    }

    private static GameRow row(ResultSet rs, List<PlayerLine> players) throws SQLException {
        long ended = rs.getLong("ended_at");
        Long endedAt = rs.wasNull() ? null : ended;
        return new GameRow(rs.getLong("id"), rs.getString("arena"), rs.getInt("slot"), rs.getString("kit"), rs.getLong("started_at"),
                endedAt, rs.getString("result"), rs.getString("end_reason"), uuid(rs.getString("winner")),
                rs.getString("stopped_by"), rs.getInt("counted") != 0, rs.getString("review"), rs.getString("flags"),
                rs.getString("reviewed_by"), players);
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    /**
     * Deletes old details: combat log, positions and end states older than {@code days}, given-back states older than
     * {@code snapshotDays}. Rounds and owed states always stay.
     *
     * @return number of deleted rows
     */
    public static int cleanUp(Connection c, long now, int days, int snapshotDays) throws SQLException {
        long details = now - days * 86_400_000L;
        long snapshots = now - snapshotDays * 86_400_000L;
        int deleted = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM events WHERE game_id IN (SELECT id FROM games WHERE ended_at < ?)")) {
            ps.setLong(1, details);
            deleted += ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM positions WHERE game_id IN (SELECT id FROM games WHERE ended_at < ?)")) {
            ps.setLong(1, details);
            deleted += ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM snapshots WHERE phase = 'END' AND taken_at < ?")) {
            ps.setLong(1, details);
            deleted += ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM snapshots WHERE phase = 'JOIN' AND restored_at IS NOT NULL AND taken_at < ?")) {
            ps.setLong(1, snapshots);
            deleted += ps.executeUpdate();
        }
        return deleted;
    }
}
