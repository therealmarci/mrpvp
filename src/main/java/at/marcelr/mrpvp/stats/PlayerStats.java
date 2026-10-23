package at.marcelr.mrpvp.stats;

import java.util.UUID;

/** A player's totals (all kits or one kit). */
public record PlayerStats(UUID uuid, String name, String kit, int games, int wins, int losses, int draws, int kills,
                          int deaths, double damageDealt, double damageTaken, int arrowsShot, int arrowsHit,
                          int totems, long durationMillis, int streak, int bestStreak) {

    public static PlayerStats empty(UUID uuid, String name, String kit) {
        return new PlayerStats(uuid, name, kit, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Kills per death (kills alone without deaths). */
    public double kd() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    /** Share of won rounds in percent. */
    public double winRate() {
        return games == 0 ? 0 : 100.0 * wins / games;
    }

    /** Arrows that hit in percent. */
    public double accuracy() {
        return arrowsShot == 0 ? 0 : 100.0 * arrowsHit / arrowsShot;
    }

    public double averageDamage() {
        return games == 0 ? 0 : damageDealt / games;
    }

    public long averageDurationMillis() {
        return games == 0 ? 0 : durationMillis / games;
    }
}
