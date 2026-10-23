package at.marcelr.mrpvp.api;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Bans;
import at.marcelr.mrpvp.game.Game;
import at.marcelr.mrpvp.game.Monitor;
import at.marcelr.mrpvp.game.Reviews;
import at.marcelr.mrpvp.gui.AdminMenu;
import at.marcelr.mrpvp.gui.GameMenu;
import at.marcelr.mrpvp.gui.PlayerAdminMenu;
import at.marcelr.mrpvp.stats.PlayerStats;
import at.marcelr.mrpvp.stats.StatsStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/**
 * Connection point for MRadmin (admin compass, player menu, server health, web interface). MRadmin finds this class
 * at runtime (no dependency in either direction) and calls these methods; MRpvp works the same without MRadmin.
 * Keep the signatures stable and raise {@link #API_VERSION} only together with MRadmin.
 */
public final class MRadminHook {

    public static final int API_VERSION = 4;

    private MRadminHook() {
    }

    private static MRPvp plugin() {
        return JavaPlugin.getPlugin(MRPvp.class);
    }

    /** Lines for the MRpvp button: running rounds, arenas in use, alerts of the last day, waiting reviews. */
    public static List<Component> summary(Player viewer) {
        MRPvp plugin = plugin();
        return plugin.messages().as(viewer, () -> plugin.messages().lore("mradmin-summary",
                tag("games", plugin.games().games().size()), tag("arenas", plugin.slots().inUse()),
                tag("alerts", plugin.monitor().recent().size()), tag("bans", plugin.bans().all().size()),
                tag("reviews", plugin.reviews().pending())));
    }

    /** Duel statistics, ban and the current round of a player. */
    public static List<Component> playerSummary(Player viewer, UUID target) {
        MRPvp plugin = plugin();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        String name = offline.getName() == null ? target.toString() : offline.getName();
        PlayerStats stats;
        try {
            stats = plugin.database().call(c -> StatsStore.stats(c, target, name, StatsStore.ALL));
        } catch (SQLException e) {
            return List.of();
        }
        PlayerStats s = stats;
        return plugin.messages().as(viewer, () -> {
            var m = plugin.messages();
            List<Component> lines = new ArrayList<>(m.lore("mradmin-player", tag("wins", s.wins()),
                    tag("games", s.games()), tag("kd", decimal(s.kd(), 2))));
            Player online = offline.getPlayer();
            Game game = online == null ? null : plugin.games().gameOf(online);
            if (game != null) {
                lines.addAll(m.lore("mradmin-player-ingame", tag("id", game.id())));
            }
            Bans.Ban ban = plugin.bans().get(target);
            if (ban != null) {
                lines.addAll(m.lore("mradmin-player-banned", tag("until", ban.until() == null
                        ? m.plain("ban-permanent") : m.dateTime(ban.until()))));
            }
            return lines;
        });
    }

    /** Opens MRpvp's admin view of the player; "back" returns to MRadmin. @return false without permission */
    public static boolean openPlayerMenu(Player viewer, UUID target, Runnable back) {
        MRPvp plugin = plugin();
        if (!viewer.hasPermission(AdminMenu.PERM_PLAYERS)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        plugin.messages().run(viewer, () -> new PlayerAdminMenu(plugin, viewer, target,
                offline.getName() == null ? target.toString() : offline.getName(), back).open());
        return true;
    }

    /**
     * For MRadmin's server health: rounds waiting for a review first (with "review" permission), then the
     * conspicuous rounds of the last day. "icon" (ItemStack) and "id" (Game-ID); a click opens the round, where a
     * waiting one can be counted or discarded.
     */
    public static List<Map<String, Object>> activity(Player viewer) {
        MRPvp plugin = plugin();
        if (!viewer.hasPermission(AdminMenu.PERM_GAMES) && !viewer.hasPermission(Reviews.PERM_REVIEW)) {
            return List.of();
        }
        return plugin.messages().as(viewer, () -> {
            var m = plugin.messages();
            List<Map<String, Object>> result = new ArrayList<>();
            if (viewer.hasPermission(Reviews.PERM_REVIEW)) {
                try {
                    for (StatsStore.Pending p : plugin.reviews().listNow(20)) {
                        ItemStack icon = ItemStack.of(Material.WRITABLE_BOOK);
                        icon.editMeta(meta -> {
                            meta.displayName(m.item("mradmin-review", tag("id", p.id()), tag("one", p.oneName()),
                                    tag("two", p.twoName())));
                            meta.lore(m.lore("mradmin-review-lore", tag("flags",
                                    plugin.reviews().flagNames(Reviews.flags(p.flags()))), tag("date", m.dateTime(p.endedAt()))));
                        });
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("icon", icon);
                        entry.put("id", String.valueOf(p.id()));
                        result.add(entry);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("Could not list the reviews for MRadmin: " + e.getMessage());
                }
            }
            for (Monitor.Alert a : plugin.monitor().recent()) {
                ItemStack icon = ItemStack.of(Material.IRON_SWORD);
                icon.editMeta(meta -> {
                    meta.displayName(m.item("mradmin-alert", tag("id", a.gameId()),
                            tag("type", m.plain("alert-" + a.type().name().toLowerCase(Locale.ROOT)))));
                    meta.lore(m.lore("mradmin-alert-lore", tag("one", a.one()), tag("two", a.two()),
                            tag("detail", a.detail()), tag("date", m.dateTime(a.time()))));
                });
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("icon", icon);
                entry.put("id", String.valueOf(a.gameId()));
                result.add(entry);
            }
            return result;
        });
    }

    /** Opens the round of an alert. */
    public static boolean openActivity(Player viewer, String id, Runnable back) {
        MRPvp plugin = plugin();
        if (!viewer.hasPermission(AdminMenu.PERM_GAMES) && !viewer.hasPermission(Reviews.PERM_REVIEW)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        try {
            long gameId = Long.parseLong(id);
            plugin.messages().run(viewer, () -> new GameMenu(plugin, viewer, gameId, true, back).open());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Data and actions for MRadmin's web interface (hook version 4) - see {@link WebData}. */
    public static Object web(String request, Map<String, Object> args) {
        return WebData.handle(plugin(), request, args);
    }
}
