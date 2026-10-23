package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Messages;
import at.marcelr.mrpvp.Settings;
import at.marcelr.mrpvp.arena.ArenaInstance;
import at.marcelr.mrpvp.arena.ArenaProvider;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.snapshot.PlayerState;
import at.marcelr.mrpvp.snapshot.Snapshot;
import at.marcelr.mrpvp.snapshot.SnapshotStore;
import at.marcelr.mrpvp.stats.StatsRules;
import at.marcelr.mrpvp.stats.StatsStore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Invitations and rounds: start (save both states, arena, kit), countdown, fight, end (result, numbers, combat
 * log into the database) and giving everything back. Main thread only.
 */
public final class GameManager {

    public static final String PERM_USE = "mrpvp.use";
    public static final String PERM_BYPASS_COOLDOWN = "mrpvp.bypass.cooldown";

    private static final int POSITION_TICKS = 10;
    private static final int FLUSH_SECONDS = 5;

    private final MRPvp plugin;
    private final ArenaProvider arenas;
    private final InviteManager invites = new InviteManager();
    private final DuelBar bar = new DuelBar();
    private final Map<Long, Game> games = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    /** When each running match started (match id -> epoch ms) */
    private final Map<Long, Long> matchStart = new HashMap<>();
    private BukkitTask secondTask;
    private BukkitTask positionTask;
    private int seconds;

    public GameManager(MRPvp plugin, ArenaProvider arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    public void start() {
        stop();
        try {
            int aborted = plugin.database().call(c -> GameStore.abortUnfinished(c, System.currentTimeMillis()));
            if (aborted > 0) {
                plugin.getLogger().warning(aborted + " round(s) were still running when the server stopped last time"
                        + " - marked as aborted (crash). The players get their things back when they join.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not check for unfinished rounds", e);
        }
        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, this::everySecond, 20L, 20L);
        positionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::samplePositions, POSITION_TICKS, POSITION_TICKS);
    }

    public void stop() {
        if (secondTask != null) {
            secondTask.cancel();
            secondTask = null;
        }
        if (positionTask != null) {
            positionTask.cancel();
            positionTask = null;
        }
    }

