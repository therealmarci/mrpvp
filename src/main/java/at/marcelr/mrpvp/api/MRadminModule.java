package at.marcelr.mrpvp.api;

import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.HealthEntry;
import at.marcelr.mradmin.api.MRadminService;
import at.marcelr.mradmin.api.WebAlert;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.api.WebUser;
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
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/**
 * MRpvp as a module of MRadmin (module API, see MRadmin's docs/MODULE-API.md): duel counts on the dashboard button,
 * statistics in the player menu, reviews and alerts in the server health and the duel pages of the web interface.
 * <b>Only loaded while MRadmin runs</b> - everything else in MRpvp goes through {@link MRadminLink}, which has no
 * MRadmin types.
 */
final class MRadminModule implements AdminModule, WebModule {

    static final String ID = "mrpvp";

    private static final Map<String, String> PERMISSIONS = Map.of(
            "games", AdminMenu.PERM_GAMES, "stop", AdminMenu.PERM_STOP, "ban", AdminMenu.PERM_BAN,
            "reset", AdminMenu.PERM_RESET, "restore", AdminMenu.PERM_RESTORE, "review", Reviews.PERM_REVIEW,
            "pause", AdminMenu.PERM_PAUSE, "kits", AdminMenu.PERM_KITS);
    /** Rounds with inventories need "games", the review list "review", the kit editor "kits"; everything else only viewing. */
    private static final Map<String, String> REQUESTS = Map.of("games", "games", "game", "games", "reviews", "review",
            "kits", "kits", "kit", "kits");
    private static final Set<String> DATA = Set.of("overview", "live", "games", "game", "alerts", "reviews", "kits", "kit");
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("stop", "stop"), Map.entry("win", "stop"), Map.entry("pause", "pause"), Map.entry("resume", "pause"),
            Map.entry("ban", "ban"), Map.entry("unban", "ban"), Map.entry("reset", "reset"), Map.entry("giveBack", "restore"),
            Map.entry("count", "review"), Map.entry("discard", "review"), Map.entry("uncount", "review"),
            Map.entry("recount", "review"), Map.entry("kitSave", "kits"), Map.entry("kitDelete", "kits"),
            Map.entry("kitOrder", "kits"));

    private final MRPvp plugin;

    private MRadminModule(MRPvp plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------- entry points (MRadminLink)

    /** Registers the module with the running MRadmin. @return false if MRadmin offers no module API */
    static boolean register(MRPvp plugin) {
        MRadminService service = MRadminService.get();
        if (service == null) {
            return false;
        }
        service.register(new MRadminModule(plugin));
        return true;
    }

    static boolean adminMode(Player player) {
        MRadminService service = MRadminService.get();
        return service != null && service.adminMode(player);
    }

    static void webChanged(String type, String id) {
        MRadminService service = MRadminService.get();
        if (service != null) {
            service.webChanged(ID, type, id);
        }
    }

    /** Items another module keeps for the player outside the inventory (e.g. "mrtoolbar": belts). */
    static List<Map<String, Object>> storedItems(Player player, String module) {
        MRadminService service = MRadminService.get();
        return service == null ? List.of() : service.storedItems(player).getOrDefault(module, List.of());
    }

    static void adminLog(String text) {
        MRadminService service = MRadminService.get();
        if (service != null) {
            service.adminLog(ID, text);
        }
    }

    // -------------------------------------------------------------- module

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    @Override
    public String viewPermission() {
        return AdminMenu.PERM_GUI;
    }

    @Override
    public Material icon() {
        return Material.IRON_SWORD;
    }

    /** Running rounds, arenas in use, alerts of the last day, waiting reviews. */
    @Override
    public List<Component> summary(Player viewer) {
        return plugin.messages().as(viewer, () -> plugin.messages().lore("mradmin-summary",
                tag("games", plugin.games().games().size()), tag("arenas", plugin.slots().inUse()),
                tag("alerts", plugin.monitor().recent().size()), tag("bans", plugin.bans().all().size()),
                tag("reviews", plugin.reviews().pending())));
    }

    /** Duel statistics, ban and the current round of a player. */
    @Override
    public List<Component> playerSummary(Player viewer, UUID player) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        String name = offline.getName() == null ? player.toString() : offline.getName();
        PlayerStats stats;
        try {
            stats = plugin.database().call(c -> StatsStore.stats(c, player, name, StatsStore.ALL));
        } catch (SQLException e) {
            return List.of();
        }
        return plugin.messages().as(viewer, () -> {
            var m = plugin.messages();
            List<Component> lines = new ArrayList<>(m.lore("mradmin-player", tag("wins", stats.wins()),
                    tag("games", stats.games()), tag("kd", decimal(stats.kd(), 2))));
            Player online = offline.getPlayer();
            Game game = online == null ? null : plugin.games().gameOf(online);
            if (game != null) {
                lines.addAll(m.lore("mradmin-player-ingame", tag("id", game.id())));
            }
            Bans.Ban ban = plugin.bans().get(player);
            if (ban != null) {
                lines.addAll(m.lore("mradmin-player-banned", tag("until", ban.until() == null
                        ? m.plain("ban-permanent") : m.dateTime(ban.until()))));
            }
            return lines;
        });
    }

    /** MRpvp's admin view of the player. */
    @Override
    public boolean openMenu(Player viewer, Runnable back) {
        if (!viewer.hasPermission(AdminMenu.PERM_GUI)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        plugin.messages().run(viewer, () -> new AdminMenu(plugin, viewer).open());
        return true;
    }

    @Override
    public boolean openPlayerMenu(Player viewer, UUID player, Runnable back) {
        if (!viewer.hasPermission(AdminMenu.PERM_PLAYERS)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        plugin.messages().run(viewer, () -> new PlayerAdminMenu(plugin, viewer, player,
                offline.getName() == null ? player.toString() : offline.getName(), back).open());
        return true;
    }

    /**
     * Rounds waiting for a review first (with "review" permission), then the conspicuous rounds of the last day; a
     * click opens the round, where a waiting one can be counted or discarded.
     */
    @Override
    public List<HealthEntry> health(Player viewer) {
        if (!viewer.hasPermission(AdminMenu.PERM_GAMES) && !viewer.hasPermission(Reviews.PERM_REVIEW)) {
            return List.of();
        }
        return plugin.messages().as(viewer, () -> {
            var m = plugin.messages();
            List<HealthEntry> result = new ArrayList<>();
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
                        result.add(new HealthEntry(String.valueOf(p.id()), icon));
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
                result.add(new HealthEntry(String.valueOf(a.gameId()), icon));
            }
            return result;
        });
    }

    /** Opens the round of a review or alert. */
    @Override
    public boolean openHealth(Player viewer, String id, Runnable back) {
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

    /** In a round, the arena editor or watching - other plugins leave the player and their inventory alone. */
    @Override
    public boolean busy(Player player) {
        return plugin.stateKeeper().busy(player);
    }

    /** The arena world: no chunk loaders, no sleep quota there. */
    @Override
    public boolean ownsWorld(org.bukkit.World world) {
        org.bukkit.World arena = plugin.arenaWorld().world();
        return arena != null && arena.equals(world);
    }

    // ----------------------------------------------------------------- web

    @Override
    public WebModule web() {
        return this;
    }

    @Override
    public Map<String, String> permissions() {
        return PERMISSIONS;
    }

    @Override
    public Map<String, String> requests() {
        return REQUESTS;
    }

    @Override
    public @Nullable Object data(String request, Map<String, String> args) {
        return switch (request) {
            case "kits" -> KitWeb.list(plugin);
            case "kit" -> KitWeb.kit(plugin, args.getOrDefault("id", ""));
            default -> DATA.contains(request) ? WebData.handle(plugin, request, args) : null;
        };
    }

    @Override
    public Map<String, String> actions() {
        return ACTIONS;
    }

    @Override
    public @Nullable String action(String action, Map<String, Object> args, WebUser user) {
        switch (action) {
            case "kitSave" -> {
                return KitWeb.save(plugin, args, user.name());
            }
            case "kitDelete" -> {
                return KitWeb.delete(plugin, args, user.name());
            }
            case "kitOrder" -> {
                return KitWeb.order(plugin, args);
            }
            default -> {
                // the round and player actions below
            }
        }
        Map<String, Object> all = new HashMap<>(args);
        all.put("action", action);
        all.put("admin", user.name());
        Object error = WebData.handle(plugin, "action", all);
        return error == null ? null : String.valueOf(error);
    }

    @Override
    public @Nullable Object player(UUID player) {
        return WebData.handle(plugin, "player", Map.of("uuid", player.toString()));
    }

    /** Rounds waiting for a review. */
    @Override
    public List<WebAlert> alerts() {
        int pending = plugin.reviews().pending();
        return pending == 0 ? List.of() : List.of(new WebAlert("info",
                plugin.messages().plain("web-alert-reviews", "count", String.valueOf(pending)), "/pvp"));
    }

    @Override
    public Map<UUID, List<String>> flags() {
        Map<UUID, List<String>> flags = new LinkedHashMap<>();
        if (WebData.handle(plugin, "flags", Map.of()) instanceof Map<?, ?> answer) {
            answer.forEach((uuid, keys) -> {
                if (keys instanceof List<?> list) {
                    flags.put(UUID.fromString(String.valueOf(uuid)), list.stream().map(String::valueOf).toList());
                }
            });
        }
        return flags;
    }
}
