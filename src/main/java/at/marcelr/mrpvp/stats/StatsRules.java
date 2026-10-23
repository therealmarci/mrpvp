package at.marcelr.mrpvp.stats;

/**
 * When a round counts and what counts as kill or death (config stats.*).
 *
 * @param minDurationMillis       shorter rounds do not count
 * @param maxPerOpponentPerDay    counted rounds against the same opponent within 24 h (0 = no limit)
 * @param quitCountsAsDeath       leaving the server during a fight counts as a death
 * @param surrenderCountsAsDeath  giving up counts as a death
 * @param borderCountsAsKill      falling out of the arena counts as a kill for the winner
 * @param minGamesForRatio        K/D and win rate only appear in the leaderboard after this many rounds
 */
public record StatsRules(long minDurationMillis, int maxPerOpponentPerDay, boolean quitCountsAsDeath,
                         boolean surrenderCountsAsDeath, boolean borderCountsAsKill, int minGamesForRatio) {

    /** Kill and death of one side of a decided round. */
    record Score(String outcome, int kills, int deaths) {
    }

    /**
     * @param reason  Game.EndReason name
     * @param won     this player won (only meaningful for result WIN)
     */
    Score score(String result, String reason, boolean won) {
        if ("DRAW".equals(result)) {
            return new Score("DRAW", 0, 0);
        }
        boolean kill = "KILL".equals(reason) || ("BORDER".equals(reason) && borderCountsAsKill);
        boolean death = "KILL".equals(reason) || "BORDER".equals(reason)
                || ("QUIT".equals(reason) && quitCountsAsDeath)
                || ("SURRENDER".equals(reason) && surrenderCountsAsDeath);
        return won ? new Score("WIN", kill ? 1 : 0, 0) : new Score("LOSS", 0, death ? 1 : 0);
    }
}
