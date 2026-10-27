package at.marcelr.mrpvp.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmingCheckTest {

    private static final long MIN = 60_000;
    private static final FarmingCheck.Rules RULES = new FarmingCheck.Rules(8, 60 * MIN, 0.9, 0.2);

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final FarmingCheck check = new FarmingCheck();

    /** a beats b, b deals {@code bDamage} and a 20. */
    private int aWins(long time, double bDamage) {
        return check.add(time, a, 20, b, bDamage, a, RULES);
    }

    @Test
    void oneSidedSeriesIsFarming() {
        for (int i = 0; i < 7; i++) {
            assertEquals(0, aWins(i * MIN, 1));
        }
        assertEquals(8, aWins(7 * MIN, 1));
        assertEquals(9, aWins(8 * MIN, 1));
    }

    @Test
    void twoTimesFiveEvenRoundsAreFine() {
        // The case from the issue: friends play 2 × 5 rounds, both win sometimes and both fight.
        for (int i = 0; i < 10; i++) {
            UUID winner = i % 3 == 0 ? b : a;
            assertEquals(0, check.add(i * MIN, a, 20, b, 15, winner, RULES));
        }
    }

    @Test
    void sameWinnerButRealFightIsFine() {
        // One is simply better: always wins, but the other deals plenty of damage.
        for (int i = 0; i < 12; i++) {
            assertEquals(0, aWins(i * MIN, 15));
        }
    }

    @Test
    void sharedWinsAreFine() {
        for (int i = 0; i < 12; i++) {
            UUID winner = i % 2 == 0 ? a : b;
            assertEquals(0, check.add(i * MIN, a, 20, b, 0, winner, RULES));
        }
    }

    @Test
    void noDamageAtAllCountsAsOneSided() {
        for (int i = 0; i < 7; i++) {
            check.add(i * MIN, a, 0, b, 0, a, RULES);
        }
        assertEquals(8, check.add(7 * MIN, a, 0, b, 0, a, RULES));
    }

    @Test
    void oldRoundsLeaveTheWindow() {
        for (int i = 0; i < 7; i++) {
            aWins(i * MIN, 1);
        }
        // Two hours later: the series starts again.
        assertEquals(0, aWins(180 * MIN, 1));
    }

    @Test
    void otherPairsDoNotMix() {
        for (int i = 0; i < 7; i++) {
            aWins(i * MIN, 1);
            check.add(i * MIN, a, 20, c, 1, a, RULES);
        }
        assertEquals(8, aWins(7 * MIN, 1));
    }

    @Test
    void sidesDoNotMatter() {
        for (int i = 0; i < 7; i++) {
            UUID first = i % 2 == 0 ? a : b;
            UUID second = first == a ? b : a;
            check.add(i * MIN, first, first == a ? 20 : 1, second, second == a ? 20 : 1, a, RULES);
        }
        assertEquals(8, aWins(7 * MIN, 1));
    }

    @Test
    void drawsCountAgainstTheWinnerShare() {
        for (int i = 0; i < 6; i++) {
            aWins(i * MIN, 1);
        }
        check.add(6 * MIN, a, 0, b, 0, null, RULES);
        // 7 of 8 won by a = 87.5 % < 90 %.
        assertEquals(0, aWins(7 * MIN, 1));
    }
}
