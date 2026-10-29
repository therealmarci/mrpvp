package at.marcelr.mrpvp;

import at.marcelr.mrpvp.api.MRadminLink;
import at.marcelr.mrpvp.arena.ArenaProtection;
import at.marcelr.mrpvp.arena.BuiltinArenas;
import at.marcelr.mrpvp.arena.DefaultArena;
import at.marcelr.mrpvp.arena.SlotManager;
import at.marcelr.mrpvp.arena.TemplateManager;
import at.marcelr.mrpvp.command.DuelCommand;
import at.marcelr.mrpvp.command.MRPvpCommand;
import at.marcelr.mrpvp.editor.CreativeClearGuard;
import at.marcelr.mrpvp.editor.EditorManager;
import at.marcelr.mrpvp.game.Bans;
import at.marcelr.mrpvp.game.GameListener;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.game.Monitor;
import at.marcelr.mrpvp.game.Reviews;
import at.marcelr.mrpvp.game.Spectators;
import at.marcelr.mrpvp.gui.MenuListener;
import at.marcelr.mrpvp.kit.KitManager;
import at.marcelr.mrpvp.snapshot.ProtectionListener;
import at.marcelr.mrpvp.snapshot.StateKeeper;
import at.marcelr.mrpvp.stats.GameQueries;
import at.marcelr.mrpvp.storage.Database;
import at.marcelr.mrpvp.world.ArenaWorld;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

