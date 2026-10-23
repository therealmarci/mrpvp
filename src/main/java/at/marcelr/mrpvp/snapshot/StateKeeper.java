package at.marcelr.mrpvp.snapshot;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.storage.Database;
import at.marcelr.mrpvp.world.ArenaWorld;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Keeps players' own things safe while they are in the arena world (round, editor, debug).
 * <p>
 * The rule: the state is <b>in the database before</b> the inventory is touched, and it is marked as given back
 * <b>before</b> it is handed out. The database is the only truth - whatever is not marked as given back is owed:
 * after a quit, a crash or a reload the player gets it on their next login. Both steps wait for the database
 * (a few milliseconds) on purpose; doing them later would leave a window for duplicated items.
 */
public final class StateKeeper implements Listener {

    /**
     * A player who is in the arena world right now, with what they get back.
     *
     * @param statistics Minecraft statistics from before (protection.restore-statistics), or null
     */
    public record Session(Snapshot owed, Map<Statistic, Integer> statistics) {
    }

    /** Time statistics keep running - resetting them would make play time disappear. */
    private static final Set<Statistic> KEEP = Set.of(Statistic.PLAY_ONE_MINUTE, Statistic.TOTAL_WORLD_TIME,
            Statistic.TIME_SINCE_DEATH, Statistic.TIME_SINCE_REST, Statistic.LEAVE_GAME, Statistic.SNEAK_TIME);

    private final MRPvp plugin;
    private final Map<UUID, Session> active = new ConcurrentHashMap<>();
    /** Players whose state is being saved or given back this very moment. */
    private final Set<UUID> changing = ConcurrentHashMap.newKeySet();
    /** Joined with an owed state that is handed out on the next tick. */
    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    /** Owed states found while logging in (async), handed out on join. */
    private final Map<UUID, List<Snapshot>> owedOnLogin = new ConcurrentHashMap<>();

    public StateKeeper(MRPvp plugin) {
        this.plugin = plugin;
    }

    private Database db() {
        return plugin.database();
    }

