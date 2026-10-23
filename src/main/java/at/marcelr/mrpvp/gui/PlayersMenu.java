package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Bans;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

import static at.marcelr.mrpvp.Messages.tag;

/** Admin: online players (and banned ones), search by name for offline players. */
public final class PlayersMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;

    public PlayersMenu(MRPvp plugin, Player viewer, int page) {
        super(plugin, viewer, 6, plugin.messages().get("gui-players-admin-title"));
        this.page = page;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<Player> online = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).map(p -> (Player) p).toList();
        int pages = Math.max(1, (online.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < online.size(); i++) {
            Player target = online.get(page * PER_PAGE + i);
            Bans.Ban ban = plugin.bans().get(target.getUniqueId());
            set(i, Items.head(target).name(m.item("gui-players-name", tag("player", target.getName())))
                    .lore(m.lore(ban != null ? "gui-players-admin-banned" : plugin.games().inGame(target.getUniqueId())
                            ? "gui-players-admin-ingame" : "gui-players-admin-click")).build(), click -> {
                clickSound();
                new PlayerAdminMenu(plugin, viewer, target.getUniqueId(), target.getName()).open();
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new AdminMenu(plugin, viewer).open();
        });
        if (page > 0) {
            set(48, Items.of(Material.SPECTRAL_ARROW).name(m.item("gui-page-previous")).build(), click -> {
                page--;
                render();
            });
        }
        set(49, Items.of(Material.PAPER).name(m.item("gui-page", tag("page", page + 1), tag("pages", pages))).build());
        if (page < pages - 1) {
            set(50, Items.of(Material.SPECTRAL_ARROW).name(m.item("gui-page-next")).build(), click -> {
                page++;
                render();
            });
        }
        set(52, Items.of(Material.BARRIER).name(m.item("gui-players-admin-bans", tag("count", plugin.bans().all().size())))
                .lore(m.lore("gui-players-admin-bans-lore")).build(), click -> {
            clickSound();
            new BansMenu(plugin, viewer).open();
        });
        set(53, Items.of(Material.NAME_TAG).name(m.item("gui-players-admin-search")).build(), click ->
                TextPrompt.ask(plugin, viewer, "prompt-player", "", 16, value -> {
                    OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(value);
                    if (target == null || target.getName() == null) {
                        m.send(viewer, "player-unknown", tag("player", value));
                        open();
                        return;
                    }
                    new PlayerAdminMenu(plugin, viewer, target.getUniqueId(), target.getName()).open();
                }, this::open));
        fillRow(5);
    }
}
