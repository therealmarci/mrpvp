package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Bans;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

import static at.marcelr.mrpvp.Messages.tag;

/** Players excluded from duels; click opens the player. */
public final class BansMenu extends Menu {

    public BansMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-bans-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<Bans.Ban> bans = plugin.bans().all();
        if (bans.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(m.item("gui-bans-empty")).build());
        }
        for (int i = 0; i < bans.size() && i < 45; i++) {
            Bans.Ban ban = bans.get(i);
            set(i, Items.head(Bukkit.getOfflinePlayer(ban.uuid())).name(m.item("gui-players-name", tag("player", ban.name())))
                    .lore(m.lore("gui-bans-lore", tag("until", ban.until() == null ? m.plain("ban-permanent")
                                    : m.dateTime(ban.until())), tag("reason", ban.reason() == null ? "-" : ban.reason()),
                            tag("by", ban.by()), tag("date", m.dateTime(ban.createdAt())))).build(), click -> {
                clickSound();
                new PlayerAdminMenu(plugin, viewer, ban.uuid(), ban.name()).open();
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new PlayersMenu(plugin, viewer, 0).open();
        });
        fillRow(5);
    }
}
