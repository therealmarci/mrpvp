package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Invite;
import at.marcelr.mrpvp.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

import static at.marcelr.mrpvp.Messages.tag;

/** The open invitations to the viewer. */
public final class InvitesMenu extends Menu {

    public InvitesMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 3, plugin.messages().get("gui-invites-title"));
    }

    @Override
    protected void draw() {
        long now = System.currentTimeMillis();
        List<Invite> invites = plugin.games().invites().to(viewer.getUniqueId(), now);
        if (invites.isEmpty()) {
            set(13, Items.of(Material.STRUCTURE_VOID).name(plugin.messages().item("gui-invites-empty")).build());
        }
        for (int i = 0; i < invites.size() && i < 18; i++) {
            Invite invite = invites.get(i);
            Kit kit = plugin.kits().get(invite.kit());
            Items head = Bukkit.getPlayer(invite.from()) instanceof Player from ? Items.head(from)
                    : Items.of(Material.PLAYER_HEAD);
            set(i, head.name(plugin.messages().item("gui-invites-name", tag("player", invite.fromName())))
                    .lore(plugin.messages().lore("gui-invites-lore",
                            tag("kit", kit == null ? plugin.messages().get("kit-unknown") : plugin.messages().mini(kit.name())),
                            tag("arena", plugin.games().arenaName(invite.arena())),
                            tag("options", kit == null ? net.kyori.adventure.text.Component.empty()
                                    : plugin.games().optionsText(invite.options().with(kit))),
                            tag("seconds", Math.max(0, (invite.expires() - now) / 1000)))).build(), click -> {
                clickSound();
                new InviteMenu(plugin, viewer, invite).open();
            });
        }
        set(18, Items.of(Material.ARROW).name(plugin.messages().item("gui-back")).build(), click -> {
            clickSound();
            new MainMenu(plugin, viewer).open();
        });
        fillRow(2);
    }
}
