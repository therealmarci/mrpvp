package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.snapshot.Snapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Admins watching a round in spectator mode. Their state is saved like a player's (they could be anywhere with
 * anything), they cannot leave the arena world on their own and are taken out when the round is over.
 */
public final class Spectators implements Listener {

    public static final String PERM_SPECTATE = "mrpvp.admin.spectate";

    private final MRPvp plugin;
    /** spectator -> Game-ID */
    private final Map<UUID, Long> watching = new HashMap<>();

    public Spectators(MRPvp plugin) {
        this.plugin = plugin;
    }

    public boolean watching(Player player) {
        return watching.containsKey(player.getUniqueId());
    }

    public void watch(Player player, Game game) {
        if (watching(player)) {
            // Switch to another round without giving back and saving again.
            watching.put(player.getUniqueId(), game.id());
            player.teleport(view(game));
            return;
        }
        if (plugin.stateKeeper().inside(player) || plugin.games().inGame(player.getUniqueId())) {
            plugin.messages().send(player, "invite-error-busy-self");
            return;
        }
        if (plugin.stateKeeper().enter(player, Snapshot.Purpose.SPECTATE, game.id(), GameMode.SPECTATOR) == null) {
            plugin.messages().send(player, "state-save-failed");
            return;
        }
        watching.put(player.getUniqueId(), game.id());
        player.teleport(view(game));
        plugin.messages().send(player, "spectate-started", tag("id", game.id()), tag("one", game.one().name()),
                tag("two", game.two().name()));
    }

    /** Like {@link #watch}, but right next to one of the fighters (null = the usual view from above). */
    public void watchAt(Player player, Game game, Fighter at) {
        watch(player, game);
        Player target = at == null ? null : Bukkit.getPlayer(at.uuid());
        if (target != null && Long.valueOf(game.id()).equals(watching.get(player.getUniqueId()))) {
            player.teleport(target.getLocation());
        }
    }

    /** Above the middle between both spawns, looking down a bit. */
    private static Location view(Game game) {
        Location a = game.arena().spawn1();
        Location b = game.arena().spawn2();
        Location view = a.clone().add(b).multiply(0.5).add(0, 8, 0);
        view.setDirection(b.toVector().subtract(a.toVector()));
        view.setPitch(35);
        return view;
    }

    public void stop(Player player) {
        if (watching.remove(player.getUniqueId()) != null) {
            plugin.games().hideBar(player.getUniqueId());
            plugin.stateKeeper().leave(player);
        }
    }

    /** Spectators of a round who are online. */
    java.util.List<Player> watchers(Game game) {
        java.util.List<Player> list = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Long> entry : watching.entrySet()) {
            Player player = entry.getValue() == game.id() ? plugin.getServer().getPlayer(entry.getKey()) : null;
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    /** The next round of a match started (in another arena): watchers come along. */
    void follow(Game previous, Game next) {
        for (Map.Entry<UUID, Long> entry : Map.copyOf(watching).entrySet()) {
            if (entry.getValue() == previous.id()) {
                watching.put(entry.getKey(), next.id());
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null) {
                    player.teleport(view(next));
                }
            }
        }
    }

    /** A round closed: everyone who watched it gets out. */
    void gameClosed(Game game) {
        for (Map.Entry<UUID, Long> entry : Map.copyOf(watching).entrySet()) {
            if (entry.getValue() == game.id()) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null) {
                    stop(player);
                    plugin.messages().send(player, "spectate-ended", tag("id", game.id()));
                } else {
                    watching.remove(entry.getKey());
                }
            }
        }
    }

    /** Spectator teleport (clicking a player) is fine, other worlds are not - only through "stop watching". */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (watching(player) && !plugin.arenaWorld().is(event.getTo().getWorld())) {
            event.setCancelled(true);
            plugin.messages().send(player, "spectate-leave-first");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (watching(event.getPlayer())) {
            plugin.games().hideBar(event.getPlayer().getUniqueId());
        }
        watching.remove(event.getPlayer().getUniqueId());
    }

    public void closeAll() {
        watching.clear();
    }
}
