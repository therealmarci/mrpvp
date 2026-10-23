package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.stats.PlayerStats;
import at.marcelr.mrpvp.stats.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/** A player's duel statistics - all kits or one kit (switch with the chest). */
public final class StatsMenu extends Menu {

    private final UUID target;
    private final String name;
    private final Runnable back;
    private String kit;
    private PlayerStats stats;
    private List<String> kits = List.of();
    private String favouriteKit;
    private String favouriteArena;
    /** viewer vs target (only when looking at someone else) */
    private int[] headToHead;

    public StatsMenu(MRPvp plugin, Player viewer, UUID target, String name, String kit, Runnable back) {
        super(plugin, viewer, 4, plugin.messages().get("gui-stats-title", tag("player", name)));
        this.target = target;
        this.name = name;
        this.kit = kit;
        this.back = back;
        reload();
    }

    private void reload() {
        String k = kit;
        boolean other = !target.equals(viewer.getUniqueId());
        UUID self = viewer.getUniqueId();
        load(c -> new Object[] {StatsStore.stats(c, target, name, k), StatsStore.kitsOf(c, target),
                StatsStore.favourite(c, target, "kit"), StatsStore.favourite(c, target, "arena"),
                other ? StatsStore.headToHead(c, self, target) : null}, data -> {
            stats = (PlayerStats) data[0];
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) data[1];
            kits = list;
            favouriteKit = (String) data[2];
            favouriteArena = (String) data[3];
            headToHead = (int[]) data[4];
        });
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        if (stats == null) {
            set(13, loading());
            return;
        }
        PlayerStats s = stats;
        var head = Bukkit.getPlayer(target) instanceof Player online ? Items.head(online)
                : Items.head(Bukkit.getOfflinePlayer(target));
        set(4, head.name(m.item("gui-stats-head", tag("player", name)))
                .lore(m.lore("gui-stats-head-lore", tag("kit", kitName(kit)))).build());

        set(10, Items.of(Material.IRON_SWORD).flags().name(m.item("gui-stats-games", tag("games", s.games())))
                .lore(m.lore("gui-stats-games-lore", tag("wins", s.wins()), tag("losses", s.losses()),
                        tag("draws", s.draws()), tag("rate", decimal(s.winRate(), 1)))).build());
        set(11, Items.of(Material.SKELETON_SKULL).name(m.item("gui-stats-kd", tag("kd", decimal(s.kd(), 2))))
                .lore(m.lore("gui-stats-kd-lore", tag("kills", s.kills()), tag("deaths", s.deaths()))).build());
        set(12, Items.of(Material.BLAZE_POWDER).name(m.item("gui-stats-streak", tag("streak", s.streak())))
                .lore(m.lore("gui-stats-streak-lore", tag("best", s.bestStreak()))).build());
        set(14, Items.of(Material.REDSTONE).name(m.item("gui-stats-damage", tag("dealt", decimal(s.damageDealt() / 2, 1))))
                .lore(m.lore("gui-stats-damage-lore", tag("taken", decimal(s.damageTaken() / 2, 1)),
                        tag("average", decimal(s.averageDamage() / 2, 1)))).build());
        set(15, Items.of(Material.BOW).name(m.item("gui-stats-bow", tag("accuracy", decimal(s.accuracy(), 1))))
                .lore(m.lore("gui-stats-bow-lore", tag("shot", s.arrowsShot()), tag("hit", s.arrowsHit()),
                        tag("totems", s.totems()))).build());
        set(16, Items.of(Material.CLOCK).name(m.item("gui-stats-time",
                tag("time", GameManager.clock(s.averageDurationMillis())))).build());
        set(22, Items.of(Material.ITEM_FRAME).name(m.item("gui-stats-favourites"))
                .lore(m.lore("gui-stats-favourites-lore",
                        tag("kit", favouriteKit == null ? m.get("gui-stats-none") : kitName(favouriteKit)),
                        tag("arena", favouriteArena == null ? m.get("gui-stats-none")
                                : plugin.games().arenaName(favouriteArena)))).build());
        if (headToHead != null) {
            set(20, Items.head(viewer).name(m.item("gui-stats-versus", tag("player", name)))
                    .lore(m.lore("gui-stats-versus-lore", tag("wins", headToHead[0]), tag("losses", headToHead[1]),
                            tag("draws", headToHead[2]))).build());
        }

        // Kit switch: all -> each kit with rounds -> all ...
        List<String> order = new ArrayList<>();
        order.add(StatsStore.ALL);
        order.addAll(kits);
        set(24, Items.of(Material.CHEST).name(m.item("gui-stats-kit", tag("kit", kitName(kit))))
                .lore(m.lore("gui-stats-kit-lore")).build(), click -> {
            int index = order.indexOf(kit);
            int step = click == ClickType.RIGHT ? order.size() - 1 : 1;
            kit = order.get((Math.max(0, index) + step) % order.size());
            stats = null;
            clickSound();
            render();
            reload();
        });
        set(27, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        set(31, Items.of(Material.BOOK).name(m.item("gui-stats-games-button")).build(), click -> {
            clickSound();
            new GamesMenu(plugin, viewer, target, name, viewer.hasPermission(AdminMenu.PERM_GAMES)
                    && !target.equals(viewer.getUniqueId()), this::open).open();
        });
        fillRow(3);
    }

    private net.kyori.adventure.text.Component kitName(String id) {
        if (id == null || id.isEmpty()) {
            return plugin.messages().get("gui-stats-all-kits");
        }
        Kit k = plugin.kits().get(id);
        return k == null ? net.kyori.adventure.text.Component.text(id) : plugin.messages().mini(k.name());
    }
}
