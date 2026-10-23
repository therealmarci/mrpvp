package at.marcelr.mrpvp.api;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.arena.SlotManager;
import at.marcelr.mrpvp.game.Bans;
import at.marcelr.mrpvp.game.Fighter;
import at.marcelr.mrpvp.game.Game;
import at.marcelr.mrpvp.game.Invite;
import at.marcelr.mrpvp.game.Monitor;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.snapshot.Snapshot;
import at.marcelr.mrpvp.snapshot.SnapshotStore;
import at.marcelr.mrpvp.snapshot.ToolbarBelts;
import at.marcelr.mrpvp.stats.GameQueries;
import at.marcelr.mrpvp.stats.PlayerStats;
import at.marcelr.mrpvp.stats.StatsStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Plain data (maps, lists, strings, numbers) for MRadmin's web interface - no MRpvp or Bukkit types leave this
 * class. Called by {@link MRadminHook#web} on the server thread. MRadmin checks the permissions (MRpvp's own:
 * mrpvp.admin.gui to look, .games for rounds with inventories, .stop / .ban / .reset / .restore for actions).
 * <pre>
 *  overview                      running rounds (live), invitations, slots, arenas, alerts, totals
 *  live    {id}                  one running round: both players with health, inventory, position, the mini map
 *  games   {filter, offset, uuid?} finished rounds, newest first (filter all | win | draw | aborted), 30 per page
 *  game    {id}                  one round: players, saved inventories (on entering / at the end), combat log,
 *                                positions and mini map
 *  player  {uuid}                one player: statistics, ban, current round, last rounds, owed states
 *  flags                         uuid -> marks for MRadmin's player list (pvp-ingame, pvp-banned)
 *  alerts                        conspicuous rounds of the last 24 h
 *  reviews                       rounds waiting for an admin (flags, players, result), newest first
 *  action  {action, admin, ...}  stop {id} | ban {uuid, duration (ms, 0 = permanent), reason} | unban {uuid}
 *                                | reset {uuid} | giveBack {uuid} | count {id} | discard {id} (review)
 *                                -> null or an error key
 * </pre>
 * Times are epoch ms, durations ms, health in half hearts (Minecraft points), positions relative to the arena corner.
 */
final class WebData {

    private static final int PAGE = 30;
    private static final int MAX_REASON = 200;

    private WebData() {
    }

