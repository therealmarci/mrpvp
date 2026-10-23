package at.marcelr.mrpvp.game;

import java.util.UUID;

/** One of the two players of a round, with the numbers counted during the fight (main thread only). */
public final class Fighter {

    private final UUID uuid;
    private final String name;
    /** 1 = the inviting player, 2 = the invited one */
    private final int side;

    double damageDealt;
    double damageTaken;
    int hits;
    int crits;
    int arrowsShot;
    int arrowsHit;
    int totems;
    int pearls;
    Double healthEnd;
    /** strongest single hit dealt (for the monitor) and its weapon */
    double maxHit;
    String maxHitWeapon;
    /** tries to leave the arena */
    int borders;
    /** when this player last hit the other one / was last hurt (epoch ms, 0 = never) - for the web live view */
    long lastHitAt;
    long lastHurtAt;

    Fighter(UUID uuid, String name, int side) {
        this.uuid = uuid;
        this.name = name;
        this.side = side;
    }

    public long lastHitAt() {
        return lastHitAt;
    }

    public long lastHurtAt() {
        return lastHurtAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public int side() {
        return side;
    }

    public double damageDealt() {
        return damageDealt;
    }

    public double damageTaken() {
        return damageTaken;
    }

    public int hits() {
        return hits;
    }

    public int crits() {
        return crits;
    }

    public int arrowsShot() {
        return arrowsShot;
    }

    public int arrowsHit() {
        return arrowsHit;
    }

    public int totems() {
        return totems;
    }

    public int pearls() {
        return pearls;
    }

    public double maxHit() {
        return maxHit;
    }

    public String maxHitWeapon() {
        return maxHitWeapon;
    }

    public int borders() {
        return borders;
    }

    public Double healthEnd() {
        return healthEnd;
    }
}
