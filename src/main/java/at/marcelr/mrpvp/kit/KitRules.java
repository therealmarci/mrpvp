package at.marcelr.mrpvp.kit;

import java.util.List;

/**
 * What a kit changes about a round (kits.yml "rules"). Null for an option = the players choose it.
 *
 * @param damage     false = hits do no damage, only knockback (sumo, boxing)
 * @param hitsToWin  &gt; 0 = whoever lands this many hits first wins (boxing)
 * @param regen      forced natural regeneration (UHC: false), null = chosen by the players
 * @param hunger     forced hunger, null = chosen by the players
 * @param arenas     only these arena templates, empty = any that allows the kit
 * @param build      the kit needs an arena where building is allowed (blocks, crystals, lava)
 */
public record KitRules(boolean damage, int hitsToWin, Boolean regen, Boolean hunger, List<String> arenas,
                       boolean build) {

    public static final KitRules NONE = new KitRules(true, 0, null, null, List.of(), false);

    public boolean isDefault() {
        return equals(NONE);
    }
}
