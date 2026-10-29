package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.GameManager;
import at.marcelr.mrpvp.util.Npcs;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

import static at.marcelr.mrpvp.Messages.tag;

/** Whom to challenge: heads of the online players who may duel. */
public final class PlayerSelectMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;

    public PlayerSelectMenu(MRPvp plugin, Player viewer, int page) {
        super(plugin, viewer, 6, plugin.messages().get("gui-players-title"));
        this.page = page;
    }

    @Override
    protected void draw() {
        GameManager games = plugin.games();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                // Nobody in MRadmin's admin mode - they cannot be invited.
                .filter(p -> !p.equals(viewer) && !Npcs.is(p) && p.hasPermission(GameManager.PERM_USE) && viewer.canSee(p)
                        && !plugin.admin().adminMode(p))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> (Player) p).toList();
        int pages = Math.max(1, (players.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.clamp(page, 0, pages - 1);
        if (players.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(plugin.messages().item("gui-players-empty")).build());
        }
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < players.size(); i++) {
            Player target = players.get(page * PER_PAGE + i);
            boolean free = games.available(target);
            set(i, Items.head(target).name(plugin.messages().item("gui-players-name", tag("player", target.getName())))
                    .lore(plugin.messages().lore(free ? "gui-players-free" : "gui-players-busy")).build(), click -> {
                if (!games.available(target)) {
                    sound(org.bukkit.Sound.ENTITY_VILLAGER_NO);
                    return;
                }
                clickSound();
                DuelSetupMenu.open(plugin, viewer, target);
            });
        }
        set(45, Items.of(Material.ARROW).name(plugin.messages().item("gui-back")).build(), click -> {
            clickSound();
            new MainMenu(plugin, viewer).open();
        });
        if (page > 0) {
            set(48, Items.of(Material.SPECTRAL_ARROW).name(plugin.messages().item("gui-page-previous")).build(), click -> {
                page--;
                clickSound();
                render();
            });
        }
        set(49, Items.of(Material.PAPER).name(plugin.messages().item("gui-page", tag("page", page + 1),
                tag("pages", pages))).build());
        if (page < pages - 1) {
            set(50, Items.of(Material.SPECTRAL_ARROW).name(plugin.messages().item("gui-page-next")).build(), click -> {
                page++;
                clickSound();
                render();
            });
        }
        fillRow(5);
    }
}
