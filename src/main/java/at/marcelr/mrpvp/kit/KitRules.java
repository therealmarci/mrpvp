package at.marcelr.mrpvp.kit;

import java.util.List;
import java.util.Map;

/**
 * What a kit changes about a round (kits.yml "rules"). Null for an option = the players choose it.
 *
 * @param damage     false = hits do no damage, only knockback (sumo, boxing)
 * @param hitsToWin  &gt; 0 = whoever lands this many hits first wins (boxing)
 * @param regen      forced natural regeneration (UHC: false), null = chosen by the players
 * @param hunger     forced hunger, null = chosen by the players
 * @param arenas     only these arena templates, empty = any that allows the kit
 * @param build      the kit needs an arena where building is allowed (blocks, crystals, lava)
 * @param effects    effect key ("speed") -&gt; level (1 = I) for the whole round, in kits.yml order
 * @param maxHearts  &gt; 0 = both players have this many hearts (10 = normal)
 * @param cooldowns  item key ("ender_pearl") -&gt; seconds to wait after using one
 * @param minutes    &gt; 0 = fixed time limit of a round, 0 = chosen by the players
 */
public record KitRules(boolean damage, int hitsToWin, Boolean regen, Boolean hunger, List<String> arenas,
                       boolean build, Map<String, Integer> effects, int maxHearts, Map<String, Integer> cooldowns,
                       int minutes) {

    public static final KitRules NONE = new KitRules(true, 0, null, null, List.of(), false, Map.of(), 0, Map.of(), 0);

    /** Limits for every place that sets rules (kits.yml, kit editor in the game, web). */
    public static final int MAX_EFFECT_LEVEL = 10;
    public static final int MAX_HEARTS = 100;
    public static final int MAX_COOLDOWN = 3600;
    public static final int MAX_MINUTES = 120;

    public KitRules {
        arenas = List.copyOf(arenas);
        effects = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(effects));
        cooldowns = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(cooldowns));
    }

    public boolean isDefault() {
        return equals(NONE);
    }

    public KitRules withDamage(boolean value) {
        return new KitRules(value, hitsToWin, regen, hunger, arenas, build, effects, maxHearts, cooldowns, minutes);
    }

    public KitRules withHitsToWin(int value) {
        return new KitRules(damage, value, regen, hunger, arenas, build, effects, maxHearts, cooldowns, minutes);
    }

    public KitRules withRegen(Boolean value) {
        return new KitRules(damage, hitsToWin, value, hunger, arenas, build, effects, maxHearts, cooldowns, minutes);
    }

    public KitRules withHunger(Boolean value) {
        return new KitRules(damage, hitsToWin, regen, value, arenas, build, effects, maxHearts, cooldowns, minutes);
    }

    public KitRules withArenas(List<String> value) {
        return new KitRules(damage, hitsToWin, regen, hunger, value, build, effects, maxHearts, cooldowns, minutes);
    }

    public KitRules withBuild(boolean value) {
        return new KitRules(damage, hitsToWin, regen, hunger, arenas, value, effects, maxHearts, cooldowns, minutes);
    }

    public KitRules withEffects(Map<String, Integer> value) {
        return new KitRules(damage, hitsToWin, regen, hunger, arenas, build, value, maxHearts, cooldowns, minutes);
    }

    public KitRules withMaxHearts(int value) {
        return new KitRules(damage, hitsToWin, regen, hunger, arenas, build, effects, value, cooldowns, minutes);
    }

    public KitRules withCooldowns(Map<String, Integer> value) {
        return new KitRules(damage, hitsToWin, regen, hunger, arenas, build, effects, maxHearts, value, minutes);
    }

    public KitRules withMinutes(int value) {
        return new KitRules(damage, hitsToWin, regen, hunger, arenas, build, effects, maxHearts, cooldowns, value);
    }
}
