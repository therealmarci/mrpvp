package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.kit.Kit;

import java.util.UUID;

/**
 * A duel of one or more rounds ("first to N"). Each round is its own game with its own Game-ID; the match has the
 * id of its first round. Between rounds both stay in the arena world - their own things come back only at the very
 * end. Main thread only.
 */
public final class Match {

    private final long id;
    private final Kit kit;
    /** wanted arena template, null = random each round */
    private final String template;
    private final DuelOptions options;
    private final UUID one;
    private final UUID two;
    private int winsOne;
    private int winsTwo;
    private int round = 1;

    Match(long id, Kit kit, String template, DuelOptions options, UUID one, UUID two) {
        this.id = id;
        this.kit = kit;
        this.template = template;
        this.options = options;
        this.one = one;
        this.two = two;
    }

    public long id() {
        return id;
    }

    public Kit kit() {
        return kit;
    }

    public String template() {
        return template;
    }

    /** The options as they apply with the kit. */
    public DuelOptions options() {
        return options;
    }

    public int round() {
        return round;
    }

    void nextRound() {
        round++;
    }

    public int wins(int side) {
        return side == 1 ? winsOne : winsTwo;
    }

    void won(UUID player) {
        if (one.equals(player)) {
            winsOne++;
        } else if (two.equals(player)) {
            winsTwo++;
        }
    }

    /** Someone has enough wins. */
    boolean decided() {
        return winsOne >= options.wins() || winsTwo >= options.wins();
    }

    /** After so many rounds (draws) it ends anyway: twice the needed wins plus one. */
    boolean roundsUsedUp() {
        return round >= options.wins() * 2 + 1;
    }

    /** The match winner (side 1 / 2), or 0 for none. */
    int leader() {
        return winsOne > winsTwo ? 1 : winsTwo > winsOne ? 2 : 0;
    }

    public boolean single() {
        return options.wins() <= 1;
    }

    // ------------------------------------------------------------------ totals for the summary at the end

    private final double[] dealt = new double[3];
    private final double[] taken = new double[3];
    private int held;

    void addRound(Fighter one, Fighter two) {
        dealt[1] += one.damageDealt();
        taken[1] += one.damageTaken();
        dealt[2] += two.damageDealt();
        taken[2] += two.damageTaken();
    }

    double dealt(int side) {
        return dealt[side];
    }

    double taken(int side) {
        return taken[side];
    }

    // ------------------------------------------------------------------ leaving

    /** who already came back after losing the connection (once per match - otherwise it could be abused) */
    private final java.util.Set<UUID> reconnected = new java.util.HashSet<>();
    /** who gave up between two rounds - they lose the match when the next round would start */
    private UUID forfeit;

    boolean mayReconnect(UUID player) {
        return !reconnected.contains(player);
    }

    void usedReconnect(UUID player) {
        reconnected.add(player);
    }

    UUID forfeit() {
        return forfeit;
    }

    void forfeit(UUID player) {
        forfeit = player;
    }

    /** A round of the match waits for an admin. */
    void held() {
        held++;
    }

    int heldRounds() {
        return held;
    }
}
