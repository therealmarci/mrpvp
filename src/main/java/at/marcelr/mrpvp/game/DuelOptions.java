package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.kit.Kit;

/**
 * What the inviting player chose for the duel.
 *
 * @param wins    rounds needed to win the match (first to 1, 2, 3, 5 ...)
 * @param minutes time limit of each round
 * @param hunger  food goes down as usual (off = always full)
 * @param regen   natural regeneration (off = only golden apples / potions heal)
 */
public record DuelOptions(int wins, int minutes, boolean hunger, boolean regen) {

    /** The options as they apply with this kit - a kit can fix hunger or regeneration (UHC: no regeneration). */
    public DuelOptions with(Kit kit) {
        var rules = kit.rules();
        return new DuelOptions(wins, minutes, rules.hunger() != null ? rules.hunger() : hunger,
                rules.regen() != null ? rules.regen() : regen);
    }

    public DuelOptions withWins(int value) {
        return new DuelOptions(value, minutes, hunger, regen);
    }

    public DuelOptions withMinutes(int value) {
        return new DuelOptions(wins, value, hunger, regen);
    }

    public DuelOptions withHunger(boolean value) {
        return new DuelOptions(wins, minutes, value, regen);
    }

    public DuelOptions withRegen(boolean value) {
        return new DuelOptions(wins, minutes, hunger, value);
    }
}
