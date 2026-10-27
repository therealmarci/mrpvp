package at.marcelr.mrpvp.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * The tables. A fresh database gets everything from {@link #create}; an older one is brought up step by step in
 * {@link #migrate}. Raise {@link #VERSION} and add a step whenever a table changes - never change a released step.
 */
final class Schema {

    /** Version of the tables (meta row "schema"). */
    static final int VERSION = 6;

    private Schema() {
    }

    static void migrate(Connection c, Logger log) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        int version = 0;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE name = 'schema'")) {
            if (rs.next()) {
                version = Integer.parseInt(rs.getString(1));
            }
        }
        if (version > VERSION) {
            log.warning("The database was used by a newer MRpvp (table version " + version
                    + "). Older versions may not understand everything in it.");
            return;
        }
        if (version == VERSION) {
            return;
        }
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            if (version == 0) {
                create(c);
            }
            if (version < 2) {
                createCombat(c);
            }
            if (version < 3) {
                createStats(c);
            }
            if (version < 4) {
                createMatches(c);
            }
            if (version < 5) {
                createReview(c);
            }
            if (version < 6) {
                createReviewNote(c);
            }
            try (Statement st = c.createStatement()) {
                st.execute("INSERT OR REPLACE INTO meta (name, value) VALUES ('schema', '" + VERSION + "')");
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
        if (version > 0) {
            log.info("Database tables updated from version " + version + " to " + VERSION + ".");
        }
    }

    private static void create(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // One round = one game; the id is the Game-ID players and admins see (#1042).
            st.execute("CREATE TABLE games ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "match_id INTEGER, "
                    + "arena TEXT, arena_version INTEGER, slot INTEGER, kit TEXT, "
                    + "inviter TEXT, "
                    + "started_at INTEGER NOT NULL, ended_at INTEGER, "
                    + "result TEXT, end_reason TEXT, winner TEXT, stopped_by TEXT, "
                    + "plugin_version TEXT NOT NULL)");

            // A player's full state. phase JOIN = before entering (also what they get back), END = when it ended.
            // purpose GAME | EDITOR | DEBUG; game_id only for GAME.
            st.execute("CREATE TABLE snapshots ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "uuid TEXT NOT NULL, name TEXT NOT NULL, "
                    + "purpose TEXT NOT NULL, game_id INTEGER REFERENCES games(id) ON DELETE CASCADE, "
                    + "phase TEXT NOT NULL, taken_at INTEGER NOT NULL, "
                    + "inventory BLOB NOT NULL, armor BLOB NOT NULL, offhand BLOB NOT NULL, enderchest BLOB NOT NULL, "
                    + "toolbar_belts BLOB, "
                    + "level INTEGER NOT NULL, exp REAL NOT NULL, total_exp INTEGER NOT NULL, "
                    + "health REAL NOT NULL, absorption REAL NOT NULL, "
                    + "food INTEGER NOT NULL, saturation REAL NOT NULL, exhaustion REAL NOT NULL, "
                    + "air INTEGER NOT NULL, fire_ticks INTEGER NOT NULL, effects TEXT NOT NULL, "
                    + "gamemode TEXT NOT NULL, flying INTEGER NOT NULL, allow_flight INTEGER NOT NULL, "
                    + "world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, "
                    + "yaw REAL NOT NULL, pitch REAL NOT NULL, "
                    + "restored_at INTEGER)");
            // "Who still has to get something back" - asked on every login.
            st.execute("CREATE INDEX snapshots_pending ON snapshots (uuid, restored_at)");
            st.execute("CREATE INDEX snapshots_game ON snapshots (game_id)");
        }
    }

    /**
     * 3: the duel statistics. {@code games.counted} says whether a round counts (anti-farming rules);
     * {@code player_stats} holds the totals per player and kit ('' = all kits) so lists stay fast;
     * {@code bans} are players excluded from duels.
     */
    private static void createStats(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE games ADD COLUMN counted INTEGER NOT NULL DEFAULT 0");
            st.execute("CREATE INDEX games_ended ON games (ended_at)");
            // Decided once when the round ends (with the rules of that moment): WIN | LOSS | DRAW, kill, death.
            st.execute("ALTER TABLE game_players ADD COLUMN outcome TEXT");
            st.execute("ALTER TABLE game_players ADD COLUMN kills INTEGER NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE game_players ADD COLUMN deaths INTEGER NOT NULL DEFAULT 0");
            // Statistics reset by an admin: older rounds no longer count (uuid '*' = everyone).
            st.execute("CREATE TABLE stats_resets (uuid TEXT PRIMARY KEY, at INTEGER NOT NULL)");
            st.execute("CREATE TABLE player_stats ("
                    + "uuid TEXT NOT NULL, kit TEXT NOT NULL, name TEXT NOT NULL, "
                    + "games INTEGER NOT NULL DEFAULT 0, wins INTEGER NOT NULL DEFAULT 0, "
                    + "losses INTEGER NOT NULL DEFAULT 0, draws INTEGER NOT NULL DEFAULT 0, "
                    + "kills INTEGER NOT NULL DEFAULT 0, deaths INTEGER NOT NULL DEFAULT 0, "
                    + "damage_dealt REAL NOT NULL DEFAULT 0, damage_taken REAL NOT NULL DEFAULT 0, "
                    + "arrows_shot INTEGER NOT NULL DEFAULT 0, arrows_hit INTEGER NOT NULL DEFAULT 0, "
                    + "totems INTEGER NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, "
                    + "streak INTEGER NOT NULL DEFAULT 0, best_streak INTEGER NOT NULL DEFAULT 0, "
                    + "last_game INTEGER, PRIMARY KEY (uuid, kit))");
            st.execute("CREATE TABLE bans ("
                    + "uuid TEXT PRIMARY KEY, name TEXT NOT NULL, until INTEGER, reason TEXT, "
                    + "by TEXT NOT NULL, created_at INTEGER NOT NULL)");
        }
    }

    /**
     * 4: matches of several rounds ("first to N"). games.match_id (since 1) points to the match, games.round is the
     * round in it; matches holds the result (id = Game-ID of the first round).
     */
    private static void createMatches(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE games ADD COLUMN round INTEGER NOT NULL DEFAULT 1");
            st.execute("CREATE INDEX games_match ON games (match_id)");
            st.execute("CREATE TABLE matches ("
                    + "id INTEGER PRIMARY KEY, kit TEXT NOT NULL, first_to INTEGER NOT NULL, rounds INTEGER NOT NULL, "
                    + "one TEXT NOT NULL, two TEXT NOT NULL, wins_one INTEGER NOT NULL, wins_two INTEGER NOT NULL, "
                    + "winner TEXT, reason TEXT, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL, "
                    + "options TEXT)");
            st.execute("CREATE INDEX matches_one ON matches (one)");
            st.execute("CREATE INDEX matches_two ON matches (two)");
        }
    }

    /**
     * 5: conspicuous rounds wait for an admin instead of simply not counting. games.flags = what was noticed
     * (comma list), games.review = PENDING | APPROVED | REJECTED (null = nothing to check), games.duration = fight
     * time (to build the statistics again). invite_prefs = what a player chose last time when inviting.
     */
    private static void createReview(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE games ADD COLUMN flags TEXT");
            st.execute("ALTER TABLE games ADD COLUMN review TEXT");
            st.execute("ALTER TABLE games ADD COLUMN reviewed_by TEXT");
            st.execute("ALTER TABLE games ADD COLUMN reviewed_at INTEGER");
            st.execute("ALTER TABLE games ADD COLUMN duration INTEGER");
            st.execute("CREATE INDEX games_review ON games (review)");
            st.execute("CREATE TABLE invite_prefs ("
                    + "uuid TEXT PRIMARY KEY, kit TEXT, arena TEXT, wins INTEGER NOT NULL, minutes INTEGER NOT NULL, "
                    + "hunger INTEGER NOT NULL, regen INTEGER NOT NULL)");
        }
    }

    /**
     * 6: admins take counted rounds out of the statistics afterwards (games.review = REMOVED) or count them again -
     * games.review_note = their optional reason.
     */
    private static void createReviewNote(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE games ADD COLUMN review_note TEXT");
        }
    }

    /** 2: the two players of a round with their numbers, the combat log and positions for the mini map. */
    private static void createCombat(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE game_players ("
                    + "game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE, "
                    + "uuid TEXT NOT NULL, name TEXT NOT NULL, side INTEGER NOT NULL, "
                    + "health_end REAL, damage_dealt REAL NOT NULL, damage_taken REAL NOT NULL, "
                    + "hits INTEGER NOT NULL, crits INTEGER NOT NULL, arrows_shot INTEGER NOT NULL, "
                    + "arrows_hit INTEGER NOT NULL, totems INTEGER NOT NULL, pearls INTEGER NOT NULL, "
                    + "PRIMARY KEY (game_id, side))");
            st.execute("CREATE INDEX game_players_uuid ON game_players (uuid)");
            // t = ms since the round started
            st.execute("CREATE TABLE events ("
                    + "game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE, "
                    + "t INTEGER NOT NULL, type TEXT NOT NULL, actor TEXT, target TEXT, damage REAL, "
                    + "cause TEXT, weapon TEXT, x REAL, y REAL, z REAL)");
            st.execute("CREATE INDEX events_game ON events (game_id, t)");
            st.execute("CREATE TABLE positions ("
                    + "game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE, "
                    + "t INTEGER NOT NULL, side INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, "
                    + "yaw REAL NOT NULL, health REAL NOT NULL)");
            st.execute("CREATE INDEX positions_game ON positions (game_id, t)");
        }
    }
}
