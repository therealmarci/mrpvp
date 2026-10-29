package at.marcelr.mrpvp.command;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.game.Invite;
import at.marcelr.mrpvp.game.Reviews;
import at.marcelr.mrpvp.gui.InviteMenu;
import at.marcelr.mrpvp.gui.AdminMenu;
import at.marcelr.mrpvp.gui.DuelSetupMenu;
import at.marcelr.mrpvp.gui.MainMenu;
import at.marcelr.mrpvp.gui.ReviewsMenu;
import at.marcelr.mrpvp.util.Npcs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * /duel - only an entrance to the menus: no argument opens the main menu, a player name the duel setup for that
 * player. "invite / accept / decline &lt;id&gt;" and "review" are the chat buttons, "leave" gives up.
 */
public final class DuelCommand implements TabExecutor {

    private final MRPvp plugin;

    public DuelCommand(MRPvp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label,
                             String @NonNull [] args) {
        plugin.messages().run(sender, () -> execute(sender, args));
        return true;
    }

    private void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission(GameManager.PERM_USE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        // Editing a kit in the own inventory: /duel opens its menu, /duel kit <done|cancel|icon> are its chat buttons.
        if (plugin.kitEditor().editing(player) && (args.length == 0 || args[0].equalsIgnoreCase("kit"))) {
            plugin.kitEditor().command(player, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "");
            return;
        }
        if (args.length == 0) {
            new MainMenu(plugin, player).open();
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "kit" -> plugin.messages().send(player, "kit-inventory-none");
            case "invite" -> {
                Invite invite = invite(player, args);
                if (invite != null) {
                    new InviteMenu(plugin, player, invite).open();
                }
            }
            case "accept", "decline" -> {
                Invite invite = invite(player, args);
                if (invite != null) {
                    if (args[0].equalsIgnoreCase("accept")) {
                        plugin.games().accept(player, invite);
                    } else {
                        plugin.games().decline(player, invite);
                    }
                }
            }
            case "review" -> {
                if (!player.hasPermission(Reviews.PERM_REVIEW)) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                new ReviewsMenu(plugin, player).open();
            }
            case "leave" -> plugin.games().surrender(player);
            case "admin" -> {
                if (!player.hasPermission(AdminMenu.PERM_GUI)) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                new AdminMenu(plugin, player).open();
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !player.canSee(target)) {
                    plugin.messages().send(player, "invite-error-offline", tag("player", args[0]));
                    return;
                }
                if (target.equals(player)) {
                    plugin.messages().send(player, "invite-error-self");
                    return;
                }
                DuelSetupMenu.open(plugin, player, target);
            }
        }
    }


    /** The invitation to this player with the id in args[1] (chat buttons), or null after telling them. */
    private Invite invite(Player player, String[] args) {
        Invite invite = null;
        try {
            invite = args.length > 1 ? plugin.games().invites().get(Integer.parseInt(args[1])) : null;
        } catch (NumberFormatException ignored) {
            // falls through to "expired"
        }
        if (invite == null || !invite.to().equals(player.getUniqueId())) {
            plugin.messages().send(player, "invite-expired-self");
            return null;
        }
        return invite;
    }
    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label,
                                      String @NonNull [] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        String start = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player) && !Npcs.is(p) && player.canSee(p))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(start))
                .toList();
    }
}
