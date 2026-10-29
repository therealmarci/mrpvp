package at.marcelr.mrpvp;

import at.marcelr.mrpvp.stats.StatsRules;
import at.marcelr.mrpvp.util.DurationUtil;
import org.bukkit.Difficulty;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Values from config.yml, checked and with sane limits. Built again on every reload. */
public record Settings(
        ArenaWorldSettings arenaWorld,
        ArenaSettings arenas,
        DuelSettings duel,
        StatsRules stats,
        ProtectionSettings protection,
        HistorySettings history,
        MonitorSettings monitor,
        ReviewSettings review,
        UpdateSettings update,
        GuiSettings gui
) {

    /**
     * @param inviteSeconds   how long an invitation stays open
     * @param cooldownSeconds wait between two invitations of the same player
     * @param countdown       seconds before the fight starts (players frozen)
     * @param maxSeconds      a round ends as a draw after this
     * @param endDelaySeconds pause between the end and getting the own things back
     * @param totems          whether a totem in the kit may save a player
     * @param allowedCommands commands (without "/") players may use in a round
     * @param reconnectSeconds how long a round waits for a player who lost the connection (once per match), 0 = not
     */
    public record DuelSettings(int inviteSeconds, int cooldownSeconds, int countdown, int maxSeconds,
                               int endDelaySeconds, boolean totems, Set<String> allowedCommands,
                               List<Integer> winChoices, List<Integer> minuteChoices,
                               at.marcelr.mrpvp.game.DuelOptions defaults, int reconnectSeconds) {

        public boolean commandAllowed(String command) {
            String name = command.toLowerCase(Locale.ROOT);
            int colon = name.indexOf(':');
            return allowedCommands.contains(name) || (colon >= 0 && allowedCommands.contains(name.substring(colon + 1)));
        }
    }

    /**
     * @param name       folder name of the arena world
     * @param difficulty never peaceful (players would heal all the time)
     * @param time       fixed time of day in ticks (6000 = noon)
     */
    public record ArenaWorldSettings(String name, Difficulty difficulty, long time, boolean hideOnSquaremap) {
    }

    /**
     * @param pool      ready (pasted) slots per template for new templates
     * @param maxSlots  most arenas at the same time (all templates together)
     * @param spacing   distance between two slots in blocks
     * @param maxWidth  largest arena (x and z) the editor saves
     * @param maxHeight largest arena height the editor saves
     * @param headroom  air above an arena players may use (elytra, mace) before they are put back
     */
    public record ArenaSettings(int pool, int maxSlots, int spacing, int maxWidth, int maxHeight, int headroom) {
    }

    /**
     * @param revokeAdvancements advancements reached in the arena world are taken back
     * @param restoreStatistics  vanilla statistics (damage, jumps, walked ...) are put back after a round
     */
    public record ProtectionSettings(boolean revokeAdvancements, boolean restoreStatistics) {
    }

    /**
     * @param days         combat log, positions and end states are kept this long
     * @param snapshotDays states that were given back are kept this long (owed ones always)
     */
    public record HistorySettings(int days, int snapshotDays) {
    }

    /**
     * Conspicuous rounds do not simply not count - an admin decides.
     *
     * @param hold      flags that make a round wait (farming, limit, short, hit, border, idle, quit); others are only
     *                  reported
     * @param autoDays  undecided rounds are decided automatically after so many days
     * @param autoCount true = they count then, false = they never count, null = they keep waiting
     * @param notifyAdmins admins online get a message when a round waits
     */
    public record ReviewSettings(Set<String> hold, int autoDays, Boolean autoCount, boolean notifyAdmins) {
    }

    /**
     * @param discord          also into MRadmin's Discord admin log
     * @param farming          when a series of the same two players counts as statistics farming
     * @param maxHitHearts     a single hit at least this strong is noticed (mace excluded)
     * @param borderTries      this many tries to leave the arena in one round
     * @param idleSeconds      a round at least this long without any hit
     */
    public record MonitorSettings(boolean enabled, boolean discord, at.marcelr.mrpvp.game.FarmingCheck.Rules farming,
                                  double maxHitHearts,
                                  int borderTries, int idleSeconds) {
    }

    public record UpdateSettings(boolean enabled, String source, boolean includePrereleases,
                                 int intervalHours, boolean notifyOnJoin) {
    }

    /** @param banDurations quick choices for duel bans in MRadmin's web interface, in ms ({@link #PERMANENT} = forever) */
    public record GuiSettings(int refreshTicks, boolean sounds, List<Long> banDurations) {
    }

    public static final long PERMANENT = -1L;

    /** Sorted, without duplicates, within limits; the fallback if nothing valid is left. */
    private static List<Integer> choices(List<Integer> values, List<Integer> fallback, int min, int max) {
        List<Integer> result = values.stream().filter(v -> v >= min && v <= max).distinct().sorted().toList();
        return result.isEmpty() ? fallback : result;
    }

    public static Settings from(FileConfiguration c, Logger log) {
        String worldName = c.getString("arena-world.name", "mrpvp_arenas").trim();
        if (!worldName.matches("[a-zA-Z0-9_\\-]+")) {
            log.warning("Invalid arena-world.name '" + worldName + "' (only letters, digits, _ and -), using mrpvp_arenas.");
            worldName = "mrpvp_arenas";
        }
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(c.getString("arena-world.difficulty", "normal").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Invalid arena-world.difficulty, using normal.");
            difficulty = Difficulty.NORMAL;
        }
        if (difficulty == Difficulty.PEACEFUL) {
            log.warning("arena-world.difficulty peaceful would heal players during fights, using easy.");
            difficulty = Difficulty.EASY;
        }
        ArenaWorldSettings arenaWorld = new ArenaWorldSettings(worldName, difficulty,
                Math.floorMod(c.getLong("arena-world.time", 6000), 24_000L),
                c.getBoolean("arena-world.hide-on-squaremap", true));

        ArenaSettings arenas = new ArenaSettings(
                Math.clamp(c.getInt("arenas.pool-per-template", 1), 0, 16),
                Math.clamp(c.getInt("arenas.max-slots", 32), 1, 512),
                Math.clamp(c.getInt("arenas.spacing", 1000), 200, 10_000),
                Math.clamp(c.getInt("arenas.max-width", 64), 8, 128),
                Math.clamp(c.getInt("arenas.max-height", 48), 4, 128),
                Math.clamp(c.getInt("arenas.headroom", 150), 0, 200));

        Set<String> commands = new HashSet<>();
        for (String command : c.getStringList("duel.allowed-commands")) {
            String name = command.trim().toLowerCase(Locale.ROOT);
            commands.add(name.startsWith("/") ? name.substring(1) : name);
        }
        // Without these nobody could give up or open the menu.
        commands.add("duel");
        List<Integer> wins = choices(c.getIntegerList("duel.options.wins"), List.of(1, 2, 3, 5), 1, 25);
        List<Integer> minutes = choices(c.getIntegerList("duel.options.minutes"), List.of(3, 5, 10, 15), 1, 120);
        int defaultWins = c.getInt("duel.options.default-wins", 1);
        int defaultMinutes = c.getInt("duel.options.default-minutes", 10);
        at.marcelr.mrpvp.game.DuelOptions defaults = new at.marcelr.mrpvp.game.DuelOptions(
                wins.contains(defaultWins) ? defaultWins : wins.getFirst(),
                minutes.contains(defaultMinutes) ? defaultMinutes : minutes.getFirst(),
                c.getBoolean("duel.options.default-hunger", true),
                c.getBoolean("duel.options.default-regen", true));
        DuelSettings duel = new DuelSettings(
                Math.clamp(c.getInt("duel.invite-seconds", 30), 5, 600),
                Math.clamp(c.getInt("duel.invite-cooldown-seconds", 10), 0, 3600),
                Math.clamp(c.getInt("duel.countdown-seconds", 3), 0, 10),
                defaults.minutes() * 60,
                Math.clamp(c.getInt("duel.end-delay-seconds", 3), 0, 30),
                c.getBoolean("duel.totems", true),
                Set.copyOf(commands), wins, minutes, defaults,
                Math.clamp(c.getInt("duel.reconnect-seconds", 20), 0, 300));

        StatsRules stats = new StatsRules(
                Math.clamp(c.getInt("stats.min-duration-seconds", 10), 0, 600) * 1000L,
                Math.clamp(c.getInt("stats.max-counted-per-opponent-per-day", 50), 0, 1000),
                c.getBoolean("stats.quit-counts-as-death", true),
                c.getBoolean("stats.surrender-counts-as-death", true),
                c.getBoolean("stats.border-counts-as-kill", true),
                Math.clamp(c.getInt("stats.min-games-for-ratio", 10), 0, 1000));
        ProtectionSettings protection = new ProtectionSettings(
                c.getBoolean("protection.revoke-advancements", true),
                c.getBoolean("protection.restore-statistics", true));
        HistorySettings history = new HistorySettings(
                Math.clamp(c.getInt("history.days", 30), 1, 3650),
                Math.clamp(c.getInt("history.snapshot-days", 90), 1, 3650));

        MonitorSettings monitor = new MonitorSettings(
                c.getBoolean("monitor.enabled", true),
                c.getBoolean("monitor.discord", true),
                new at.marcelr.mrpvp.game.FarmingCheck.Rules(
                        Math.clamp(c.getInt("monitor.farming.rounds", 8), 2, 1000),
                        Math.clamp(c.getInt("monitor.farming.minutes", 60), 1, 24 * 60) * 60_000L,
                        Math.clamp(c.getInt("monitor.farming.winner-percent", 90), 50, 100) / 100.0,
                        Math.clamp(c.getInt("monitor.farming.max-loser-damage-percent", 20), 0, 100) / 100.0),
                Math.clamp(c.getDouble("monitor.max-hit-hearts", 20), 1, 1000),
                Math.clamp(c.getInt("monitor.border-tries", 10), 1, 1000),
                Math.clamp(c.getInt("monitor.idle-seconds", 90), 10, 3600));

        String source = c.getString("update-checker.source", "github").toLowerCase(Locale.ROOT);
        if (!source.equals("modrinth") && !source.equals("github")) {
            log.warning("Invalid update-checker.source '" + source + "', using github.");
            source = "github";
        }
        UpdateSettings update = new UpdateSettings(
                c.getBoolean("update-checker.enabled", true),
                source,
                c.getBoolean("update-checker.include-prereleases", false),
                Math.max(1, c.getInt("update-checker.interval-hours", 6)),
                c.getBoolean("update-checker.notify-on-join", true));

        GuiSettings gui = new GuiSettings(
                Math.max(5, c.getInt("gui.refresh-ticks", 20)),
                c.getBoolean("gui.sounds", true),
                banDurations(c.getStringList("gui.ban-durations"), log));

        Set<String> hold = new HashSet<>();
        for (String flag : c.getStringList("review.hold")) {
            hold.add(flag.trim().toLowerCase(Locale.ROOT));
        }
        String decision = c.getString("review.auto-decision", "count").trim().toLowerCase(Locale.ROOT);
        ReviewSettings review = new ReviewSettings(Set.copyOf(hold),
                Math.clamp(c.getInt("review.auto-days", 7), 1, 365),
                decision.equals("discard") ? Boolean.FALSE : decision.equals("none") ? null : Boolean.TRUE,
                c.getBoolean("review.notify", true));

        return new Settings(arenaWorld, arenas, duel, stats, protection, history, monitor, review, update, gui);
    }

    /** Exactly three durations; invalid entries fall back to the default at their position. */
    private static List<Long> banDurations(List<String> raw, Logger log) {
        List<String> defaults = List.of("1d", "7d", "permanent");
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < defaults.size(); i++) {
            String value = i < raw.size() ? raw.get(i).trim() : defaults.get(i);
            long parsed = banDuration(value);
            if (parsed == 0) {
                log.warning("Invalid duration '" + value + "' in gui.ban-durations, using " + defaults.get(i) + ".");
                parsed = banDuration(defaults.get(i));
            }
            result.add(parsed);
        }
        return List.copyOf(result);
    }

    /** "permanent" -> {@link #PERMANENT}, "7d" -> ms, invalid -> 0 (parse() reports invalid as -1). */
    private static long banDuration(String value) {
        if (value.equalsIgnoreCase("permanent")) {
            return PERMANENT;
        }
        long parsed = DurationUtil.parse(value);
        return parsed > 0 ? parsed : 0;
    }
}