    static Object handle(MRPvp plugin, String request, Map<?, ?> args) {
        try {
            return switch (request) {
                case "overview" -> overview(plugin);
                case "live" -> live(plugin, number(args.get("id")));
                case "games" -> games(plugin, args);
                case "game" -> game(plugin, number(args.get("id")));
                case "player" -> {
                    UUID uuid = uuid(args.get("uuid"));
                    yield uuid == null ? null : player(plugin, uuid);
                }
                case "flags" -> flags(plugin);
                case "alerts" -> alerts(plugin);
                case "reviews" -> reviews(plugin);
                case "action" -> action(plugin, args);
                default -> null;
            };
        } catch (SQLException e) {
            plugin.getLogger().warning("Web request '" + request + "' failed: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ overview / live

    private static Map<String, Object> overview(MRPvp plugin) {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> games = new ArrayList<>();
        for (Game game : plugin.games().games()) {
            games.add(gameSummary(plugin, game));
        }
        r.put("games", games);
        List<Map<String, Object>> invites = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Invite invite : plugin.games().invites().to(p.getUniqueId(), now)) {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("from", player(invite.from(), invite.fromName()));
                i.put("to", player(invite.to(), invite.toName()));
                i.put("kit", invite.kit());
                i.put("arena", invite.arena());
                i.put("expires", invite.expires());
                invites.add(i);
            }
        }
        r.put("invites", invites);
        List<Map<String, Object>> arenas = new ArrayList<>();
        for (ArenaTemplate t : plugin.templates().all()) {
            int[] counts = plugin.slots().counts(t.id());
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", t.id());
            a.put("name", plain(t.name()));
            a.put("size", List.of(t.sizeX(), t.sizeY(), t.sizeZ()));
            a.put("version", t.version());
            a.put("enabled", t.enabled());
            a.put("build", t.allowBuild());
            a.put("pool", t.pool());
            a.put("ready", counts[0]);
            a.put("used", counts[1]);
            a.put("kits", t.kits());
            arenas.add(a);
        }
        r.put("arenas", arenas);
        List<Map<String, Object>> slots = new ArrayList<>();
        for (SlotManager.Slot slot : plugin.slots().slots()) {
            if (slot.state() == SlotManager.State.EMPTY) {
                continue;
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("index", slot.index());
            s.put("template", slot.template());
            s.put("state", slot.state().name().toLowerCase(Locale.ROOT));
            s.put("pasteMillis", slot.lastPasteMillis());
            slots.add(s);
        }
        r.put("slots", slots);
        List<Map<String, Object>> kits = new ArrayList<>();
        for (Kit kit : plugin.kits().all()) {
            kits.add(Map.of("id", kit.id(), "name", plain(kit.name()), "icon", kit.icon().getKey().getKey()));
        }
        r.put("kits", kits);
        r.put("alerts", alerts(plugin).stream().limit(5).toList());
        r.put("bans", plugin.bans().all().size());
        r.put("reviews", plugin.reviews().pending());
        r.put("now", System.currentTimeMillis());
        return r;
    }

    private static Map<String, Object> gameSummary(MRPvp plugin, Game game) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", game.id());
        g.put("state", game.state().name().toLowerCase(Locale.ROOT));
        g.put("elapsed", game.elapsed());
        g.put("kit", game.kit().id());
        g.put("kitName", plain(game.kit().name()));
        g.put("arena", game.arena().name());
        g.put("arenaName", arenaName(plugin, game.arena().name()));
        g.put("slot", game.arena().slot());
        List<Map<String, Object>> players = new ArrayList<>();
        for (Fighter f : List.of(game.one(), game.two())) {
            Map<String, Object> p = fighter(f);
            Player online = Bukkit.getPlayer(f.uuid());
            p.put("online", online != null);
            if (online != null) {
                p.put("health", online.getHealth());
                p.put("maxHealth", at.marcelr.mrpvp.snapshot.PlayerState.maxHealth(online));
                p.put("absorption", online.getAbsorptionAmount());
                p.put("food", online.getFoodLevel());
            }
            players.add(p);
        }
        g.put("players", players);
        return g;
    }

    private static Map<String, Object> live(MRPvp plugin, long id) throws SQLException {
        Game game = plugin.games().game(id);
        if (game == null) {
            return null;
        }
        Map<String, Object> r = gameSummary(plugin, game);
        var region = game.arena().region();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) r.get("players");
        for (int i = 0; i < players.size(); i++) {
            Fighter f = i == 0 ? game.one() : game.two();
            Player online = Bukkit.getPlayer(f.uuid());
            if (online == null) {
                continue;
            }
            Map<String, Object> p = players.get(i);
            Location at = online.getLocation();
            p.put("x", at.getX() - region.getMinX());
            p.put("y", at.getY() - region.getMinY());
            p.put("z", at.getZ() - region.getMinZ());
            p.put("yaw", at.getYaw());
            PlayerInventory inv = online.getInventory();
            p.put("inventory", ItemView.list(inv.getStorageContents(), 0));
            p.put("armor", ItemView.list(inv.getArmorContents(), 0));
            p.put("offhand", ItemView.list(new ItemStack[] {inv.getItemInOffHand()}, 0));
            p.put("held", inv.getHeldItemSlot());
            List<Map<String, Object>> effects = new ArrayList<>();
            for (PotionEffect e : online.getActivePotionEffects()) {
                effects.add(Map.of("type", e.getType().getKey().getKey(), "level", e.getAmplifier() + 1,
                        "seconds", e.getDuration() / 20));
            }
            p.put("effects", effects);
        }
        // Server time, so the page can tell how long ago lastHitAt / lastHurtAt were (a dot lights up).
        r.put("now", System.currentTimeMillis());
        r.put("map", map(plugin, game.arena().name(), game.arena().slot()));
        r.put("events", events(plugin.database().call(c -> GameQueries.events(c, id))));
        return r;
    }