    /**
     * Saves the player's state and clears them for the arena (empty inventory, full health, {@code mode}).
     *
     * @return the stored snapshot, or null if the player is already inside or the database failed (then nothing
     *         about the player was changed)
     */
    public Snapshot enter(Player player, Snapshot.Purpose purpose, Long gameId, GameMode mode) {
        if (active.containsKey(player.getUniqueId())) {
            return null;
        }
        // From here on other plugins (MRtoolbar ...) see the player as busy and keep their hands off the inventory.
        changing.add(player.getUniqueId());
        try {
            Snapshot taken = PlayerState.capture(player, purpose, gameId, Snapshot.Phase.JOIN,
                    plugin.toolbar().belts(player));
            Snapshot stored;
            try {
                stored = db().call(c -> SnapshotStore.insert(c, taken));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save the state of " + player.getName()
                        + " - they stay where they are.", e);
                return null;
            }
            Map<Statistic, Integer> statistics = plugin.settings().protection().restoreStatistics() ? statistics(player) : null;
            active.put(player.getUniqueId(), new Session(stored, statistics));
            PlayerState.reset(player, mode);
            return stored;
        } finally {
            changing.remove(player.getUniqueId());
        }
    }

    /**
     * Whether MRpvp has the player's inventory right now: inside the arena world, or just saving / giving back.
     * Other plugins must not touch the inventory then ({@code MRpvpApi.inGame}).
     */
    public boolean busy(Player player) {
        UUID id = player.getUniqueId();
        // Owed after a crash/quit: between joining and getting it back (next tick) the inventory is the arena one -
        // MRtoolbar must not equip a belt tool into it, the whole inventory is replaced right after.
        return active.containsKey(id) || changing.contains(id) || owedOnLogin.containsKey(id) || waiting.contains(id);
    }

    /** Gives the player their state back and ends the session. False if there was nothing to give back. */
    public boolean leave(Player player) {
        changing.add(player.getUniqueId());
        try {
            Session session = active.remove(player.getUniqueId());
            if (session == null || !giveBack(player, session.owed())) {
                return false;
            }
            restoreStatistics(player, session.statistics());
            return true;
        } finally {
            changing.remove(player.getUniqueId());
        }
    }

    /** The plain (untyped) Minecraft statistics, e.g. damage dealt, jumps, walked centimetres. */
    private static Map<Statistic, Integer> statistics(Player player) {
        Map<Statistic, Integer> values = new EnumMap<>(Statistic.class);
        for (Statistic statistic : Statistic.values()) {
            if (statistic.getType() == Statistic.Type.UNTYPED && !KEEP.contains(statistic)) {
                values.put(statistic, player.getStatistic(statistic));
            }
        }
        return values;
    }

    private void restoreStatistics(Player player, Map<Statistic, Integer> values) {
        if (values == null) {
            return;
        }
        values.forEach((statistic, value) -> {
            try {
                player.setStatistic(statistic, value);
            } catch (IllegalArgumentException ignored) {
                // a statistic the server does not track
            }
        });
    }

    public Session session(Player player) {
        return active.get(player.getUniqueId());
    }

    public boolean inside(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    /**
     * Marks the snapshot as given back, then hands it out. If handing out fails the mark is taken back, so the
     * player gets it on the next login.
     */
    private boolean giveBack(Player player, Snapshot owed) {
        boolean outer = changing.add(player.getUniqueId());
        try {
            return handOut(player, owed);
        } finally {
            if (outer) {
                changing.remove(player.getUniqueId());
            }
        }
    }

    private boolean handOut(Player player, Snapshot owed) {
        boolean marked;
        try {
            marked = db().call(c -> SnapshotStore.markRestored(c, owed.id(), System.currentTimeMillis()));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not mark the state of " + player.getName()
                    + " as given back - they get it on their next login.", e);
            plugin.messages().send(player, "state-restore-later");
            return false;
        }
        if (!marked) {
            // Already given back (e.g. by an admin) - giving it again would duplicate items.
            plugin.getLogger().warning("State " + owed.id() + " of " + player.getName() + " was already given back.");
            return false;
        }
        boolean applied;
        try {
            applied = PlayerState.apply(player, owed, ArenaWorld.fallback(), plugin.getLogger());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Giving back the state of " + player.getName() + " failed.", e);
            applied = false;
        }
        if (!applied) {
            try {
                db().call(c -> {
                    SnapshotStore.unmarkRestored(c, owed.id());
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "State " + owed.id() + " of " + player.getName()
                        + " is marked as given back but was not - an admin has to give it back.", e);
            }
            plugin.messages().send(player, "state-restore-failed");
            return false;
        }
        plugin.getLogger().info("Gave " + player.getName() + " their state back (snapshot " + owed.id() + ", "
                + owed.purpose().name().toLowerCase(Locale.ROOT) + ").");
        return true;
    }

    /**
     * Admin action: hands out a state that is still owed to a player who is online and not in the arena (replaces
     * what they carry now).
     */
    public boolean giveBackNow(Player player, Snapshot owed) {
        return !inside(player) && giveBack(player, owed);
    }

    /** On shutdown/reload: everyone inside gets their things back now, while the database is still open. */
    public void giveBackAll() {
        for (Map.Entry<UUID, Session> entry : Map.copyOf(active).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            active.remove(entry.getKey());
            if (player != null && giveBack(player, entry.getValue().owed())) {
                restoreStatistics(player, entry.getValue().statistics());
            }
        }
    }

    /** A player who is offline is no longer inside (their state is given back on the next login). */
    public void forget(UUID player) {
        if (Bukkit.getPlayer(player) == null) {
            active.remove(player);
        }
    }

    public int activeCount() {
        return active.size();
    }

    // ------------------------------------------------------------------ events

    /** Looks up owed states while the player logs in (off the main thread, the login waits for it). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || db() == null) {
            return;
        }
        UUID uuid = event.getUniqueId();
        if (active.containsKey(uuid)) {
            // Comes back into a round that waited for them - the state stays saved, nothing is given back.
            return;
        }
        try {
            List<Snapshot> owed = db().call(c -> SnapshotStore.owed(c, uuid));
            if (!owed.isEmpty()) {
                owedOnLogin.put(uuid, owed);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check whether " + event.getName() + " is owed a state.", e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<Snapshot> owed = owedOnLogin.remove(player.getUniqueId());
        if (owed != null) {
            waiting.add(player.getUniqueId());
        }
        // One tick later: teleports during the join itself are not reliable.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                waiting.remove(player.getUniqueId());
                return;
            }
            if (owed != null) {
                try {
                    giveBackOnJoin(player, owed);
                } finally {
                    waiting.remove(player.getUniqueId());
                }
            } else if (plugin.arenaWorld().is(player.getWorld()) && !inside(player)) {
                // In the arena world without a reason (e.g. teleported there by hand): out to spawn.
                Location spawn = ArenaWorld.fallback();
                player.teleport(spawn);
                plugin.messages().send(player, "arena-world-left");
            }
        });
    }

    private void giveBackOnJoin(Player player, List<Snapshot> owed) {
        // Normally one. More would mean a bug: the oldest is the real state from before, the others were taken
        // inside the arena - they are only marked, never handed out.
        Snapshot first = owed.getFirst();
        if (owed.size() > 1) {
            plugin.getLogger().warning(player.getName() + " was owed " + owed.size()
                    + " states - giving back the oldest (" + first.id() + "), the others stay in the database.");
            for (Snapshot extra : owed.subList(1, owed.size())) {
                try {
                    db().call(c -> SnapshotStore.markRestored(c, extra.id(), System.currentTimeMillis()));
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not mark snapshot " + extra.id(), e);
                }
            }
        }
        if (giveBack(player, first)) {
            plugin.messages().send(player, "state-restored-join");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // The state stays owed in the database and is given back on the next login - unless a round waits for the
        // player to come back (then they stay "inside" until it is decided).
        if (plugin.games().waitingFor(event.getPlayer().getUniqueId())) {
            return;
        }
        active.remove(event.getPlayer().getUniqueId());
        owedOnLogin.remove(event.getPlayer().getUniqueId());
    }
}
