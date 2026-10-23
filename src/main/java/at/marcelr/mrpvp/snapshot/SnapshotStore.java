package at.marcelr.mrpvp.snapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQL for the snapshots table. Every method takes the connection of the storage thread, so callers decide whether
 * to wait ({@code Database.call}) or not ({@code Database.submit}).
 */
public final class SnapshotStore {

    private static final String COLUMNS = "uuid, name, purpose, game_id, phase, taken_at, inventory, armor, offhand, "
            + "enderchest, toolbar_belts, level, exp, total_exp, health, absorption, food, saturation, exhaustion, "
            + "air, fire_ticks, effects, gamemode, flying, allow_flight, world, x, y, z, yaw, pitch, restored_at";

    private SnapshotStore() {
    }

    /** Stores a snapshot and returns it with its new id. */
    public static Snapshot insert(Connection c, Snapshot s) throws SQLException {
        String marks = "?" + ", ?".repeat(COLUMNS.split(",").length - 1);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO snapshots (" + COLUMNS + ") VALUES (" + marks + ")",
                Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, s.uuid().toString());
            ps.setString(i++, s.name());
            ps.setString(i++, s.purpose().name());
            if (s.gameId() == null) {
                ps.setNull(i++, Types.INTEGER);
            } else {
                ps.setLong(i++, s.gameId());
            }
            ps.setString(i++, s.phase().name());
            ps.setLong(i++, s.takenAt());
            ps.setBytes(i++, s.inventory());
            ps.setBytes(i++, s.armor());
            ps.setBytes(i++, s.offhand());
            ps.setBytes(i++, s.enderchest());
            ps.setBytes(i++, s.toolbarBelts());
            ps.setInt(i++, s.level());
            ps.setFloat(i++, s.exp());
            ps.setInt(i++, s.totalExp());
            ps.setDouble(i++, s.health());
            ps.setDouble(i++, s.absorption());
            ps.setInt(i++, s.food());
            ps.setFloat(i++, s.saturation());
            ps.setFloat(i++, s.exhaustion());
            ps.setInt(i++, s.air());
            ps.setInt(i++, s.fireTicks());
            ps.setString(i++, s.effects());
            ps.setString(i++, s.gameMode());
            ps.setInt(i++, s.flying() ? 1 : 0);
            ps.setInt(i++, s.allowFlight() ? 1 : 0);
            ps.setString(i++, s.world());
            ps.setDouble(i++, s.x());
            ps.setDouble(i++, s.y());
            ps.setDouble(i++, s.z());
            ps.setFloat(i++, s.yaw());
            ps.setFloat(i++, s.pitch());
            if (s.restoredAt() == null) {
                ps.setNull(i, Types.INTEGER);
            } else {
                ps.setLong(i, s.restoredAt());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No id for the new snapshot");
                }
                return s.withId(keys.getLong(1));
            }
        }
    }

    /** The states a player still has to get back, oldest first (normally none or one). */
    public static List<Snapshot> owed(Connection c, UUID player) throws SQLException {
        return query(c, "WHERE uuid = ? AND phase = 'JOIN' AND restored_at IS NULL ORDER BY id", player.toString());
    }

    /** Marks a snapshot as given back. False if it was already marked (by someone else in the meantime). */
    public static boolean markRestored(Connection c, long id, long time) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE snapshots SET restored_at = ? WHERE id = ? AND restored_at IS NULL")) {
            ps.setLong(1, time);
            ps.setLong(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    /** Takes back {@link #markRestored} - only when giving back failed right after marking. */
    public static void unmarkRestored(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE snapshots SET restored_at = NULL WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public static Snapshot byId(Connection c, long id) throws SQLException {
        List<Snapshot> found = query(c, "WHERE id = ?", id);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** The states of the players of a round (JOIN and END), in the order they were taken. */
    public static List<Snapshot> ofGame(Connection c, long gameId) throws SQLException {
        return query(c, "WHERE game_id = ? ORDER BY id", gameId);
    }

    /** The newest snapshots of a player (all purposes), newest first. */
    public static List<Snapshot> latest(Connection c, UUID player, int limit) throws SQLException {
        return query(c, "WHERE uuid = ? ORDER BY id DESC LIMIT " + Math.max(1, limit), player.toString());
    }

    /** Number of states still owed to players (shown in /mrpvp debug). */
    public static int owedCount(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM snapshots WHERE phase = 'JOIN' AND restored_at IS NULL")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static List<Snapshot> query(Connection c, String where, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, " + COLUMNS + " FROM snapshots " + where)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            List<Snapshot> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(read(rs));
                }
            }
            return result;
        }
    }

    private static Snapshot read(ResultSet rs) throws SQLException {
        long gameId = rs.getLong("game_id");
        Long game = rs.wasNull() ? null : gameId;
        long restored = rs.getLong("restored_at");
        Long restoredAt = rs.wasNull() ? null : restored;
        return new Snapshot(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                Snapshot.Purpose.valueOf(rs.getString("purpose")),
                game,
                Snapshot.Phase.valueOf(rs.getString("phase")),
                rs.getLong("taken_at"),
                rs.getBytes("inventory"),
                rs.getBytes("armor"),
                rs.getBytes("offhand"),
                rs.getBytes("enderchest"),
                rs.getBytes("toolbar_belts"),
                rs.getInt("level"),
                rs.getFloat("exp"),
                rs.getInt("total_exp"),
                rs.getDouble("health"),
                rs.getDouble("absorption"),
                rs.getInt("food"),
                rs.getFloat("saturation"),
                rs.getFloat("exhaustion"),
                rs.getInt("air"),
                rs.getInt("fire_ticks"),
                rs.getString("effects"),
                rs.getString("gamemode"),
                rs.getInt("flying") != 0,
                rs.getInt("allow_flight") != 0,
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                restoredAt);
    }
}