    // ------------------------------------------------------------------ finished rounds

    private static List<Map<String, Object>> games(MRPvp plugin, Map<?, ?> args) throws SQLException {
        GameQueries.ResultFilter filter;
        try {
            filter = GameQueries.ResultFilter.valueOf(String.valueOf(args.get("filter")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            filter = GameQueries.ResultFilter.ALL;
        }
        int offset = (int) Math.max(0, number(args.get("offset")));
        UUID player = uuid(args.get("uuid"));
        GameQueries.ResultFilter f = filter;
        List<GameQueries.GameRow> rows = plugin.database().call(c -> GameQueries.list(c, player, f, offset, PAGE));
        List<Map<String, Object>> result = new ArrayList<>();
        for (GameQueries.GameRow row : rows) {
            result.add(row(plugin, row));
        }
        return result;
    }

    private static Map<String, Object> game(MRPvp plugin, long id) throws SQLException {
        GameQueries.GameRow row = plugin.database().call(c -> GameQueries.get(c, id));
        if (row == null) {
            return null;
        }
        Map<String, Object> r = row(plugin, row);
        r.put("running", plugin.games().game(id) != null);
        List<Snapshot> snapshots = plugin.database().call(c -> SnapshotStore.ofGame(c, id));
        List<Map<String, Object>> states = new ArrayList<>();
        for (Snapshot s : snapshots) {
            states.add(snapshot(s));
        }
        r.put("snapshots", states);
        r.put("events", events(plugin.database().call(c -> GameQueries.events(c, id))));
        List<Map<String, Object>> positions = new ArrayList<>();
        double[] origin = origin(plugin, row.slot());
        for (GameQueries.PositionRow p : plugin.database().call(c -> GameQueries.positions(c, id))) {
            positions.add(Map.of("t", p.t(), "side", p.side(), "x", p.x() - origin[0], "z", p.z() - origin[1],
                    "yaw", p.yaw(), "health", p.health()));
        }
        r.put("positions", positions);
        r.put("map", map(plugin, row.arena(), row.slot()));
        return r;
    }

    private static Map<String, Object> row(MRPvp plugin, GameQueries.GameRow row) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", row.id());
        g.put("arena", row.arena());
        g.put("arenaName", arenaName(plugin, row.arena()));
        g.put("kit", row.kit());
        Kit kit = plugin.kits().get(row.kit());
        g.put("kitName", kit == null ? row.kit() : plain(kit.name()));
        g.put("startedAt", row.startedAt());
        g.put("endedAt", row.endedAt());
        g.put("duration", row.duration());
        g.put("result", row.result() == null ? null : row.result().toLowerCase(Locale.ROOT));
        g.put("reason", row.reason() == null ? null : row.reason().toLowerCase(Locale.ROOT));
        g.put("winner", row.winner() == null ? null : row.winner().toString());
        g.put("stoppedBy", row.stoppedBy());
        g.put("counted", row.counted());
        g.put("review", row.review() == null ? null : row.review().toLowerCase(Locale.ROOT));
        g.put("flags", List.copyOf(new java.util.TreeSet<>(at.marcelr.mrpvp.game.Reviews.flags(row.flags()))));
        g.put("reviewedBy", row.reviewedBy());
        List<Map<String, Object>> players = new ArrayList<>();
        for (GameQueries.PlayerLine p : row.players()) {
            Map<String, Object> m = player(p.uuid(), p.name());
            m.put("side", p.side());
            m.put("outcome", p.outcome() == null ? null : p.outcome().toLowerCase(Locale.ROOT));
            m.put("healthEnd", p.healthEnd());
            m.put("damageDealt", p.damageDealt());
            m.put("damageTaken", p.damageTaken());
            m.put("hits", p.hits());
            m.put("crits", p.crits());
            m.put("arrowsShot", p.arrowsShot());
            m.put("arrowsHit", p.arrowsHit());
            m.put("totems", p.totems());
            m.put("pearls", p.pearls());
            m.put("kills", p.kills());
            m.put("deaths", p.deaths());
            players.add(m);
        }
        g.put("players", players);
        return g;
    }

    private static Map<String, Object> snapshot(Snapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id());
        m.put("uuid", s.uuid().toString());
        m.put("name", s.name());
        m.put("phase", s.phase().name().toLowerCase(Locale.ROOT));
        m.put("takenAt", s.takenAt());
        m.put("restoredAt", s.restoredAt());
        try {
            m.put("inventory", ItemView.list(ItemStack.deserializeItemsFromBytes(s.inventory()), 0));
            m.put("armor", ItemView.list(ItemStack.deserializeItemsFromBytes(s.armor()), 0));
            m.put("offhand", ItemView.list(ItemStack.deserializeItemsFromBytes(s.offhand()), 0));
            m.put("enderchest", ItemView.list(ItemStack.deserializeItemsFromBytes(s.enderchest()), 0));
        } catch (RuntimeException e) {
            m.put("unreadable", true);
        }
        List<ToolbarBelts.Belt> belts = ToolbarBelts.read(s.toolbarBelts());
        if (belts != null) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ToolbarBelts.Belt belt : belts) {
                list.add(Map.of("slot", belt.slot(), "selected", belt.selected(),
                        "items", ItemView.list(belt.items().toArray(ItemStack[]::new), 0)));
            }
            m.put("belts", list);
        }
        m.put("level", s.level());
        m.put("health", s.health());
        m.put("food", s.food());
        m.put("gamemode", s.gameMode().toLowerCase(Locale.ROOT));
        m.put("world", s.world());
        m.put("x", Math.round(s.x()));
        m.put("y", Math.round(s.y()));
        m.put("z", Math.round(s.z()));
        return m;
    }

    private static List<Map<String, Object>> events(List<GameQueries.EventRow> events) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GameQueries.EventRow e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", e.t());
            m.put("type", e.type().toLowerCase(Locale.ROOT));
            m.put("actor", e.actor() == null ? null : e.actor().toString());
            m.put("target", e.target() == null ? null : e.target().toString());
            m.put("damage", e.damage());
            m.put("cause", e.cause() == null ? null : e.cause().toLowerCase(Locale.ROOT));
            m.put("weapon", e.weapon() == null ? null : e.weapon().toLowerCase(Locale.ROOT));
            result.add(m);
        }
        return result;
    }

    // ------------------------------------------------------------------ player, flags, alerts

    private static Map<String, Object> player(MRPvp plugin, UUID uuid) throws SQLException {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName() == null ? uuid.toString() : offline.getName();
        Map<String, Object> r = new LinkedHashMap<>();
        PlayerStats s = plugin.database().call(c -> StatsStore.stats(c, uuid, name, StatsStore.ALL));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("games", s.games());
        stats.put("wins", s.wins());
        stats.put("losses", s.losses());
        stats.put("draws", s.draws());
        stats.put("kills", s.kills());
        stats.put("deaths", s.deaths());
        stats.put("kd", s.kd());
        stats.put("winRate", s.winRate());
        stats.put("streak", s.streak());
        stats.put("bestStreak", s.bestStreak());
        stats.put("damageDealt", s.damageDealt());
        stats.put("damageTaken", s.damageTaken());
        stats.put("accuracy", s.accuracy());
        r.put("stats", stats);
        Bans.Ban ban = plugin.bans().get(uuid);
        if (ban != null) {
            r.put("ban", Map.of("until", ban.until() == null ? 0L : ban.until(), "permanent", ban.until() == null,
                    "reason", ban.reason() == null ? "" : ban.reason(), "by", ban.by(), "since", ban.createdAt()));
        }
        Player online = Bukkit.getPlayer(uuid);
        Game game = online == null ? null : plugin.games().gameOf(online);
        r.put("game", game == null ? null : game.id());
        r.put("inside", online != null && plugin.stateKeeper().inside(online));
        List<Map<String, Object>> games = new ArrayList<>();
        for (GameQueries.GameRow row : plugin.database().call(c -> GameQueries.list(c, uuid, GameQueries.ResultFilter.ALL, 0, 10))) {
            games.add(row(plugin, row));
        }
        r.put("games", games);
        List<Snapshot> owed = plugin.database().call(c -> SnapshotStore.owed(c, uuid));
        r.put("owed", owed.size());
        return r;
    }

    private static Map<String, List<String>> flags(MRPvp plugin) {
        Map<String, List<String>> flags = new LinkedHashMap<>();
        for (Game game : plugin.games().games()) {
            for (Fighter f : List.of(game.one(), game.two())) {
                flags.computeIfAbsent(f.uuid().toString(), k -> new ArrayList<>()).add("pvp-ingame");
            }
        }
        for (Bans.Ban ban : plugin.bans().all()) {
            flags.computeIfAbsent(ban.uuid().toString(), k -> new ArrayList<>()).add("pvp-banned");
        }
        return flags;
    }

    private static List<Map<String, Object>> alerts(MRPvp plugin) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Monitor.Alert a : plugin.monitor().recent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", a.time());
            m.put("game", a.gameId());
            m.put("type", a.type().name().toLowerCase(Locale.ROOT));
            m.put("one", a.one());
            m.put("two", a.two());
            m.put("detail", a.detail());
            result.add(m);
        }
        return result;
    }


    private static List<Map<String, Object>> reviews(MRPvp plugin) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (StatsStore.Pending p : plugin.reviews().listNow(100)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("game", p.id());
            m.put("kit", p.kit());
            Kit kit = plugin.kits().get(p.kit());
            m.put("kitName", kit == null ? p.kit() : plain(kit.name()));
            m.put("endedAt", p.endedAt());
            m.put("result", p.result() == null ? null : p.result().toLowerCase(Locale.ROOT));
            m.put("reason", p.reason() == null ? null : p.reason().toLowerCase(Locale.ROOT));
            m.put("flags", List.copyOf(new java.util.TreeSet<>(at.marcelr.mrpvp.game.Reviews.flags(p.flags()))));
            m.put("one", player(p.one(), p.oneName()));
            m.put("two", player(p.two(), p.twoName()));
            m.put("winner", p.winner() == null ? null : p.winner().toString());
            result.add(m);
        }
        return result;
    }
    // ------------------------------------------------------------------ actions

    private static String action(MRPvp plugin, Map<?, ?> args) {
        String action = String.valueOf(args.get("action"));
        String admin = text(args.get("admin"), "web");
        switch (action) {
            case "stop" -> {
                Game game = plugin.games().game(number(args.get("id")));
                if (game == null) {
                    return "game-not-running";
                }
                plugin.games().stop(game, admin + " (web)");
                return null;
            }
            case "ban" -> {
                UUID uuid = uuid(args.get("uuid"));
                if (uuid == null) {
                    return "not-found";
                }
                long duration = number(args.get("duration"));
                String reason = text(args.get("reason"), "");
                if (reason.length() > MAX_REASON) {
                    reason = reason.substring(0, MAX_REASON);
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
                plugin.bans().ban(uuid, target.getName() == null ? uuid.toString() : target.getName(),
                        duration <= 0 ? null : System.currentTimeMillis() + duration, reason, admin);
                plugin.games().invites().removeAll(uuid);
                plugin.getLogger().info(admin + " (web) banned " + target.getName() + " from duels.");
                return null;
            }
            case "unban" -> {
                UUID uuid = uuid(args.get("uuid"));
                if (uuid == null || plugin.bans().get(uuid) == null) {
                    return "not-banned";
                }
                plugin.bans().unban(uuid);
                plugin.getLogger().info(admin + " (web) lifted a duel ban (" + uuid + ").");
                return null;
            }
            case "reset" -> {
                UUID uuid = uuid(args.get("uuid"));
                if (uuid == null) {
                    return "not-found";
                }
                long now = System.currentTimeMillis();
                plugin.database().submit(c -> {
                    StatsStore.reset(c, uuid, now);
                    return null;
                });
                plugin.getLogger().info(admin + " (web) reset the duel statistics of " + uuid + ".");
                return null;
            }
            case "count", "discard" -> {
                long id = number(args.get("id"));
                try {
                    Boolean changed = plugin.reviews().decide(id, action.equals("count"), admin + " (web)").get();
                    return Boolean.TRUE.equals(changed) ? null : "not-pending";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "database-error";
                } catch (java.util.concurrent.ExecutionException e) {
                    return "database-error";
                }
            }
            case "giveBack" -> {
                UUID uuid = uuid(args.get("uuid"));
                Player online = uuid == null ? null : Bukkit.getPlayer(uuid);
                if (online == null) {
                    return "player-offline";
                }
                if (plugin.games().inGame(uuid) || plugin.stateKeeper().inside(online)) {
                    return "player-busy";
                }
                try {
                    List<Snapshot> owed = plugin.database().call(c -> SnapshotStore.owed(c, uuid));
                    if (owed.isEmpty()) {
                        return "nothing-owed";
                    }
                    if (!plugin.stateKeeper().giveBackNow(online, owed.getFirst())) {
                        return "give-back-failed";
                    }
                } catch (SQLException e) {
                    return "database-error";
                }
                plugin.getLogger().info(admin + " (web) gave " + online.getName() + " their saved state back.");
                return null;
            }
            default -> {
                return "unknown-action";
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The mini map of an arena (current template) with the corner positions are measured from. */
    private static Map<String, Object> map(MRPvp plugin, String arena, int slot) {
        ArenaTemplate t = plugin.templates().get(arena);
        return t == null ? null : plugin.slots().map(t);
    }

    /** Lowest corner (x, z) of the arena a round was in. */
    private static double[] origin(MRPvp plugin, int slot) {
        if (slot < 0) {
            // built-in arena: slot n at x = n * 1000, 16 blocks around
            return new double[] {-slot * 1000.0 - 16, -16};
        }
        Location o = plugin.slots().origin(slot);
        return new double[] {o.getX(), o.getZ()};
    }

    private static Map<String, Object> fighter(Fighter f) {
        Map<String, Object> p = player(f.uuid(), f.name());
        p.put("side", f.side());
        p.put("damageDealt", f.damageDealt());
        p.put("damageTaken", f.damageTaken());
        p.put("hits", f.hits());
        p.put("crits", f.crits());
        p.put("arrowsShot", f.arrowsShot());
        p.put("arrowsHit", f.arrowsHit());
        p.put("lastHitAt", f.lastHitAt());
        p.put("lastHurtAt", f.lastHurtAt());
        return p;
    }

    private static Map<String, Object> player(UUID uuid, String name) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("uuid", uuid.toString());
        p.put("name", name);
        return p;
    }

    private static String arenaName(MRPvp plugin, String id) {
        ArenaTemplate t = plugin.templates().get(id);
        return t == null ? id : plain(t.name());
    }

    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static String text(Object value, String otherwise) {
        return value == null ? otherwise : String.valueOf(value);
    }

    private static long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static UUID uuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
