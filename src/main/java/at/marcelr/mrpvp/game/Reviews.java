package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Settings;
import at.marcelr.mrpvp.stats.StatsStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Conspicuous rounds wait for an admin (in-game: admin menu → review, web: MRadmin). Counts how many wait, tells
 * admins, decides automatically after {@code review.auto-days}. The decision itself is SQL in {@link StatsStore}.
 * Main thread only (the database work runs on the storage thread).
 */
public final class Reviews {

    public static final String PERM_REVIEW = "mrpvp.admin.review";
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long HOUR_TICKS = 20L * 60 * 60;

    private final MRPvp plugin;
    private int pending;
    private BukkitTask task;

    public Reviews(MRPvp plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        refresh();
        // Once shortly after the start, then every hour.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::autoDecide, 20L * 30, HOUR_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Rounds waiting for a decision (as of the last change). */
    public int pending() {
        return pending;
    }

    private void refresh() {
        plugin.database().submit(StatsStore::pendingCount).thenAccept(count -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> pending = count);
            }
        });
    }

    /** A round just went into review: count it, tell the admins online, MRadmin's web pages load again. */
    void held(long id, String one, String two, Set<String> flags) {
        pending++;
        String reasons = flags.stream().sorted().collect(Collectors.joining(", "));
        plugin.getLogger().info("Round #" + id + " (" + one + " vs " + two + ") waits for a review: " + reasons + ".");
        plugin.admin().webEvent("review", String.valueOf(id), false);
        if (plugin.settings().monitor().discord()) {
            plugin.admin().adminLog(plugin.messages().server(() -> plugin.messages().plain("review-discord", "id", id,
                    "one", one, "two", two, "flags", flagNames(flags))));
        }
        if (!plugin.settings().review().notifyAdmins()) {
            return;
        }
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(PERM_REVIEW)) {
                plugin.messages().run(admin, () -> plugin.messages().send(admin, "review-new", tag("id", id),
                        tag("one", one), tag("two", two), tag("flags", flagNames(flags)), tag("open", openButton())));
            }
        }
    }

    /** On join: admins hear how many rounds wait. */
    public void joined(Player player) {
        if (pending > 0 && plugin.settings().review().notifyAdmins() && player.hasPermission(PERM_REVIEW)) {
            plugin.messages().run(player, () -> plugin.messages().send(player, "review-waiting",
                    tag("count", pending), tag("open", openButton())));
        }
    }

    private Component openButton() {
        var m = plugin.messages();
        return m.get("review-open-button").clickEvent(ClickEvent.runCommand("/duel review"))
                .hoverEvent(HoverEvent.showText(m.get("review-open-hover")));
    }

    /** "Farming, kein Treffer" - the flags in the viewer's language. */
    public String flagNames(Set<String> flags) {
        return flags.stream().sorted().map(f -> plugin.messages().plain("flag-" + f.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining(", "));
    }

    /** A comma list from the database as set. */
    public static Set<String> flags(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        return Set.of(stored.split(","));
    }

    /**
     * Counts the round (statistics are built again) or not.
     *
     * @return false (completed) if it did not wait any more
     */
    public CompletableFuture<Boolean> decide(long id, boolean count, String by) {
        long now = System.currentTimeMillis();
        return plugin.database().submit(c -> StatsStore.decide(c, id, count, by, now)).thenApply(changed -> {
            if (changed) {
                plugin.getLogger().info(by + (count ? " counted" : " discarded") + " the reviewed round #" + id + ".");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    refresh();
                    plugin.admin().webEvent("review", String.valueOf(id), false);
                });
            }
            return changed;
        });
    }

    /**
     * Takes a counted round out of the statistics, or counts a removed / discarded one again (statistics are built
     * again). Logged on the console and - like reviews - in MRadmin's Discord admin log.
     *
     * @param note optional reason
     * @return false (completed) if the round was not in that state
     */
    public CompletableFuture<Boolean> setCounted(long id, boolean count, String by, String note) {
        long now = System.currentTimeMillis();
        String clean = note == null ? "" : note.replaceAll("\\p{Cntrl}", " ").strip();
        String stored = clean.length() > 200 ? clean.substring(0, 200) : clean;
        return plugin.database().submit(c -> StatsStore.setCounted(c, id, count, by, stored, now)).thenApply(changed -> {
            if (changed) {
                plugin.getLogger().info(by + (count ? " counted round #" + id + " again"
                        : " took round #" + id + " out of the statistics") + (stored.isEmpty() ? "." : ": " + stored));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String key = count ? "admin-log-recounted" : "admin-log-uncounted";
                    Object[] values = {"id", id, "admin", by, "note", stored.isEmpty() ? "-" : stored};
                    if (plugin.settings().monitor().discord()) {
                        plugin.admin().adminLog(plugin.messages().server(() -> plugin.messages().plain(key, values)));
                    }
                    plugin.admin().webEvent("game", String.valueOf(id), false);
                });
            }
            return changed;
        });
    }

    /** Rounds nobody decided about within review.auto-days. */
    private void autoDecide() {
        Settings.ReviewSettings s = plugin.settings().review();
        if (s.autoCount() == null) {
            return;
        }
        boolean count = s.autoCount();
        long before = System.currentTimeMillis() - s.autoDays() * DAY;
        plugin.database().submit(c -> {
            List<Long> ids = StatsStore.pendingBefore(c, before);
            long now = System.currentTimeMillis();
            for (long id : ids) {
                StatsStore.decide(c, id, count, "auto", now);
            }
            return ids.size();
        }).whenComplete((decided, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not decide old reviews", error);
            } else if (decided > 0) {
                plugin.getLogger().info(decided + " round(s) waited longer than " + s.autoDays() + " days for a review"
                        + " - " + (count ? "counted" : "discarded") + " automatically.");
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, this::refresh);
                }
            }
        });
    }

    /** For the admin menu and the web: the waiting rounds, newest first. */
    public CompletableFuture<List<StatsStore.Pending>> list(int limit) {
        return plugin.database().submit(c -> StatsStore.pending(c, limit));
    }

    /** Blocking variant for MRadmin's web requests (already off the main thread there it is called synced). */
    public List<StatsStore.Pending> listNow(int limit) throws SQLException {
        return plugin.database().call(c -> StatsStore.pending(c, limit));
    }
}
