package at.marcelr.mrpvp.command;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Messages;
import at.marcelr.mrpvp.UpdateChecker;
import at.marcelr.mrpvp.snapshot.Snapshot;
import at.marcelr.mrpvp.snapshot.SnapshotStore;
import at.marcelr.mrpvp.snapshot.StateKeeper;
import at.marcelr.mrpvp.util.Npcs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * /mrpvp version | reload | debug - for the console and emergencies. Everything players and admins do in game
 * goes through the menus (/duel), this command only offers what a server owner needs without them.
 */
public final class MRPvpCommand implements TabExecutor {

    public static final String PERM_RELOAD = "mrpvp.admin.reload";
    public static final String PERM_DEBUG = "mrpvp.admin.debug";

    private final MRPvp plugin;

    public MRPvpCommand(MRPvp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label,
                             String @NonNull [] args) {
        // Every text of the command in the language of the sender.
        return plugin.messages().as(sender, () -> execute(sender, label, args));
    }

    private boolean execute(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        String sub = args.length == 0 ? "version" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "version", "ver" -> sendVersion(sender);
            case "reload" -> {
                if (!sender.hasPermission(PERM_RELOAD)) {
                    messages.send(sender, "no-permission");
                    return true;
                }
                plugin.reload();
                messages.send(sender, "reloaded");
                plugin.getLogger().info(sender.getName() + " reloaded the configuration.");
            }
            case "debug" -> {
                if (!sender.hasPermission(PERM_DEBUG)) {
                    messages.send(sender, "no-permission");
                    return true;
                }
                debug(sender, label, args);
            }
            default -> messages.send(sender, "admin-usage", tag("label", label));
        }
        return true;
    }

    /** debug arena | leave | state [player] - tests saving and giving back the state without a round. */
    private void debug(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        String action = args.length < 2 ? "state" : args[1].toLowerCase(Locale.ROOT);
        StateKeeper keeper = plugin.stateKeeper();
        switch (action) {
            case "arena" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "player-only");
                    return;
                }
                if (plugin.arenaWorld().world() == null) {
                    messages.send(sender, "arena-world-missing");
                    return;
                }
                if (keeper.inside(player)) {
                    messages.send(sender, "debug-already-inside");
                    return;
                }
                Snapshot stored = keeper.enter(player, Snapshot.Purpose.DEBUG, null, GameMode.SURVIVAL);
                if (stored == null) {
                    messages.send(sender, "state-save-failed");
                    return;
                }
                player.teleport(plugin.arenaWorld().platform());
                messages.send(sender, "debug-entered", tag("id", stored.id()), tag("label", label));
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "player-only");
                    return;
                }
                if (!keeper.leave(player)) {
                    messages.send(sender, "debug-not-inside");
                    return;
                }
                messages.send(sender, "debug-left");
            }
            case "state" -> state(sender, args.length >= 3 ? args[2] : null);
            default -> messages.send(sender, "debug-usage", tag("label", label));
        }
    }

    private void state(CommandSender sender, String name) {
        Messages messages = plugin.messages();
        OfflinePlayer target = name == null
                ? (sender instanceof Player self ? self : null)
                : Bukkit.getOfflinePlayerIfCached(name);
        int owedTotal;
        List<Snapshot> latest;
        try {
            owedTotal = plugin.database().call(SnapshotStore::owedCount);
            latest = target == null ? List.of()
                    : plugin.database().call(c -> SnapshotStore.latest(c, target.getUniqueId(), 5));
        } catch (SQLException e) {
            messages.send(sender, "database-error", tag("error", e.getMessage()));
            return;
        }
        messages.send(sender, "debug-state", tag("active", plugin.stateKeeper().activeCount()),
                tag("owed", owedTotal), tag("size", plugin.database().sizeBytes() / 1024));
        if (target == null) {
            if (name != null) {
                messages.send(sender, "player-unknown", tag("player", name));
            }
            return;
        }
        messages.send(sender, "debug-state-player", tag("player", target.getName() == null ? name : target.getName()),
                tag("count", latest.size()));
        SimpleDateFormat format = new SimpleDateFormat("dd.MM. HH:mm:ss");
        for (Snapshot s : latest) {
            String state = s.phase() == Snapshot.Phase.END ? messages.plain("debug-snapshot-end")
                    : s.restoredAt() == null ? messages.plain("debug-snapshot-owed")
                    : messages.plain("debug-snapshot-restored", "time", format.format(new Date(s.restoredAt())));
            messages.send(sender, "debug-snapshot", tag("id", s.id()), tag("purpose", s.purpose().name()),
                    tag("time", format.format(new Date(s.takenAt()))), tag("state", state),
                    tag("world", s.world()));
        }
    }

    private void sendVersion(CommandSender sender) {
        Messages messages = plugin.messages();
        UpdateChecker updates = plugin.updateChecker();
        messages.send(sender, "version",
                tag("version", updates.currentVersion()), tag("mc", updates.minecraftVersion()),
                tag("author", String.join(", ", plugin.getPluginMeta().getAuthors())));
        Runnable links = () -> messages.send(sender, "version-links",
                tag("website", link("Website", updates.websitePage())),
                tag("modrinth", link("Website", updates.websitePage())),
                tag("github", link("GitHub", updates.githubPage())));
        if (plugin.settings().update().enabled()) {
            updates.checkNow(sender, links);
        } else {
            updates.sendStatus(sender);
            links.run();
        }
    }

    private static Component link(String label, String url) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(url));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label,
                                      String @NonNull [] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("version");
            if (sender.hasPermission(PERM_RELOAD)) {
                options.add("reload");
            }
            if (sender.hasPermission(PERM_DEBUG)) {
                options.add("debug");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug") && sender.hasPermission(PERM_DEBUG)) {
            options.addAll(List.of("arena", "leave", "state"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("state")
                && sender.hasPermission(PERM_DEBUG)) {
            Bukkit.getOnlinePlayers().stream().filter(p -> !Npcs.is(p)).forEach(p -> options.add(p.getName()));
        }
        String start = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(start)).toList();
    }
}
