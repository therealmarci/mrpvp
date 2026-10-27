package at.marcelr.mrpvp.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statistic farming: the same two players many times in a short time, and one-sided - one (nearly) always wins and
 * the other hardly fights back. Even fights between friends, however many, are never suspicious. Pure logic without
 * Bukkit (tested in FarmingCheckTest), used by {@link Monitor}.
 */
public final class FarmingCheck {

    /**
     * @param rounds              at least this many rounds of the same two players ...
     * @param windowMillis        ... within this time ...
     * @param winnerShare         ... of which the same player won at least this share (0-1) ...
     * @param maxLoserDamageShare ... while the other dealt at most this share (0-1) of all damage in them
     */
    public record Rules(int rounds, long windowMillis, double winnerShare, double maxLoserDamageShare) {
    }

    private record Round(long time, String pair, UUID winner, UUID one, double damageOne, double damageTwo) {
    }

    private final Deque<Round> rounds = new ArrayDeque<>();

    /**
     * Adds a round that just ended.
     *
     * @param winner null for a draw
     * @return the number of rounds of the two in the window if they look like farming, otherwise 0
     */
    public int add(long now, UUID one, double damageOne, UUID two, double damageTwo, UUID winner, Rules rules) {
        String pair = key(one, two);
        rounds.add(new Round(now, pair, winner, one, damageOne, damageTwo));
        rounds.removeIf(r -> now - r.time() > rules.windowMillis());
        List<Round> together = rounds.stream().filter(r -> r.pair().equals(pair)).toList();
        if (together.size() < rules.rounds()) {
            return 0;
        }
        Map<UUID, Integer> wins = new HashMap<>();
        for (Round r : together) {
            if (r.winner() != null) {
                wins.merge(r.winner(), 1, Integer::sum);
            }
        }
        UUID dominant = null;
        int most = 0;
        for (Map.Entry<UUID, Integer> e : wins.entrySet()) {
            if (e.getValue() > most) {
                dominant = e.getKey();
                most = e.getValue();
            }
        }
        if (dominant == null || most < rules.winnerShare() * together.size()) {
            return 0;
        }
        double total = 0;
        double loser = 0;
        for (Round r : together) {
            total += r.damageOne() + r.damageTwo();
            loser += r.one().equals(dominant) ? r.damageTwo() : r.damageOne();
        }
        // No damage at all in all those rounds is one-sided too (the loser just stands there).
        if (total > 0 && loser > rules.maxLoserDamageShare() * total) {
            return 0;
        }
        return together.size();
    }

    static String key(UUID a, UUID b) {
        String x = a.toString();
        String y = b.toString();
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }
}
