package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.MRPvp;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * The rules of a round. The most important one: <b>nobody really dies</b>. Deadly damage is cancelled and counts as
 * a defeat - no PlayerDeathEvent, so no death statistic, no kill statistic, no scoreboard criteria, no drops and no
 * death message, and other plugins never notice.
 */
public final class GameListener implements Listener {

    public static final String PERM_BYPASS_COMMANDS = "mrpvp.bypass.commands";

    private final MRPvp plugin;
    private final GameManager games;

    public GameListener(MRPvp plugin, GameManager games) {
        this.plugin = plugin;
        this.games = games;
    }

    // ------------------------------------------------------------------ damage

    /**
     * HIGHEST: runs after other plugins had their say (protection plugins cancelling it count), but the deadly hit
     * is still turned into a defeat before anyone could see a death.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Game game = games.gameOf(victim);
        if (game == null) {
            return;
        }
        if (game.state() != Game.State.RUNNING) {
            event.setCancelled(true);
            return;
        }
        Player attacker = attacker(event);
        if (attacker != null && game.fighter(attacker.getUniqueId()) == null) {
            // Someone who is not part of this round (should not be possible in the arena world).
            event.setCancelled(true);
            return;
        }
        var rules = game.kit().rules();
        if (!rules.damage()) {
            // Sumo, boxing: hits only push, the void (falling off) still decides.
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
                games.defeated(game, victim, Game.EndReason.BORDER);
                return;
            }
            if (attacker == null) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(0);
        }
        double damage = event.getFinalDamage();
        // Only what really hit counts: a mace smash for 40 on 6 health is 6, not 40 (statistics, combat log).
        record(game, victim, attacker, event, Math.min(damage, victim.getHealth()));

        if (rules.hitsToWin() > 0 && attacker != null
                && game.fighter(attacker.getUniqueId()).hits >= rules.hitsToWin()) {
            event.setCancelled(true);
            games.defeated(game, victim, Game.EndReason.KILL);
            return;
        }
        boolean deadly = damage >= victim.getHealth();
        if (deadly && !(plugin.settings().duel().totems() && holdsTotem(victim))) {
            event.setCancelled(true);
            Game.EndReason reason = event.getCause() == EntityDamageEvent.DamageCause.VOID
                    ? Game.EndReason.BORDER : Game.EndReason.KILL;
            games.defeated(game, victim, reason);
        }
    }

    private void record(Game game, Player victim, Player attacker, EntityDamageEvent event, double damage) {
        Fighter target = game.fighter(victim.getUniqueId());
        target.damageTaken += damage;
        long now = System.currentTimeMillis();
        target.lastHurtAt = now;
        String cause = event.getCause().name();
        if (attacker != null) {
            Fighter source = game.fighter(attacker.getUniqueId());
            source.damageDealt += damage;
            if (attacker != victim) {
                source.lastHitAt = now;
            }
            String weapon = null;
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Entity damager = byEntity.getDamager();
                if (damager instanceof Player) {
                    source.hits++;
                    if (byEntity.isCritical()) {
                        source.crits++;
                    }
                    weapon = attacker.getInventory().getItemInMainHand().getType().name();
                } else if (damager instanceof Projectile projectile) {
                    if (projectile instanceof AbstractArrow) {
                        source.arrowsHit++;
                    }
                    weapon = projectile.getType().name();
                }
            }
            if (damage > source.maxHit) {
                source.maxHit = damage;
                source.maxHitWeapon = weapon;
            }
            games.log(game, CombatLog.Type.HIT, attacker, victim, damage, cause, weapon, victim.getLocation());
            games.hit(game);
        } else {
            games.log(game, CombatLog.Type.DAMAGE, null, victim, damage, cause, null, victim.getLocation());
        }
    }

    /** The player who caused the damage - directly or with a projectile. */
    private static Player attacker(EntityDamageEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player player) {
            return player;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player player) {
                return player;
            }
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static boolean holdsTotem(Player player) {
        return isTotem(player.getInventory().getItemInMainHand()) || isTotem(player.getInventory().getItemInOffHand());
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }

    // ------------------------------------------------------------------ options: hunger, regeneration

