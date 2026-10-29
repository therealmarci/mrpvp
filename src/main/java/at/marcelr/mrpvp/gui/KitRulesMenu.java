package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.kit.KitRules;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * The rules of a kit in the kit editor: damage, hits to win, regeneration, hunger, building, time limit, max hearts,
 * and on their own pages the arenas, the effects and the item cooldowns. Works on the editor's draft - nothing is
 * stored until the kit is saved there.
 */
final class KitRulesMenu extends Menu {

    private enum Page { MAIN, ARENAS, EFFECTS, COOLDOWNS }

    /** Choices for the cooldown of an item (seconds; left click goes up, right click down). */
    private static final int[] COOLDOWN_STEPS = {0, 1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60, 90, 120};

    private final KitEditorMenu.Draft draft;
    private final Runnable back;
    private Page page = Page.MAIN;

    KitRulesMenu(MRPvp plugin, Player viewer, KitEditorMenu.Draft draft, Runnable back) {
        super(plugin, viewer, 6, plugin.messages().get("gui-kitrules-title", tag("id", draft.id)));
        this.draft = draft;
        this.back = back;
    }

    @Override
    protected void draw() {
        switch (page) {
            case MAIN -> main();
            case ARENAS -> arenas();
            case EFFECTS -> effects();
            case COOLDOWNS -> cooldowns();
        }
        fillRow(5);
    }

    private void change(KitRules rules) {
        draft.rules = rules;
        draft.changed = true;
        clickSound();
        render();
    }

    private void show(Page next) {
        page = next;
        clickSound();
        render();
    }

    // ------------------------------------------------------------------ main page

    private void main() {
        var m = plugin.messages();
        KitRules r = draft.rules;
        set(10, Items.of(r.damage() ? Material.IRON_SWORD : Material.FEATHER).flags()
                .name(m.item(r.damage() ? "gui-kitrules-damage-on" : "gui-kitrules-damage-off"))
                .lore(m.lore("gui-kitrules-toggle-lore")).build(), click -> change(r.withDamage(!r.damage())));
        set(11, Items.of(Material.TARGET).amount(Math.clamp(r.hitsToWin(), 1, 64))
                .name(m.item("gui-kitrules-hits", tag("value", r.hitsToWin() == 0 ? m.item("gui-kitrules-off-value")
                        : Component.text(r.hitsToWin()))))
                .lore(m.lore("gui-kitrules-number-lore")).build(),
                click -> change(r.withHitsToWin(step(r.hitsToWin(), click, 0, 1000))));
        set(12, Items.of(Material.GLISTERING_MELON_SLICE).name(m.item("gui-kitrules-regen", tag("value", choice(r.regen()))))
                .lore(m.lore("gui-kitrules-cycle-lore")).build(), click -> change(r.withRegen(cycle(r.regen()))));
        set(13, Items.of(Material.COOKED_BEEF).name(m.item("gui-kitrules-hunger", tag("value", choice(r.hunger()))))
                .lore(m.lore("gui-kitrules-cycle-lore")).build(), click -> change(r.withHunger(cycle(r.hunger()))));
        set(14, Items.of(r.build() ? Material.BRICKS : Material.GLASS)
                .name(m.item(r.build() ? "gui-kitrules-build-on" : "gui-kitrules-build-off"))
                .lore(m.lore("gui-kitrules-toggle-lore")).build(), click -> change(r.withBuild(!r.build())));
        set(15, Items.of(Material.CLOCK).amount(Math.clamp(r.minutes(), 1, 64))
                .name(m.item("gui-kitrules-minutes", tag("value", r.minutes() == 0 ? m.item("gui-kitrules-choose")
                        : m.item("gui-kitrules-minutes-value", tag("n", r.minutes())))))
                .lore(m.lore("gui-kitrules-minutes-lore")).build(),
                click -> change(r.withMinutes(step(r.minutes(), click, 0, KitRules.MAX_MINUTES))));
        set(16, Items.of(Material.RED_DYE).amount(Math.clamp(r.maxHearts(), 1, 64))
                .name(m.item("gui-kitrules-hearts", tag("value", r.maxHearts() == 0 ? m.item("gui-kitrules-hearts-normal")
                        : m.item("gui-kitrules-hearts-value", tag("n", r.maxHearts())))))
                .lore(m.lore("gui-kitrules-hearts-lore")).build(),
                click -> change(r.withMaxHearts(step(r.maxHearts(), click, 0, KitRules.MAX_HEARTS))));

        List<Component> arenaLines = new ArrayList<>();
        if (r.arenas().isEmpty()) {
            arenaLines.add(m.item("gui-kitrules-arenas-all"));
        } else {
            r.arenas().forEach(a -> arenaLines.add(Component.text("• " + plugin.games().arenaName(a))
                    .decoration(TextDecoration.ITALIC, false)));
        }
        arenaLines.addAll(m.lore("gui-kitrules-open-lore"));
        set(29, Items.of(Material.MAP).name(m.item("gui-kitrules-arenas")).lore(arenaLines).build(), click -> show(Page.ARENAS));

        List<Component> effectLines = new ArrayList<>();
        r.effects().forEach((key, level) -> effectLines.add(effectName(key).append(Component.text(" " + roman(level)))
                .decoration(TextDecoration.ITALIC, false)));
        if (effectLines.isEmpty()) {
            effectLines.add(m.item("gui-kitrules-none"));
        }
        effectLines.addAll(m.lore("gui-kitrules-open-lore"));
        set(31, Items.of(Material.POTION).flags().name(m.item("gui-kitrules-effects")).lore(effectLines).build(),
                click -> show(Page.EFFECTS));

        List<Component> cooldownLines = new ArrayList<>();
        r.cooldowns().forEach((item, seconds) -> {
            Material type = Material.matchMaterial(item);
            cooldownLines.add((type == null ? Component.text(item) : Component.translatable(type.translationKey()))
                    .append(Component.text(": " + seconds + " s")).decoration(TextDecoration.ITALIC, false));
        });
        if (cooldownLines.isEmpty()) {
            cooldownLines.add(m.item("gui-kitrules-none"));
        }
        cooldownLines.addAll(m.lore("gui-kitrules-open-lore"));
        set(33, Items.of(Material.ENDER_PEARL).name(m.item("gui-kitrules-cooldowns")).lore(cooldownLines).build(),
                click -> show(Page.COOLDOWNS));

        set(45, Items.of(Material.ARROW).name(m.item("gui-kitrules-back")).build(), click -> {
            clickSound();
            back.run();
        });
    }

