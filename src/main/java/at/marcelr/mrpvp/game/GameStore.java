package at.marcelr.mrpvp.game;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;

/** SQL for games, game_players, events and positions (storage thread only). */
public final class GameStore {

    private GameStore() {
    }

    /**
     * A new round; returns its Game-ID.
     *
     * @param match the match it belongs to, null for the first round (the match then gets this round's id)
     */
    static long insert(Connection c, String arena, int arenaVersion, int slot, String kit, UUID inviter,
                       long startedAt, String pluginVersion, Long match, int round) throws SQLException {
        long id;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO games (arena, arena_version, slot, kit, inviter, "
                + "started_at, plugin_version, round) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, arena);
            ps.setInt(2, arenaVersion);
            ps.setInt(3, slot);
            ps.setString(4, kit);
            ps.setString(5, inviter.toString());
            ps.setLong(6, startedAt);
            ps.setString(7, pluginVersion);
            ps.setInt(8, round);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No id for the new game");
                }
                id = keys.getLong(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE games SET match_id = ? WHERE id = ?")) {
            ps.setLong(1, match == null ? id : match);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        return id;
    }

    /** The result of a whole match. */
    static void finishMatch(Connection c, Match match, int rounds, UUID one, UUID two, UUID winner, String reason,
                            long startedAt, long endedAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO matches (id, kit, first_to, rounds, one, "
                + "two, wins_one, wins_two, winner, reason, started_at, ended_at, options) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            DuelOptions o = match.options();
            ps.setLong(1, match.id());
            ps.setString(2, match.kit().id());
            ps.setInt(3, o.wins());
            ps.setInt(4, rounds);
            ps.setString(5, one.toString());
            ps.setString(6, two.toString());
            ps.setInt(7, match.wins(1));
            ps.setInt(8, match.wins(2));
            ps.setString(9, winner == null ? null : winner.toString());
            ps.setString(10, reason);
            ps.setLong(11, startedAt);
            ps.setLong(12, endedAt);
            ps.setString(13, "minutes=" + o.minutes() + ";hunger=" + o.hunger() + ";regen=" + o.regen());
            ps.executeUpdate();
        }
    }

    /** Stores the result and both players' numbers (one transaction). */
    static void finish(Connection c, long id, long endedAt, Game.Result result, Game.EndReason reason, UUID winner,
                       String stoppedBy, Fighter... fighters) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement ps = c.prepareStatement("UPDATE games SET ended_at = ?, result = ?, end_reason = ?, "
                    + "winner = ?, stopped_by = ? WHERE id = ?")) {
                ps.setLong(1, endedAt);
                ps.setString(2, result.name());
                ps.setString(3, reason.name());
                ps.setString(4, winner == null ? null : winner.toString());
                ps.setString(5, stoppedBy);
                ps.setLong(6, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO game_players (game_id, uuid, name, "
                    + "side, health_end, damage_dealt, damage_taken, hits, crits, arrows_shot, arrows_hit, totems, pearls) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Fighter f : fighters) {
                    ps.setLong(1, id);
                    ps.setString(2, f.uuid().toString());
                    ps.setString(3, f.name());
                    ps.setInt(4, f.side());
                    if (f.healthEnd() == null) {
                        ps.setNull(5, Types.REAL);
                    } else {
                        ps.setDouble(5, f.healthEnd());
                    }
                    ps.setDouble(6, f.damageDealt());
                    ps.setDouble(7, f.damageTaken());
                    ps.setInt(8, f.hits());
                    ps.setInt(9, f.crits());
                    ps.setInt(10, f.arrowsShot());
                    ps.setInt(11, f.arrowsHit());
                    ps.setInt(12, f.totems());
                    ps.setInt(13, f.pearls());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    static void write(Connection c, long gameId, CombatLog.Batch batch) throws SQLException {
        if (batch.isEmpty()) {
            return;
        }
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO events (game_id, t, type, actor, target, "
                    + "damage, cause, weapon, x, y, z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (CombatLog.Event e : batch.events()) {
                    ps.setLong(1, gameId);
                    ps.setLong(2, e.t());
                    ps.setString(3, e.type().name());
                    ps.setString(4, e.actor() == null ? null : e.actor().toString());
                    ps.setString(5, e.target() == null ? null : e.target().toString());
                    if (e.damage() == null) {
                        ps.setNull(6, Types.REAL);
                    } else {
                        ps.setDouble(6, e.damage());
                    }
                    ps.setString(7, e.cause());
                    ps.setString(8, e.weapon());
                    ps.setDouble(9, e.x());
                    ps.setDouble(10, e.y());
                    ps.setDouble(11, e.z());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO positions (game_id, t, side, x, y, z, yaw, "
                    + "health) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (CombatLog.Position p : batch.positions()) {
                    ps.setLong(1, gameId);
                    ps.setLong(2, p.t());
                    ps.setInt(3, p.side());
                    ps.setDouble(4, p.x());
                    ps.setDouble(5, p.y());
                    ps.setDouble(6, p.z());
                    ps.setFloat(7, p.yaw());
                    ps.setDouble(8, p.health());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** What a player chose when they invited last time (kit null = none yet). */
    public record Prefs(String kit, String arena, DuelOptions options) {
    }

    static Prefs prefs(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM invite_prefs WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Prefs(rs.getString("kit"), rs.getString("arena"), new DuelOptions(rs.getInt("wins"),
                        rs.getInt("minutes"), rs.getInt("hunger") != 0, rs.getInt("regen") != 0));
            }
        }
    }

    static void savePrefs(Connection c, UUID player, Prefs p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO invite_prefs (uuid, kit, arena, wins, "
                + "minutes, hunger, regen) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, p.kit());
            ps.setString(3, p.arena());
            ps.setInt(4, p.options().wins());
            ps.setInt(5, p.options().minutes());
            ps.setInt(6, p.options().hunger() ? 1 : 0);
            ps.setInt(7, p.options().regen() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /** Rounds without end after a crash: marked as ABORTED / CRASH. Returns how many. */
    static int abortUnfinished(Connection c, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE games SET ended_at = ?, result = 'ABORTED', "
                + "end_reason = 'CRASH' WHERE ended_at IS NULL")) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        }
    }
}