    /** Hunger off: food never goes down (eating still works). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getFoodLevel() < player.getFoodLevel()) {
            Game game = games.gameOf(player);
            if (game != null && !game.match().options().hunger()) {
                event.setCancelled(true);
                player.setSaturation(Math.max(player.getSaturation(), 5f));
            }
        }
    }

    /** Regeneration off: a full belly heals nothing - golden apples and potions still do. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            Game game = games.gameOf(player);
            if (game != null && !game.match().options().regen()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            Game game = games.gameOf(player);
            if (game != null) {
                game.fighter(player.getUniqueId()).totems++;
                games.log(game, CombatLog.Type.TOTEM, player, null, null, null, null, player.getLocation());
            }
        }
    }

    /**
     * Safety net if something kills a player anyway (/kill, another plugin setting health to 0): nothing drops,
     * nothing is lost, no message - and the round is decided.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Game game = games.gameOf(player);
        if (game == null && !plugin.stateKeeper().inside(player)) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.deathMessage(null);
        if (game != null && game.state() != Game.State.ENDING) {
            games.defeated(game, player, Game.EndReason.KILL);
        }
    }

    // ------------------------------------------------------------------ projectiles

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        Game game = games.gameOf(player);
        if (game == null) {
            return;
        }
        Fighter fighter = game.fighter(player.getUniqueId());
        Projectile projectile = event.getEntity();
        if (projectile instanceof EnderPearl) {
            fighter.pearls++;
            games.log(game, CombatLog.Type.PEARL, player, null, null, null, null, player.getLocation());
        } else if (projectile instanceof AbstractArrow || projectile instanceof Trident) {
            if (projectile instanceof AbstractArrow && !(projectile instanceof Trident)) {
                fighter.arrowsShot++;
            }
            games.log(game, CombatLog.Type.SHOT, player, null, null, null, projectile.getType().name(),
                    player.getLocation());
        }
    }

    // ------------------------------------------------------------------ movement

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        Game game = games.gameOf(player);
        if (game == null) {
            return;
        }
        Location to = event.getTo();
        if (game.state() == Game.State.COUNTDOWN || game.state() == Game.State.PAUSED) {
            // Frozen, but may look around.
            Location from = event.getFrom().clone();
            from.setYaw(to.getYaw());
            from.setPitch(to.getPitch());
            event.setTo(from);
            return;
        }
        if (game.state() != Game.State.RUNNING) {
            return;
        }
        if (to.getY() < game.arena().minY()) {
            games.defeated(game, player, Game.EndReason.BORDER);
            // Not falling on through the void during the end pause.
            event.setTo(spawnOf(game, player));
            return;
        }
        if (!inside(game, to)) {
            Location from = event.getFrom();
            event.setTo(inside(game, from) ? from : spawnOf(game, player));
            game.fighter(player.getUniqueId()).borders++;
            games.log(game, CombatLog.Type.BORDER, player, null, null, null, null, to);
        }
    }

    /** Ender pearls and chorus fruit must not take anyone out; other worlds only through MRpvp itself. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Game game = games.gameOf(player);
        if (game == null) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld() != game.arena().spawn1().getWorld() || !inside(game, to)) {
            event.setCancelled(true);
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                    || event.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) {
                game.fighter(player.getUniqueId()).borders++;
                games.log(game, CombatLog.Type.BORDER, player, null, null, null, null, to);
            }
        }
    }

    private static boolean inside(Game game, Location at) {
        var region = game.arena().region();
        return at.getX() >= region.getMinX() && at.getX() <= region.getMaxX()
                && at.getZ() >= region.getMinZ() && at.getZ() <= region.getMaxZ()
                && at.getY() <= region.getMaxY();
    }

    private static Location spawnOf(Game game, Player player) {
        return game.fighter(player.getUniqueId()).side() == 1 ? game.arena().spawn1() : game.arena().spawn2();
    }

    // ------------------------------------------------------------------ other rules

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (games.gameOf(player) == null || player.hasPermission(PERM_BYPASS_COMMANDS)) {
            return;
        }
        String message = event.getMessage();
        String command = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!plugin.settings().duel().commandAllowed(command)) {
            event.setCancelled(true);
            plugin.messages().send(player, "command-blocked", tag("command", command));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (games.gameOf(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Game game = games.gameOf(event.getPlayer());
        if (game != null) {
            if (buildAllowed(game, event.getBlock().getLocation())) {
                // Nothing may drop: the arena is pasted again after the round anyway.
                event.setDropItems(false);
                event.setExpToDrop(0);
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Game game = games.gameOf(event.getPlayer());
        if (game != null && !buildAllowed(game, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Game game = games.gameOf(event.getPlayer());
        if (game != null && !buildAllowed(game, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) {
        Game game = games.gameOf(event.getPlayer());
        if (game != null && !buildAllowed(game, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Only in arenas that allow it, only during the fight, inside the arena and below its build height. */
    private static boolean buildAllowed(Game game, Location at) {
        var arena = game.arena();
        return arena.allowBuild() && game.state() == Game.State.RUNNING && inside(game, at)
                && at.getY() >= arena.region().getMinY() && at.getY() < arena.buildMaxY();
    }

    // ------------------------------------------------------------------ join / quit

    /** Before StateKeeper (MONITOR) forgets the session: the round is decided while the player is still known. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        games.quit(event.getPlayer());
    }

    /** Kit items never belong outside a round - after giving back (StateKeeper, next tick) they are removed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.reviews().joined(player);
        games.rejoined(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && games.gameOf(player) == null && !plugin.stateKeeper().inside(player)) {
                int removed = plugin.kits().sweep(player);
                if (removed > 0) {
                    plugin.getLogger().warning("Removed " + removed + " kit item stack(s) from " + player.getName()
                            + " - they must never leave a round.");
                }
            }
        }, 5L);
    }
}