    // ------------------------------------------------------------------ arenas

    private void arenas() {
        var m = plugin.messages();
        List<ArenaTemplate> templates = List.copyOf(plugin.templates().all());
        for (int i = 0; i < templates.size() && i < 45; i++) {
            ArenaTemplate t = templates.get(i);
            boolean on = draft.rules.arenas().contains(t.id());
            set(i, Items.of(DuelSetupMenu.icon(t)).name(m.miniItem(t.name())).glint(on).flags()
                    .lore(m.lore(on ? "gui-kit-toggle-on" : draft.rules.arenas().isEmpty() ? "gui-kitrules-arena-all"
                            : "gui-kit-toggle-off")).build(), click -> {
                List<String> list = new ArrayList<>(draft.rules.arenas());
                if (on) {
                    list.remove(t.id());
                } else {
                    list.add(t.id());
                }
                change(draft.rules.withArenas(list));
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> show(Page.MAIN));
        set(49, Items.of(Material.BUCKET).name(m.item("gui-kitrules-arenas-clear")).build(),
                click -> change(draft.rules.withArenas(List.of())));
    }

    // ------------------------------------------------------------------ effects

    private void effects() {
        var m = plugin.messages();
        List<PotionEffectType> types = new ArrayList<>();
        RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).forEach(types::add);
        types.sort(Comparator.comparing(t -> t.getKey().getKey()));
        for (int i = 0; i < types.size() && i < 45; i++) {
            PotionEffectType type = types.get(i);
            String key = type.getKey().getKey();
            int level = draft.rules.effects().getOrDefault(key, 0);
            set(i, Items.of(level > 0 ? Material.POTION : Material.GLASS_BOTTLE).flags().glint(level > 0)
                    .name(effectName(key).decoration(TextDecoration.ITALIC, false))
                    .lore(withLore(level > 0 ? m.item("gui-kitrules-level", tag("level", roman(level)))
                            : m.item("gui-kitrules-effect-off"), m.lore("gui-kitrules-level-lore"))).build(), click -> {
                Map<String, Integer> effects = new LinkedHashMap<>(draft.rules.effects());
                int next = Math.clamp(level + (click.isRightClick() ? -1 : 1), 0, KitRules.MAX_EFFECT_LEVEL);
                if (next == 0) {
                    effects.remove(key);
                } else {
                    effects.put(key, next);
                }
                change(draft.rules.withEffects(effects));
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> show(Page.MAIN));
        set(49, Items.of(Material.BUCKET).name(m.item("gui-kitrules-clear")).build(),
                click -> change(draft.rules.withEffects(Map.of())));
    }

    // ------------------------------------------------------------------ cooldowns

    private void cooldowns() {
        var m = plugin.messages();
        // The kit's items (in kit order), plus items that still have a cooldown but are no longer in the kit.
        Set<Material> types = new LinkedHashSet<>();
        draft.items.values().forEach(item -> types.add(item.getType()));
        draft.rules.cooldowns().keySet().forEach(key -> {
            Material type = Material.matchMaterial(key);
            if (type != null) {
                types.add(type);
            }
        });
        List<Material> list = List.copyOf(types);
        for (int i = 0; i < list.size() && i < 45; i++) {
            Material type = list.get(i);
            String key = type.getKey().getKey();
            int seconds = draft.rules.cooldowns().getOrDefault(key, 0);
            set(i, Items.of(type).flags().glint(seconds > 0)
                    .lore(withLore(seconds > 0 ? m.item("gui-kitrules-cooldown-value", tag("seconds", seconds))
                            : m.item("gui-kitrules-cooldown-off"), m.lore("gui-kitrules-cooldown-lore"))).build(), click -> {
                Map<String, Integer> cooldowns = new LinkedHashMap<>(draft.rules.cooldowns());
                int next = nextStep(seconds, click.isRightClick());
                if (next == 0) {
                    cooldowns.remove(key);
                } else {
                    cooldowns.put(key, next);
                }
                change(draft.rules.withCooldowns(cooldowns));
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> show(Page.MAIN));
        set(48, Items.of(Material.KNOWLEDGE_BOOK).name(m.item("gui-kitrules-cooldowns")).lore(m.lore("gui-kitrules-cooldowns-hint")).build());
        set(49, Items.of(Material.BUCKET).name(m.item("gui-kitrules-clear")).build(),
                click -> change(draft.rules.withCooldowns(Map.of())));
    }

    // ------------------------------------------------------------------ helpers

    /** Left +1, right -1, with shift ±10, within min..max. */
    private static int step(int value, ClickType click, int min, int max) {
        int by = click.isShiftClick() ? 10 : 1;
        return Math.clamp(value + (click.isRightClick() ? -by : by), min, max);
    }

    private static int nextStep(int seconds, boolean down) {
        int index = 0;
        for (int i = 0; i < COOLDOWN_STEPS.length; i++) {
            if (COOLDOWN_STEPS[i] <= seconds) {
                index = i;
            }
        }
        if (COOLDOWN_STEPS[index] != seconds && !down) {
            // a value from kits.yml between two steps: up goes to the next step
            return COOLDOWN_STEPS[Math.min(index + 1, COOLDOWN_STEPS.length - 1)];
        }
        return COOLDOWN_STEPS[Math.clamp(index + (down ? -1 : 1), 0, COOLDOWN_STEPS.length - 1)];
    }

    /** Players choose -> always on -> always off -> players choose. */
    private static Boolean cycle(Boolean value) {
        return value == null ? Boolean.TRUE : value ? Boolean.FALSE : null;
    }

    private Component choice(Boolean value) {
        var m = plugin.messages();
        return m.item(value == null ? "gui-kitrules-choose" : value ? "gui-kitrules-on" : "gui-kitrules-off");
    }

    private static Component effectName(String key) {
        PotionEffectType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(Key.key(key));
        return type == null ? Component.text(key) : Component.translatable(type.translationKey());
    }

    private static String roman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 0 && level < numerals.length ? numerals[level] : String.valueOf(level);
    }

    private static List<Component> withLore(Component first, List<Component> rest) {
        List<Component> lines = new ArrayList<>();
        lines.add(first);
        lines.addAll(rest);
        return lines;
    }

}