public final class MRPvp extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Settings settings;
    // lang-version 2: invitations show the arena; 3: and the duel options; 4: chat buttons, review note, match totals;
    // 5: boss bar separator.
    private final Messages messages = new Messages(9, Map.of(
            2, Set.of("invite-sent", "invite-received", "gui-invites-lore"),
            3, Set.of("invite-sent", "invite-received", "gui-invites-lore", "game-starting", "gui-kits-click"),
            4, Set.of("invite-received", "invite-view-button", "end-summary-won", "end-summary-lost", "end-summary-draw",
                    "match-won", "match-lost", "match-draw", "mradmin-summary"),
            5, Set.of("bar-part-round"),
            // 6: "Website | GitHub" in /mrpvp version.
            6, Set.of("version-links"),
            // 7: farming means one-sided series now.
            7, Set.of("monitor-farming"),
            // 8: a click on a live round opens its control menu.
            8, Set.of("gui-live-entry-lore"),
            // 9: "My rounds" instead of "My games" in the new main menu.
            9, Set.of("gui-main-games")));
    private final StateKeeper stateKeeper = new StateKeeper(this);
    private final ArenaWorld arenaWorld = new ArenaWorld(this);
    private final KitManager kits = new KitManager(this);
    private final TemplateManager templates = new TemplateManager(this);
    // Without any arena template the built-in arena keeps duels possible.
    private final SlotManager slots = new SlotManager(this, templates, new BuiltinArenas(arenaWorld::world, () -> settings().arenas().headroom()));
    private final GameManager games = new GameManager(this, slots);
    private final EditorManager editor = new EditorManager(this);
    private final Spectators spectators = new Spectators(this);
    private final Bans bans = new Bans(this);
    private final MRadminLink admin = new MRadminLink(this);
    private final Monitor monitor = new Monitor(this);
    private final Reviews reviews = new Reviews(this);
    private Database database;
    private UpdateChecker updateChecker;
    private MenuListener menuListener;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this);
        loadConfiguration();

        database = new Database(new File(getDataFolder(), "mrpvp.db"), getLogger());
        try {
            database.open();
        } catch (SQLException e) {
            // Without the database nobody's state could be kept safe - so no rounds at all.
            getLogger().log(Level.SEVERE, "Could not open the database plugins/MRpvp/mrpvp.db - MRpvp is disabled.", e);
            database.close();
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        arenaWorld.load();
        kits.load();
        try {
            List<String> newKits = kits.addNewDefaults();
            if (!newKits.isEmpty()) {
                getLogger().info("Added the new default kits " + newKits + " to kits.yml.");
            }
        } catch (IOException e) {
            getLogger().warning("Could not add the new default kits: " + e.getMessage());
        }
        try {
            List<String> translated = kits.translateDefaults();
            if (!translated.isEmpty()) {
                getLogger().info("Default kits " + translated + " now have their names in all languages.");
                kits.load();
            }
        } catch (IOException e) {
            getLogger().warning("Could not translate the default kits: " + e.getMessage());
        }
        templates.load();
        DefaultArena.installOnce(this);
        slots.start();
        bans.load();
        games.start();
        reviews.start();
        // Old combat logs and given-back states: once at start, then daily.
        cleanupTask = Bukkit.getScheduler().runTaskTimer(this, this::cleanUp, 20L * 60, 20L * 60 * 60 * 24);
        editor.start();

        updateChecker = new UpdateChecker(this);
        updateChecker.start();
        menuListener = new MenuListener(this);
        menuListener.start();

        getServer().getPluginManager().registerEvents(stateKeeper, this);
        getServer().getPluginManager().registerEvents(new GameListener(this, games), this);
        getServer().getPluginManager().registerEvents(new ArenaProtection(this), this);
        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getPluginManager().registerEvents(new CreativeClearGuard(this), this);
        getServer().getPluginManager().registerEvents(spectators, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(menuListener, this);
        getServer().getPluginManager().registerEvents(this, this);
        // Optional: MRadmin (module API) - dashboard, player menu, server health, web interface, admin mode.
        getServer().getPluginManager().registerEvents(admin, this);
        admin.connect();
        register("mrpvp", new MRPvpCommand(this));
        register("duel", new DuelCommand(this));
    }

    @Override
    public void onDisable() {
        if (menuListener != null) {
            menuListener.stop();
            menuListener.closeAll();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (database != null) {
            // Running rounds are written, then everyone inside gets their things back before the database closes.
            if (cleanupTask != null) {
                cleanupTask.cancel();
            }
            games.stop();
            reviews.stop();
            games.endAll();
            editor.stop();
            editor.closeAll();
            spectators.closeAll();
            stateKeeper.giveBackAll();
            slots.stop();
            database.close();
            database = null;
        }
    }

    /** Reloads config.yml and the language files and applies the arena world rules again. */
    public void reload() {
        ConfigUpdater.update(this);
        loadConfiguration();
        arenaWorld.applyRules();
        kits.load();
        templates.load();
        slots.fillPools();
        updateChecker.start();
        menuListener.start();
    }

    private void loadConfiguration() {
        config = ConfigUpdater.load(this);
        messages.load(this, config.getString("language", "auto"), config.getString("fallback-language", "en"),
                config.getString("server-language", "en"));
        settings = Settings.from(config, getLogger());
    }

    /** Deletes old combat logs, positions and states that were given back long ago (history.*). */
    private void cleanUp() {
        Settings.HistorySettings history = settings.history();
        long now = System.currentTimeMillis();
        database.submit(c -> GameQueries.cleanUp(c, now, history.days(), history.snapshotDays())).thenAccept(deleted -> {
            if (deleted > 0) {
                getLogger().info("Cleaned up " + deleted + " old rows (combat logs, positions, saved states).");
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateChecker.notifyIfOutdated(event.getPlayer());
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            config = ConfigUpdater.load(this);
        }
        return config;
    }

    @Override
    public void reloadConfig() {
        config = ConfigUpdater.load(this);
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    /** The database, or null while MRpvp is disabled. */
    public Database database() {
        return database;
    }

    public StateKeeper stateKeeper() {
        return stateKeeper;
    }

    public ArenaWorld arenaWorld() {
        return arenaWorld;
    }

    public KitManager kits() {
        return kits;
    }

    public TemplateManager templates() {
        return templates;
    }

    public SlotManager slots() {
        return slots;
    }

    public Spectators spectators() {
        return spectators;
    }

    public MRadminLink admin() {
        return admin;
    }

    public Reviews reviews() {
        return reviews;
    }

    public Monitor monitor() {
        return monitor;
    }

    public Bans bans() {
        return bans;
    }

    public EditorManager editor() {
        return editor;
    }

    public GameManager games() {
        return games;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }
}
