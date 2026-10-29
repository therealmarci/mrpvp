package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.arena.ArenaInstance;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.stats.GameQueries;

import java.util.List;
import java.util.UUID;

/** A running round. Main thread only. */
public final class Game {

    public enum State {
        /** players are in the arena, frozen, counting down */
        COUNTDOWN,
        /** fighting */
        RUNNING,
        /** a player lost the connection - waiting for them to come back (frozen, nobody takes damage) */
        PAUSED,
        /** decided - short pause until everyone gets their things back */
        ENDING
    }

    public enum Result { WIN, DRAW, ABORTED }

    /** Why a round ended. Stored as text - never rename. */
    public enum EndReason {
        /** the loser took deadly damage */
        KILL,
        /** the loser left the server */
        QUIT,
        /** max-minutes reached */
        TIMEOUT,
        /** the loser gave up (/duel leave) */
        SURRENDER,
        /** an admin stopped it */
        ADMIN,
        /** an admin declared the winner (the match is over, the round counts as a win without kill / death) */
        ADMIN_WIN,
        /** the loser fell out of the arena */
        BORDER,
        /** the server stopped during the round */
        SHUTDOWN,
        /** the server crashed during the round (found on the next start) */
        CRASH,
        /** the round could not start (e.g. a player's state could not be saved) */
        ERROR
    }

    private final long id;
    private final Match match;
    private final Kit kit;
    private final ArenaInstance arena;
    private final Fighter one;
    private final Fighter two;
    private final CombatLog log = new CombatLog();
    private State state = State.COUNTDOWN;
    private int countdown;
    private long countdownStep;
    /** System time when the fight started (end of the countdown), 0 before. */
    private long fightStart;
    /** while paused (and in the countdown after a pause): since when, 0 = not paused; paused time in total */
    private long pausedAt;
    private long pausedTotal;
    /** An admin paused the round: their name, otherwise null. */
    private String adminPause;
    /** who the round waits for, and until when (epoch ms) */
    private UUID waitingFor;
    private long waitUntil;
    /** the last round of its match (decided when it ends) */
    private boolean last;

    Game(long id, Match match, Kit kit, ArenaInstance arena, Fighter one, Fighter two, int countdown) {
        this.match = match;
        this.id = id;
        this.kit = kit;
        this.arena = arena;
        this.one = one;
        this.two = two;
        this.countdown = countdown;
    }

    public long id() {
        return id;
    }

    /** The match this round belongs to (a single round is a match of one). */
    public Match match() {
        return match;
    }

    public Kit kit() {
        return kit;
    }

    public ArenaInstance arena() {
        return arena;
    }

    public Fighter one() {
        return one;
    }

    public Fighter two() {
        return two;
    }

    public State state() {
        return state;
    }

    void state(State state) {
        this.state = state;
    }

    CombatLog log() {
        return log;
    }

    /**
     * Combat log entries not written to the database yet (that happens every few seconds) - the live view adds
     * them so hits show at once. Main thread only.
     */
    public List<GameQueries.EventRow> pendingEvents() {
        return log.pending().stream()
                .map(e -> new GameQueries.EventRow(e.t(), e.type().name(), e.actor(), e.target(), e.damage(), e.cause(),
                        e.weapon(), e.x(), e.y(), e.z()))
                .toList();
    }

    /**
     * Counts the countdown down by one once a full second has passed since the last step (the server's second timer
     * does not start with the round); returns the value, unchanged if it is too early.
     */
    int tickCountdown() {
        long now = System.currentTimeMillis();
        if (now - countdownStep >= 900) {
            countdownStep = now;
            countdown--;
        }
        return countdown;
    }

    /** The countdown was just shown (start of the round). */
    void countdownShown() {
        countdownStep = System.currentTimeMillis();
    }

    int countdown() {
        return countdown;
    }

    void startFight() {
        state = State.RUNNING;
        long now = System.currentTimeMillis();
        if (fightStart == 0) {
            fightStart = now;
        } else if (pausedAt > 0) {
            // Back after a pause: the time limit goes on where it stopped.
            pausedTotal += now - pausedAt;
            pausedAt = 0;
        }
    }

    /** Fight time in milliseconds - without pauses (0 before the fight). */
    public long elapsed() {
        if (fightStart == 0) {
            return 0;
        }
        long end = pausedAt > 0 ? pausedAt : System.currentTimeMillis();
        return end - fightStart - pausedTotal;
    }

    /** The fight of this round had begun (a pause before that is not possible - the round is simply aborted). */
    boolean fought() {
        return fightStart > 0;
    }

    /** Waits for a player who lost the connection (also ends an admin pause - now it waits for the player). */
    void pause(UUID player, long until) {
        adminPause = null;
        state = State.PAUSED;
        if (pausedAt == 0) {
            pausedAt = System.currentTimeMillis();
        }
        waitingFor = player;
        waitUntil = until;
    }

    /** An admin stops the time: both frozen, nobody takes damage, until an admin resumes it. */
    void adminPause(String by) {
        state = State.PAUSED;
        if (pausedAt == 0) {
            pausedAt = System.currentTimeMillis();
        }
        adminPause = by;
        waitingFor = null;
        waitUntil = Long.MAX_VALUE;
    }

    /** Who paused the round, or null if no admin did. */
    public String adminPause() {
        return adminPause;
    }

    /** They are back (or an admin resumes): a short countdown, then the fight goes on. */
    void resume(int seconds) {
        adminPause = null;
        waitingFor = null;
        waitUntil = 0;
        state = State.COUNTDOWN;
        countdown = seconds;
        countdownShown();
    }

    /** Waits (also between two rounds) for this player, null = nobody. */
    public UUID waitingFor() {
        return waitingFor;
    }

    long waitUntil() {
        return waitUntil;
    }

    /** Between two rounds a player lost the connection: the next round waits for them until then. */
    void waitBetweenRounds(UUID player, long until) {
        waitingFor = player;
        waitUntil = until;
    }

    void stopWaiting() {
        waitingFor = null;
        waitUntil = 0;
    }

    boolean last() {
        return last;
    }

    void last(boolean value) {
        last = value;
    }

    public Fighter fighter(UUID player) {
        if (one.uuid().equals(player)) {
            return one;
        }
        return two.uuid().equals(player) ? two : null;
    }

    public Fighter opponent(UUID player) {
        if (one.uuid().equals(player)) {
            return two;
        }
        return two.uuid().equals(player) ? one : null;
    }
}
