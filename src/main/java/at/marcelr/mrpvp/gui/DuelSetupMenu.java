package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.game.DuelOptions;
import at.marcelr.mrpvp.game.GameStore;
import at.marcelr.mrpvp.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Everything for an invitation on one page, with what the player chose last time already selected - so a duel is
 * two clicks away (player, send). Kit and map show what is chosen; a click opens the choice.
 * <pre>
 *  . [kit] [map] . [wins] [minutes] [hunger] [regen] .
 *  [back] . . . [send] . . . .
 * </pre>
 */
public final class DuelSetupMenu extends Menu {

    private final Player target;
    private boolean loaded;
    private Kit kit;
    /** chosen arena template, null = random */
    private String arena;
    private DuelOptions options;

    private DuelSetupMenu(MRPvp plugin, Player viewer, Player target) {
        super(plugin, viewer, 3, plugin.messages().get("gui-setup-title", tag("player", target.getName())));
        this.target = target;
        this.options = plugin.settings().duel().defaults();
    }

    /** Opens the menu with the remembered settings (loaded from the database, menu shows "loading" until then). */
    public static void open(MRPvp plugin, Player viewer, Player target) {
        DuelSetupMenu menu = new DuelSetupMenu(plugin, viewer, target);
        plugin.games().prefs(viewer.getUniqueId()).whenComplete((prefs, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            menu.apply(error == null ? prefs : null);
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) == menu) {
                menu.render();
            }
        }));
        menu.open();
    }

    private void apply(GameStore.Prefs prefs) {
        List<Kit> kits = kits();
        if (prefs != null) {
            kit = kits.stream().filter(k -> k.id().equals(prefs.kit())).findFirst().orElse(null);
            arena = prefs.arena();
            options = clamp(prefs.options());
        }
        if (kit == null && !kits.isEmpty()) {
            kit = kits.getFirst();
        }
        if (kit != null && !arenas(kit).contains(arena)) {
            arena = arenas(kit).getFirst();
        }
        loaded = true;
    }

    /** Remembered values that the config no longer offers fall back to the defaults. */
    private DuelOptions clamp(DuelOptions o) {
        var duel = plugin.settings().duel();
        int wins = duel.winChoices().contains(o.wins()) ? o.wins() : duel.defaults().wins();
        int minutes = duel.minuteChoices().contains(o.minutes()) ? o.minutes() : duel.defaults().minutes();
        return new DuelOptions(wins, minutes, o.hunger(), o.regen());
    }

    /** Kits the viewer may use that at least one arena allows. */
    List<Kit> kits() {
        return plugin.kits().allowed(viewer).stream().filter(plugin.templates()::playable).toList();
    }

    /** null (random, if the kit fits more than one) followed by every arena the kit fits. */
    List<String> arenas(Kit k) {
        List<String> ids = new ArrayList<>();
        List<ArenaTemplate> fitting = plugin.templates().enabled().stream().filter(a -> a.fits(k)).toList();
        if (fitting.size() != 1) {
            ids.add(null);
        }
        fitting.forEach(a -> ids.add(a.id()));
        return ids;
    }

    Kit kit() {
        return kit;
    }

    String arena() {
        return arena;
    }

    Player target() {
        return target;
    }

    /** From the kit choice; an arena the new kit does not fit becomes the first one it fits (or random). */
    void choose(Kit value) {
        kit = value;
        if (!arenas(value).contains(arena)) {
            arena = arenas(value).getFirst();
        }
    }

    /** From the map choice (null = random). */
    void chooseArena(String value) {
        arena = value;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(18, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new PlayerSelectMenu(plugin, viewer, 0).open();
        });
        if (!loaded) {
            set(13, loading());
            fill();
            return;
        }
        if (kit == null) {
            set(13, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-kits-empty")).build());
            fill();
            return;
        }

        // Kit: what is chosen, a click opens the choice.
        set(10, KitIcon.of(plugin, kit).name(m.item("gui-setup-kit", tag("kit", m.mini(kit.name()))))
                .lore(m.lore("gui-setup-change")).build(), click -> {
            clickSound();
            new KitPickMenu(plugin, viewer, this).open();
        });
        // Map: the same.
        ArenaTemplate template = arena == null ? null : plugin.templates().get(arena);
        Items map = template != null ? Items.of(icon(template)) : Items.of(Material.ENDER_EYE);
        set(11, map.name(m.item("gui-setup-map", tag("map", plugin.games().arenaName(arena))))
                .lore(m.lore(arenas(kit).size() > 1 ? "gui-setup-change" : "gui-setup-only-arena")).build(), click -> {
            if (arenas(kit).size() > 1) {
                clickSound();
                new ArenaPickMenu(plugin, viewer, this).open();
            }
        });

        DuelOptions shown = options.with(kit);
        var duel = plugin.settings().duel();
        set(13, Items.of(Material.GOLDEN_SWORD).amount(Math.clamp(options.wins(), 1, 64)).flags()
                .name(m.item("gui-options-wins", tag("value", options.wins())))
                .lore(m.lore("gui-options-wins-lore")).build(), click -> {
            options = options.withWins(step(duel.winChoices(), options.wins(), click));
            clickSound();
            render();
        });
        boolean minutesFixed = kit.rules().minutes() > 0;
        set(14, Items.of(Material.CLOCK).amount(Math.clamp(shown.minutes(), 1, 64))
                .name(m.item("gui-options-minutes", tag("value", shown.minutes())))
                .lore(m.lore(minutesFixed ? "gui-options-fixed-lore" : "gui-options-minutes-lore")).build(), click -> {
            if (!minutesFixed) {
                options = options.withMinutes(step(duel.minuteChoices(), options.minutes(), click));
                clickSound();
                render();
            }
        });

        boolean hungerFixed = kit.rules().hunger() != null;
        set(15, Items.of(shown.hunger() ? Material.COOKED_BEEF : Material.GOLDEN_CARROT)
                .name(m.item(shown.hunger() ? "gui-options-hunger-on" : "gui-options-hunger-off"))
                .lore(m.lore(hungerFixed ? "gui-options-fixed-lore" : "gui-options-hunger-lore")).build(), click -> {
            if (!hungerFixed) {
                options = options.withHunger(!options.hunger());
                clickSound();
                render();
            }
        });
        boolean regenFixed = kit.rules().regen() != null;
        set(16, Items.of(shown.regen() ? Material.GLISTERING_MELON_SLICE : Material.ROTTEN_FLESH)
                .name(m.item(shown.regen() ? "gui-options-regen-on" : "gui-options-regen-off"))
                .lore(m.lore(regenFixed ? "gui-options-fixed-lore" : "gui-options-regen-lore")).build(), click -> {
            if (!regenFixed) {
                options = options.withRegen(!options.regen());
                clickSound();
                render();
            }
        });

        set(22, Items.of(Material.LIME_CONCRETE).name(m.item("gui-options-send"))
                .lore(m.lore("gui-options-send-lore", tag("kit", m.mini(kit.name())),
                        tag("arena", plugin.games().arenaName(arena)), tag("options", plugin.games().optionsText(shown)),
                        tag("player", target.getName()))).build(), click -> {
            viewer.closeInventory();
            if (target.isOnline() && plugin.games().invite(viewer, target, kit, arena, options)) {
                successSound();
            }
        });
        fill();
    }

    private void fill() {
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }

    static Material icon(ArenaTemplate t) {
        Material icon = Material.matchMaterial(t.icon());
        return icon == null || !icon.isItem() ? Material.STONE_BRICKS : icon;
    }

    /** Next (left click) or previous (right click) of the configured choices, wrapping around. */
    private static int step(List<Integer> choices, int current, ClickType click) {
        int index = choices.indexOf(current);
        if (index < 0) {
            return choices.getFirst();
        }
        boolean back = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        return choices.get(Math.floorMod(index + (back ? -1 : 1), choices.size()));
    }
}