    private Settings.DuelSettings settings() {
        return plugin.settings().duel();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------ state

    /** The round the player is in (any state), or null. */
    public Game gameOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public boolean inGame(UUID player) {
        return byPlayer.containsKey(player);
    }

    public List<Game> games() {
        return List.copyOf(games.values());
    }

    public InviteManager invites() {
        return invites;
    }

    /** Whether the player could start a round right now (not in one, not in the arena world, alive). */
    public boolean available(Player player) {
        return !inGame(player.getUniqueId()) && !plugin.stateKeeper().inside(player)
                && !plugin.arenaWorld().is(player.getWorld()) && !player.isDead()
                // MRadmin's admin mode has its own (creative) inventory - no duels from there.
                && !plugin.admin().adminMode(player);
    }

    /** A hit in the round: open web pages show the new health (at most twice a second). */
    void hit(Game game) {
        // The damage is applied after the event - show the new health a tick later.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (game.state() == Game.State.RUNNING) {
                showBars(game);
            }
        });
        plugin.admin().webEvent("game", String.valueOf(game.id()), true);
    }

    // ------------------------------------------------------------------ invitations

    /** Sends an invitation (texts go to both). Returns whether it was sent. */
    public boolean invite(Player from, Player to, Kit kit, String arena, DuelOptions options) {
        Messages m = messages();
        if (!available(from)) {
            m.send(from, "invite-error-busy-self");
            return false;
        }
        if (bannedMessage(from)) {
            return false;
        }
        if (plugin.bans().get(to.getUniqueId()) != null) {
            m.send(from, "invite-error-banned", tag("player", to.getName()));
            return false;
        }
        if (!to.isOnline() || !to.hasPermission(PERM_USE)) {
            m.send(from, "invite-error-offline", tag("player", to.getName()));
            return false;
        }
        if (!available(to)) {
            m.send(from, "invite-error-busy", tag("player", to.getName()));
            return false;
        }
        if (!kit.allowed(from)) {
            m.send(from, "no-permission");
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldown = from.hasPermission(PERM_BYPASS_COOLDOWN) ? 0 : settings().cooldownSeconds() * 1000L;
        Object created = invites.create(from.getUniqueId(), from.getName(), to.getUniqueId(), to.getName(), kit.id(),
                arena, options, now, settings().inviteSeconds() * 1000L, cooldown);
        if (created instanceof InviteManager.Refusal refusal) {
            m.send(from, "invite-error-" + refusal.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                    tag("player", to.getName()), tag("seconds", invites.cooldownLeft(from.getUniqueId(), now, cooldown)));
            return false;
        }
        Invite invite = (Invite) created;
        savePrefs(from.getUniqueId(), new GameStore.Prefs(kit.id(), arena, options));
        m.send(from, "invite-sent", tag("player", to.getName()), tag("kit", m.mini(kit.name())), tag("arena", arenaName(arena)),
                tag("options", optionsText(options.with(kit))),
                tag("seconds", settings().inviteSeconds()));
        m.run(to, () -> {
            // Answer right in the chat - the menu ("details") is only there for a closer look.
            Component accept = m.get("invite-accept-button")
                    .clickEvent(ClickEvent.runCommand("/duel accept " + invite.id()))
                    .hoverEvent(HoverEvent.showText(m.get("invite-accept-hover")));
            Component decline = m.get("invite-decline-button")
                    .clickEvent(ClickEvent.runCommand("/duel decline " + invite.id()))
                    .hoverEvent(HoverEvent.showText(m.get("invite-decline-hover")));
            Component view = m.get("invite-view-button")
                    .clickEvent(ClickEvent.runCommand("/duel invite " + invite.id()))
                    .hoverEvent(HoverEvent.showText(m.get("invite-view-hover")));
            m.send(to, "invite-received", tag("player", from.getName()), tag("kit", m.mini(kit.name())), tag("arena", arenaName(arena)),
                    tag("options", optionsText(options.with(kit))),
                    tag("seconds", settings().inviteSeconds()), tag("view", view), tag("accept", accept),
                    tag("decline", decline));
        });
        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        return true;
    }


    // ------------------------------------------------------------------ remembered invitation settings

    /** The last invitation settings of a player (loaded on the storage thread), or null. */
    public java.util.concurrent.CompletableFuture<GameStore.Prefs> prefs(UUID player) {
        return plugin.database().submit(c -> GameStore.prefs(c, player));
    }

    private void savePrefs(UUID player, GameStore.Prefs prefs) {
        plugin.database().submit(c -> {
            GameStore.savePrefs(c, player, prefs);
            return null;
        });
    }
    /** Tells a banned player so (with end and reason) and returns true; false if not banned. */
    public boolean bannedMessage(Player player) {
        Bans.Ban ban = plugin.bans().get(player.getUniqueId());
        if (ban == null) {
            return false;
        }
        messages().send(player, ban.until() == null ? "banned-self-permanent" : "banned-self",
                tag("until", plugin.messages().dateTime(ban.until() == null ? 0 : ban.until())),
                tag("reason", ban.reason() == null || ban.reason().isBlank() ? "-" : ban.reason()));
        return true;
    }

    /** "first to 3 · 10 min · no regeneration" - the options in one line. */
    public Component optionsText(DuelOptions o) {
        Messages m = messages();
        return m.get(o.wins() <= 1 ? "options-line-single" : "options-line", tag("wins", o.wins()), tag("minutes", o.minutes()),
                tag("hunger", m.get(o.hunger() ? "options-hunger-on" : "options-hunger-off")),
                tag("regen", m.get(o.regen() ? "options-regen-on" : "options-regen-off")));
    }

    /** Display name of an arena template for texts ("random" for null). */
    public net.kyori.adventure.text.Component arenaName(String template) {
        var t = template == null ? null : plugin.templates().get(template);
        return t == null ? messages().get("arena-random") : messages().mini(t.name());
    }

    public void decline(Player player, Invite invite) {
        if (invites.remove(invite.id()) == null) {
            return;
        }
        messages().send(player, "invite-declined-self", tag("player", invite.fromName()));
        Player from = Bukkit.getPlayer(invite.from());
        if (from != null) {
            messages().send(from, "invite-declined", tag("player", player.getName()));
        }
    }

    /** Accepts and starts the round right away. */
    public void accept(Player player, Invite invite) {
        Messages m = messages();
        if (invites.get(invite.id()) == null || invite.expired(System.currentTimeMillis())) {
            m.send(player, "invite-expired-self");
            return;
        }
        Player from = Bukkit.getPlayer(invite.from());
        if (from == null) {
            invites.remove(invite.id());
            m.send(player, "invite-error-offline", tag("player", invite.fromName()));
            return;
        }
        if (!available(player)) {
            m.send(player, "invite-error-busy-self");
            return;
        }
        if (bannedMessage(player)) {
            return;
        }
        if (!available(from) || plugin.bans().get(from.getUniqueId()) != null) {
            m.send(player, "invite-error-busy", tag("player", from.getName()));
            return;
        }
        Kit kit = plugin.kits().get(invite.kit());
        if (kit == null) {
            invites.remove(invite.id());
            m.send(player, "kit-missing");
            return;
        }
        invites.remove(invite.id());
        begin(from, player, kit, invite.arena(), invite.options().with(kit));
    }

    // ------------------------------------------------------------------ start

    private void begin(Player p1, Player p2, Kit kit, String template, DuelOptions options) {
        Messages m = messages();
        ArenaInstance arena = arenas.acquire(template, kit.id());
        if (arena == null) {
            m.send(p1, "no-arena-free");
            m.send(p2, "no-arena-free");
            return;
        }
        long id;
        try {
            id = plugin.database().call(c -> GameStore.insert(c, arena.name(), arena.version(), arena.slot(), kit.id(),
                    p1.getUniqueId(), System.currentTimeMillis(), plugin.getPluginMeta().getVersion(), null, 1));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create a round", e);
            arenas.release(arena);
            m.send(p1, "state-save-failed");
            m.send(p2, "state-save-failed");
            return;
        }
        // Adventure mode keeps blocks as they are; arenas that allow building need survival.
        GameMode mode = arena.allowBuild() ? GameMode.SURVIVAL : GameMode.ADVENTURE;
        // Both states must be safe before anything changes; if the second fails, the first gets everything back.
        Snapshot first = plugin.stateKeeper().enter(p1, Snapshot.Purpose.GAME, id, mode);
        Snapshot second = first == null ? null
                : plugin.stateKeeper().enter(p2, Snapshot.Purpose.GAME, id, mode);
        if (second == null) {
            if (first != null) {
                plugin.stateKeeper().leave(p1);
            }
            arenas.release(arena);
            plugin.database().submit(c -> {
                GameStore.finish(c, id, System.currentTimeMillis(), Game.Result.ABORTED, Game.EndReason.ERROR,
                        null, null);
                return null;
            });
            m.send(p1, "state-save-failed");
            m.send(p2, "state-save-failed");
            return;
        }
        invites.removeAll(p1.getUniqueId()).forEach(this::dropped);
        invites.removeAll(p2.getUniqueId()).forEach(this::dropped);

        Match match = new Match(id, kit, template, options, p1.getUniqueId(), p2.getUniqueId());
        matchStart.put(id, System.currentTimeMillis());
        Game game = new Game(id, match, kit, arena, new Fighter(p1.getUniqueId(), p1.getName(), 1),
                new Fighter(p2.getUniqueId(), p2.getName(), 2), settings().countdown());
        // No chat while playing - round, score and time are in the boss bar, one summary comes at the end.
        startRound(game, p1, p2);
    }

    /** Puts both into the round's arena with a fresh kit and starts the countdown (first round and every next one). */
    private void startRound(Game game, Player p1, Player p2) {
        games.put(game.id(), game);
        byPlayer.put(p1.getUniqueId(), game);
        byPlayer.put(p2.getUniqueId(), game);
        prepare(p1, game.arena().spawn1(), game.kit(), game.id());
        prepare(p2, game.arena().spawn2(), game.kit(), game.id());
        plugin.admin().webEvent("game", String.valueOf(game.id()), false);
        plugin.getLogger().info("Round #" + game.id() + " started: " + p1.getName() + " vs " + p2.getName() + " (kit "
                + game.kit().id() + ", arena " + game.arena().name() + " slot " + game.arena().slot()
                + (game.match().single() ? "" : ", match #" + game.match().id() + " round " + game.match().round()) + ").");
        if (game.countdown() <= 0) {
            fight(game);
        } else {
            game.countdownShown();
            showCountdown(game);
        }
    }

    private void prepare(Player player, Location spawn, Kit kit, long id) {
        // Nothing of the last round may carry over: knockback, a fall, burning.
        player.setVelocity(new org.bukkit.util.Vector());
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.teleport(spawn);
        player.setVelocity(new org.bukkit.util.Vector());
        plugin.kits().give(player, kit, id);
        player.setHealth(PlayerState.maxHealth(player));
    }

    private void showCountdown(Game game) {
        Match match = game.match();
        forEachOnline(game, player -> {
            Component subtitle = match.single() ? messages().get("countdown-subtitle")
                    : messages().get("countdown-subtitle-match", tag("round", match.round()),
                    tag("score", score(game, player.getUniqueId())));
            player.showTitle(Title.title(messages().get("countdown-title", tag("seconds", game.countdown())),
                    subtitle, Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        });
        showBars(game);
    }

    private void fight(Game game) {
        game.startFight();
        forEachOnline(game, player -> {
            player.showTitle(Title.title(messages().get("fight-title"), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(300))));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        });
        showBars(game);
    }

    // ------------------------------------------------------------------ timers

    private void everySecond() {
        long now = System.currentTimeMillis();
        invites.expire(now, this::expired);
        seconds++;
        for (Game game : List.copyOf(games.values())) {
            switch (game.state()) {
                case COUNTDOWN -> {
                    int before = game.countdown();
                    int left = game.tickCountdown();
                    if (left <= 0) {
                        fight(game);
                    } else if (left != before) {
                        showCountdown(game);
                    }
                }
                case RUNNING -> {
                    if (game.elapsed() >= game.match().options().minutes() * 60_000L) {
                        end(game, Game.Result.DRAW, Game.EndReason.TIMEOUT, null, null);
                    } else {
                        showBars(game);
                    }
                }
                case PAUSED -> {
                    if (now >= game.waitUntil()) {
                        notBack(game);
                    } else {
                        showBars(game);
                    }
                }
                case ENDING -> {
                }
            }
            if (seconds % FLUSH_SECONDS == 0) {
                flush(game);
            }
        }
    }

    // ------------------------------------------------------------------ boss bar

    /** Countdown or fight: round, score, opponent's health, time left (and hits for kits like boxing). */
    private void showBars(Game game) {
        Messages m = messages();
        Match match = game.match();
        if (game.state() == Game.State.PAUSED) {
            long left = Math.max(0, game.waitUntil() - System.currentTimeMillis());
            Fighter gone = game.fighter(game.waitingFor());
            float progress = left / (settings().reconnectSeconds() * 1000f);
            List<Player> viewers = new ArrayList<>(plugin.spectators().watchers(game));
            for (Fighter f : List.of(game.one(), game.two())) {
                Player p = Bukkit.getPlayer(f.uuid());
                if (p != null) {
                    viewers.add(p);
                }
            }
            for (Player viewer : viewers) {
                m.run(viewer, () -> bar.show(viewer, m.get("bar-paused", tag("player", gone == null ? "?" : gone.name()),
                        tag("seconds", (left + 999) / 1000)), progress, BossBar.Color.PURPLE));
            }
            return;
        }
        long limit = match.options().minutes() * 60_000L;
        boolean countdown = game.state() == Game.State.COUNTDOWN;
        long left = Math.max(0, limit - game.elapsed());
        float progress = countdown ? game.countdown() / (float) Math.max(1, settings().countdown())
                : left / (float) limit;
        BossBar.Color color = countdown ? BossBar.Color.WHITE
                : left <= 15_000 ? BossBar.Color.RED : left <= 60_000 ? BossBar.Color.YELLOW : BossBar.Color.BLUE;
        for (Fighter f : List.of(game.one(), game.two())) {
            Player player = Bukkit.getPlayer(f.uuid());
            if (player == null) {
                continue;
            }
            Fighter other = game.opponent(f.uuid());
            Player opponent = Bukkit.getPlayer(other.uuid());
            m.run(player, () -> {
                Component round = match.single() ? Component.empty() : m.get("bar-part-round",
                        tag("round", match.round()), tag("score", score(game, f.uuid())));
                Component text;
                if (countdown) {
                    text = m.get("bar-countdown", tag("round", round), tag("kit", m.mini(game.kit().name())),
                            tag("opponent", other.name()), tag("seconds", game.countdown()));
                } else {
                    int goal = game.kit().rules().hitsToWin();
                    Component hits = goal <= 0 ? Component.empty() : m.get("bar-part-hits",
                            tag("mine", f.hits()), tag("theirs", other.hits()), tag("goal", goal));
                    text = m.get("bar-fight", tag("round", round), tag("opponent", other.name()),
                            tag("health", hearts(opponent)), tag("time", clock(left)), tag("hits", hits));
                }
                bar.show(player, text, progress, color);
            });
        }
        for (Player watcher : plugin.spectators().watchers(game)) {
            m.run(watcher, () -> {
                Component round = match.single() ? Component.empty() : m.get("bar-part-round",
                        tag("round", match.round()), tag("score", match.wins(1) + " : " + match.wins(2)));
                bar.show(watcher, m.get("bar-spectate", tag("round", round), tag("one", game.one().name()),
                        tag("two", game.two().name()), tag("h1", hearts(Bukkit.getPlayer(game.one().uuid()))),
                        tag("h2", hearts(Bukkit.getPlayer(game.two().uuid()))), tag("time", clock(left))),
                        progress, color);
            });
        }
    }

    /** After a round: its result, the score and what comes next. */
    private void showEndBars(Game game, Game.Result result, Fighter winner, boolean over) {
        Messages m = messages();
        Match match = game.match();
        for (Fighter f : List.of(game.one(), game.two())) {
            Player player = Bukkit.getPlayer(f.uuid());
            if (player == null) {
                continue;
            }
            String outcome = result == Game.Result.ABORTED ? "aborted" : result != Game.Result.WIN ? "draw"
                    : winner == f ? "won" : "lost";
            BossBar.Color color = switch (outcome) {
                case "won" -> BossBar.Color.GREEN;
                case "lost" -> BossBar.Color.RED;
                case "draw" -> BossBar.Color.YELLOW;
                default -> BossBar.Color.WHITE;
            };
            m.run(player, () -> {
                Component score = match.single() ? Component.empty()
                        : m.get("bar-part-score", tag("score", score(game, f.uuid())));
                bar.show(player, m.get(over ? "bar-ended" : "bar-next", tag("result", m.get("bar-result-" + outcome)),
                        tag("score", score)), 1f, color);
            });
        }
    }

    /** "7.5" hearts with absorption, "-" for someone offline. */
    private static String hearts(Player player) {
        if (player == null) {
            return "-";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", (player.getHealth() + player.getAbsorptionAmount()) / 2);
    }

    void hideBar(UUID player) {
        bar.hide(player);
    }

    private void samplePositions() {
        for (Game game : games.values()) {
            if (game.state() != Game.State.RUNNING) {
                continue;
            }
            long t = game.elapsed();
            for (Fighter f : List.of(game.one(), game.two())) {
                Player player = Bukkit.getPlayer(f.uuid());
                if (player != null) {
                    Location at = player.getLocation();
                    game.log().add(new CombatLog.Position(t, f.side(), at.getX(), at.getY(), at.getZ(), at.getYaw(),
                            player.getHealth()));
                }
            }
        }
    }

    private void flush(Game game) {
        CombatLog.Batch batch = game.log().drain();
        if (!batch.isEmpty()) {
            long id = game.id();
            plugin.database().submit(c -> {
                GameStore.write(c, id, batch);
                return null;
            });
        }
    }

    private void expired(Invite invite) {
        Player from = Bukkit.getPlayer(invite.from());
        if (from != null) {
            messages().send(from, "invite-expired", tag("player", invite.toName()));
        }
    }

    /** An open invitation that ended because a round started. */
    private void dropped(Invite invite) {
        Player from = Bukkit.getPlayer(invite.from());
        if (from != null && !inGame(invite.from())) {
            messages().send(from, "invite-cancelled", tag("player", invite.toName()));
        }
    }

    // ------------------------------------------------------------------ events from GameListener

    /** Records something in the combat log of the player's round. */
    void log(Game game, CombatLog.Type type, Player actor, Player target, Double damage, String cause, String weapon,
             Location at) {
        game.log().add(new CombatLog.Event(game.elapsed(), type, actor == null ? null : actor.getUniqueId(),
                target == null ? null : target.getUniqueId(), damage, cause, weapon, at.getX(), at.getY(), at.getZ()));
    }

    /** The player took deadly damage (or fell out): the other one wins. */
    void defeated(Game game, Player loser, Game.EndReason reason) {
        Fighter winner = game.opponent(loser.getUniqueId());
        end(game, Game.Result.WIN, reason, winner, null);
    }

    /** Nothing has happened yet: the countdown before the very first fight of the match. */
    private static boolean beforeStart(Game game) {
        return game.match().round() == 1 && !game.fought();
    }

    /**
     * /duel leave: giving up loses the whole match - at any time, also in a countdown or between two rounds. Only
     * before the first round has begun it is simply called off.
     */
    public void surrender(Player player) {
        Game game = gameOf(player);
        if (game == null || (game.state() == Game.State.ENDING && game.last())) {
            messages().send(player, "not-in-game");
            return;
        }
        if (game.state() == Game.State.ENDING) {
            // Between two rounds: the next one does not start, the other one has won the match.
            game.match().forfeit(player.getUniqueId());
            messages().send(player, "surrender-between");
        } else if (game.state() == Game.State.COUNTDOWN && beforeStart(game)) {
            end(game, Game.Result.ABORTED, Game.EndReason.SURRENDER, null, null);
        } else {
            defeated(game, player, Game.EndReason.SURRENDER);
        }
    }

    /**
     * Left the server (also a crash of the game): like giving up - unless the round may wait for them
     * (duel.reconnect-seconds, once per match and player, only while the opponent is still there).
     */
    void quit(Player player) {
        UUID id = player.getUniqueId();
        invites.removeAll(id).forEach(invite -> {
            Player other = Bukkit.getPlayer(invite.from().equals(id) ? invite.to() : invite.from());
            if (other != null) {
                messages().send(other, "invite-cancelled", tag("player", player.getName()));
            }
        });
        Game game = gameOf(player);
        if (game == null) {
            return;
        }
        Match match = game.match();
        int grace = settings().reconnectSeconds();
        boolean mayWait = grace > 0 && match.mayReconnect(id) && Bukkit.getPlayer(game.opponent(id).uuid()) != null;
        long until = System.currentTimeMillis() + grace * 1000L;
        switch (game.state()) {
            case COUNTDOWN, RUNNING -> {
                if (game.state() == Game.State.COUNTDOWN && beforeStart(game)) {
                    end(game, Game.Result.ABORTED, Game.EndReason.QUIT, null, null);
                } else if (mayWait) {
                    match.usedReconnect(id);
                    game.pause(id, until);
                    paused(game, player.getName(), grace);
                    // Stays in the round (and in the arena) - the state is not given back while we wait.
                    return;
                } else {
                    defeated(game, player, Game.EndReason.QUIT);
                }
            }
            // The one we waited for is still gone and now the other one leaves too: nobody wins.
            case PAUSED -> end(game, Game.Result.ABORTED, Game.EndReason.QUIT, null, null);
            case ENDING -> {
                if (!game.last()) {
                    if (mayWait) {
                        match.usedReconnect(id);
                        game.waitBetweenRounds(id, until);
                        plugin.getLogger().info(player.getName() + " lost the connection between two rounds of match #"
                                + match.id() + " - the next round waits up to " + grace + " s.");
                        return;
                    }
                    match.forfeit(id);
                }
            }
        }
        // Out of the round right away: if they come back during the end pause, giving their things back (and the
        // teleport out of the arena) must not be blocked by the round.
        byPlayer.remove(id, game);
    }

    /** Whether a round waits for this player to come back (they keep their state in the arena meanwhile). */
    public boolean waitingFor(UUID player) {
        Game game = byPlayer.get(player);
        return game != null && player.equals(game.waitingFor());
    }

    /** The round stops until the player is back; the opponent is frozen and cannot be hurt. */
    private void paused(Game game, String name, int seconds) {
        plugin.getLogger().info("Round #" + game.id() + " paused: " + name + " lost the connection - waiting up to "
                + seconds + " s.");
        forEachOnline(game, p -> p.showTitle(Title.title(messages().get("reconnect-title"),
                messages().get("reconnect-subtitle", tag("player", name), tag("seconds", seconds)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500)))));
        showBars(game);
        plugin.admin().webEvent("game", String.valueOf(game.id()), false);
    }

    /** A player the round waited for joined again: a fresh countdown, then it goes on. */
    public void rejoined(Player player) {
        Game game = byPlayer.get(player.getUniqueId());
        if (game == null || !player.getUniqueId().equals(game.waitingFor())) {
            return;
        }
        plugin.getLogger().info(player.getName() + " is back in round #" + game.id() + ".");
        if (game.state() == Game.State.PAUSED) {
            game.resume(Math.max(3, settings().countdown()));
            showCountdown(game);
            plugin.admin().webEvent("game", String.valueOf(game.id()), false);
        } else {
            game.stopWaiting();
        }
    }

    /** Waited long enough: the one who is gone lost. */
    private void notBack(Game game) {
        UUID gone = game.waitingFor();
        Fighter winner = game.opponent(gone);
        plugin.getLogger().info("Round #" + game.id() + ": " + game.fighter(gone).name() + " did not come back in time.");
        end(game, Game.Result.WIN, Game.EndReason.QUIT, winner, null);
        byPlayer.remove(gone, game);
    }

    // ------------------------------------------------------------------ end

    /**
     * Decides the round. Everything is written at once; the players get their things back after the end delay.
     *
     * @param winner    null for a draw / abort
     * @param stoppedBy admin name for {@link Game.EndReason#ADMIN}
     */
    public void end(Game game, Game.Result result, Game.EndReason reason, Fighter winner, String stoppedBy) {
        if (game.state() == Game.State.ENDING) {
            return;
        }
        game.state(Game.State.ENDING);
        List<Snapshot> endStates = new ArrayList<>();
        for (Fighter f : List.of(game.one(), game.two())) {
            Player player = Bukkit.getPlayer(f.uuid());
            if (player != null) {
                f.healthEnd = player.getHealth();
                endStates.add(PlayerState.capture(player, Snapshot.Purpose.GAME, game.id(), Snapshot.Phase.END, null));
                if (winner != null && winner != f) {
                    log(game, CombatLog.Type.END, null, player, null, reason.name(), null, player.getLocation());
                }
            }
        }
        CombatLog.Batch batch = game.log().drain();
        long id = game.id();
        long endedAt = System.currentTimeMillis();
        UUID winnerId = winner == null ? null : winner.uuid();
        plugin.getLogger().info("Round #" + id + " ended: " + result + " (" + reason + ")"
                + (winner == null ? "" : ", winner " + winner.name()) + ".");
        plugin.admin().webEvent("game", String.valueOf(id), false);

        // The match: count the round, then decide whether another one follows.
        Match match = game.match();
        match.addRound(game.one(), game.two());
        if (result == Game.Result.WIN && winner != null) {
            match.won(winner.uuid());
        }
        boolean broken = result == Game.Result.ABORTED || reason == Game.EndReason.QUIT
                || reason == Game.EndReason.SURRENDER || reason == Game.EndReason.ADMIN
                || reason == Game.EndReason.SHUTDOWN || reason == Game.EndReason.ERROR;
        boolean over = broken || match.single() || match.decided() || match.roundsUsedUp()
                || Bukkit.getPlayer(game.one().uuid()) == null || Bukkit.getPlayer(game.two().uuid()) == null;
        // Leaving or giving up loses the whole match; an admin stop or a shutdown ends it without winner.
        UUID matchWinner = !over ? null
                : (reason == Game.EndReason.QUIT || reason == Game.EndReason.SURRENDER) && winner != null ? winner.uuid()
                : broken ? null : match.leader() == 1 ? game.one().uuid() : match.leader() == 2 ? game.two().uuid() : null;

        // Conspicuous rounds do not count yet - an admin decides (review.*).
        Set<String> flags = plugin.monitor().check(game, reason, winner);
        StatsStore.Round round = new StatsStore.Round(id, game.kit().id(), endedAt, game.elapsed(), result.name(),
                reason.name(), winnerId, side(game.one()), side(game.two()),
                game.one().hits() + game.two().hits() + game.one().arrowsHit() + game.two().arrowsHit());
        StatsRules rules = plugin.settings().stats();
        Set<String> hold = plugin.settings().review().hold();
        plugin.database().submit(c -> {
            GameStore.write(c, id, batch);
            for (Snapshot s : endStates) {
                SnapshotStore.insert(c, s);
            }
            GameStore.finish(c, id, endedAt, result, reason, winnerId, stoppedBy, game.one(), game.two());
            return StatsStore.record(c, round, rules, flags, hold);
        }).whenComplete((recorded, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not store the statistics of round #" + id, error);
            }
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (recorded != null && recorded.verdict() == StatsStore.Verdict.REVIEW) {
                    match.held();
                    plugin.reviews().held(id, game.one().name(), game.two().name(), recorded.flags());
                }
                // The only chat message of a duel: at the very end, once it is known whether it counts.
                if (over) {
                    summary(game, result, reason, winner, matchWinner);
                }
            });
        });

        game.last(over);
        titles(game, result, reason, winner);
        showEndBars(game, result, winner, over);
        if (over) {
            finishMatch(game, matchWinner, reason.name());
        }

        int delay = reason == Game.EndReason.SHUTDOWN ? 0 : settings().endDelaySeconds();
        Runnable next = over ? () -> close(game) : () -> nextRound(game);
        if (delay <= 0) {
            next.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, next, delay * 20L);
        }
    }

    /** "2 : 1" from the player's view. */
    private static String score(Game game, UUID viewer) {
        Match match = game.match();
        boolean first = game.one().uuid().equals(viewer);
        return first ? match.wins(1) + " : " + match.wins(2) : match.wins(2) + " : " + match.wins(1);
    }

    /** Stores the match result. */
    private void finishMatch(Game game, UUID winner, String reason) {
        Match match = game.match();
        Long started = matchStart.remove(match.id());
        long startedAt = started == null ? System.currentTimeMillis() : started;
        long endedAt = System.currentTimeMillis();
        int rounds = match.round();
        UUID one = game.one().uuid();
        UUID two = game.two().uuid();
        plugin.database().submit(c -> {
            GameStore.finishMatch(c, match, rounds, one, two, winner, reason, startedAt, endedAt);
            return null;
        });
        if (!match.single()) {
            plugin.getLogger().info("Match #" + match.id() + " ended " + match.wins(1) + ":" + match.wins(2) + " after "
                    + rounds + " rounds" + (winner == null ? "" : ", winner " + (winner.equals(one) ? game.one().name()
                    : game.two().name())) + ".");
        }
    }

    /**
     * The next round of a match: a fresh arena (the old one is pasted again), a fresh kit, a new Game-ID. Both stay
     * in the arena world - their own things come back only when the match is over.
     */
    private void nextRound(Game previous) {
        games.remove(previous.id());
        Match match = previous.match();
        // Gave up during the pause: the other one has won the match.
        UUID forfeit = match.forfeit();
        if (forfeit != null) {
            Fighter winner = previous.opponent(forfeit);
            finishMatch(previous, winner.uuid(), Game.EndReason.SURRENDER.name());
            summary(previous, Game.Result.WIN, Game.EndReason.SURRENDER, winner, winner.uuid());
            close(previous);
            return;
        }
        // Lost the connection during the pause: wait for them (duel.reconnect-seconds) before the next round.
        UUID waiting = previous.waitingFor();
        if (waiting != null) {
            if (Bukkit.getPlayer(waiting) == null) {
                if (System.currentTimeMillis() < previous.waitUntil()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> nextRound(previous), 20L);
                    return;
                }
                Fighter winner = previous.opponent(waiting);
                byPlayer.remove(waiting, previous);
                finishMatch(previous, winner.uuid(), Game.EndReason.QUIT.name());
                summary(previous, Game.Result.WIN, Game.EndReason.QUIT, winner, winner.uuid());
                close(previous);
                return;
            }
            previous.stopWaiting();
        }
        Player p1 = Bukkit.getPlayer(previous.one().uuid());
        Player p2 = Bukkit.getPlayer(previous.two().uuid());
        if (p1 == null || p2 == null || byPlayer.get(previous.one().uuid()) != previous
                || byPlayer.get(previous.two().uuid()) != previous) {
            // Someone left during the pause: the one who stayed wins the match.
            UUID stayed = p1 != null && byPlayer.get(previous.one().uuid()) == previous ? previous.one().uuid()
                    : p2 != null && byPlayer.get(previous.two().uuid()) == previous ? previous.two().uuid() : null;
            finishMatch(previous, stayed, Game.EndReason.QUIT.name());
            summary(previous, Game.Result.WIN, Game.EndReason.QUIT, null, stayed);
            close(previous);
            return;
        }
        Kit kit = match.kit();
        ArenaInstance arena = arenas.acquire(match.template() != null ? match.template() : previous.arena().name(), kit.id());
        if (arena == null) {
            messages().send(p1, "no-arena-free");
            messages().send(p2, "no-arena-free");
            finishMatch(previous, null, Game.EndReason.ERROR.name());
            summary(previous, Game.Result.ABORTED, Game.EndReason.ERROR, null, null);
            close(previous);
            return;
        }
        match.nextRound();
        long id;
        try {
            int round = match.round();
            id = plugin.database().call(c -> GameStore.insert(c, arena.name(), arena.version(), arena.slot(), kit.id(),
                    p1.getUniqueId(), System.currentTimeMillis(), plugin.getPluginMeta().getVersion(), match.id(), round));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the next round", e);
            arenas.release(arena);
            finishMatch(previous, null, Game.EndReason.ERROR.name());
            summary(previous, Game.Result.ABORTED, Game.EndReason.ERROR, null, null);
            close(previous);
            return;
        }
        arenas.release(previous.arena());
        Game game = new Game(id, match, kit, arena, new Fighter(p1.getUniqueId(), p1.getName(), 1),
                new Fighter(p2.getUniqueId(), p2.getName(), 2), settings().countdown());
        GameMode mode = arena.allowBuild() ? GameMode.SURVIVAL : GameMode.ADVENTURE;
        for (Player p : List.of(p1, p2)) {
            PlayerState.reset(p, mode);
        }
        startRound(game, p1, p2);
        plugin.spectators().follow(previous, game);
    }

    private static StatsStore.Side side(Fighter f) {
        return new StatsStore.Side(f.uuid(), f.name(), f.damageDealt(), f.damageTaken(), f.arrowsShot(), f.arrowsHit(),
                f.totems());
    }

    /** Title and sound of a round's end (every round, no chat). */
    private void titles(Game game, Game.Result result, Game.EndReason reason, Fighter winner) {
        Messages m = messages();
        for (Fighter f : List.of(game.one(), game.two())) {
            Player player = Bukkit.getPlayer(f.uuid());
            if (player == null) {
                continue;
            }
            Fighter opponent = game.opponent(f.uuid());
            m.run(player, () -> {
                String outcome = result == Game.Result.ABORTED ? "aborted" : result != Game.Result.WIN ? "draw"
                        : winner == f ? "won" : "lost";
                player.showTitle(Title.title(m.get("end-title-" + outcome), m.get("end-reason-" + reason.name()
                                .toLowerCase(java.util.Locale.ROOT), tag("player", opponent.name())),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
                player.playSound(player.getLocation(), winner == f ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                        : Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            });
        }
    }

    /**
     * The one chat message of a duel, when it is over: a single round with its numbers, a match with the score and
     * the damage of all rounds. Says so if a round waits for an admin.
     */
    private void summary(Game game, Game.Result result, Game.EndReason reason, Fighter winner, UUID matchWinner) {
        Messages m = messages();
        Match match = game.match();
        for (Fighter f : List.of(game.one(), game.two())) {
            Player player = Bukkit.getPlayer(f.uuid());
            if (player == null) {
                continue;
            }
            Fighter opponent = game.opponent(f.uuid());
            m.run(player, () -> {
                Component review = match.heldRounds() == 0 ? Component.empty() : m.get("end-review");
                String reasonText = m.plain("end-reason-" + reason.name().toLowerCase(java.util.Locale.ROOT),
                        "player", opponent.name());
                if (match.single()) {
                    String outcome = result == Game.Result.ABORTED ? "aborted" : result != Game.Result.WIN ? "draw"
                            : winner == f ? "won" : "lost";
                    m.send(player, "end-summary-" + outcome, tag("id", game.id()), tag("player", opponent.name()),
                            tag("reason", reasonText), tag("time", clock(game.elapsed())),
                            tag("dealt", String.format(java.util.Locale.ROOT, "%.1f", f.damageDealt / 2)),
                            tag("taken", String.format(java.util.Locale.ROOT, "%.1f", f.damageTaken / 2)),
                            tag("review", review));
                } else {
                    String key = matchWinner == null ? "match-draw" : matchWinner.equals(f.uuid()) ? "match-won" : "match-lost";
                    m.send(player, key, tag("player", opponent.name()), tag("score", score(game, f.uuid())),
                            tag("id", match.id()), tag("rounds", match.round()), tag("reason", reasonText),
                            tag("dealt", String.format(java.util.Locale.ROOT, "%.1f", match.dealt(f.side()) / 2)),
                            tag("taken", String.format(java.util.Locale.ROOT, "%.1f", match.taken(f.side()) / 2)),
                            tag("review", review));
                }
            });
        }
    }

    /** Both get their things back, the arena is free again. */
    private void close(Game game) {
        games.remove(game.id());
        for (Fighter f : List.of(game.one(), game.two())) {
            bar.hide(f.uuid());
            byPlayer.remove(f.uuid(), game);
            Player player = Bukkit.getPlayer(f.uuid());
            if (player != null) {
                plugin.stateKeeper().leave(player);
            } else {
                // Gone (e.g. did not come back in time): their state is given back on the next login.
                plugin.stateKeeper().forget(f.uuid());
            }
        }
        plugin.spectators().gameClosed(game);
        plugin.admin().webEvent("game", String.valueOf(game.id()), false);
        arenas.release(game.arena());
    }

    public Game game(long id) {
        return games.get(id);
    }

    /** An admin ends a round: aborted, nobody wins, both get their things back. */
    public void stop(Game game, String admin) {
        if (game.state() == Game.State.ENDING) {
            return;
        }
        plugin.getLogger().info(admin + " stopped round #" + game.id() + ".");
        end(game, Game.Result.ABORTED, Game.EndReason.ADMIN, null, admin);
    }

    /** Server stops: every round ends now and is written before the database closes. */
    public void endAll() {
        for (Game game : List.copyOf(games.values())) {
            if (game.state() == Game.State.ENDING) {
                close(game);
            } else {
                end(game, Game.Result.ABORTED, Game.EndReason.SHUTDOWN, null, null);
            }
        }
    }

    private void forEachOnline(Game game, java.util.function.Consumer<Player> action) {
        for (Fighter f : List.of(game.one(), game.two())) {
            Player player = Bukkit.getPlayer(f.uuid());
            if (player != null) {
                messages().run(player, () -> action.accept(player));
            }
        }
    }

    /** "1:05" */
    public static String clock(long millis) {
        long secondsTotal = millis / 1000;
        return secondsTotal / 60 + ":" + String.format("%02d", secondsTotal % 60);
    }
}
