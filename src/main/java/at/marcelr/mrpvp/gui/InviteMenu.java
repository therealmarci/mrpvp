package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.game.Invite;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.stats.PlayerStats;
import at.marcelr.mrpvp.stats.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static at.marcelr.mrpvp.Messages.decimal;
import static at.marcelr.mrpvp.Messages.tag;

/** One invitation: who, which kit - accept or decline. Opened from the chat link or the invitation list. */
public final class InviteMenu extends Menu {

    private final Invite invite;
    private PlayerStats stats;
    private int[] versus;

    public InviteMenu(MRPvp plugin, Player viewer, Invite invite) {
        super(plugin, viewer, 3, plugin.messages().get("gui-invite-title", tag("player", invite.fromName())));
        this.invite = invite;
        load(c -> new Object[] {StatsStore.stats(c, invite.from(), invite.fromName(), StatsStore.ALL),
                StatsStore.headToHead(c, viewer.getUniqueId(), invite.from())}, data -> {
            stats = (PlayerStats) data[0];
            versus = (int[]) data[1];
        });
    }

    @Override
    protected void draw() {
        long now = System.currentTimeMillis();
        boolean open = plugin.games().invites().get(invite.id()) != null && !invite.expired(now);
        Player from = Bukkit.getPlayer(invite.from());
        Items head = from != null ? Items.head(from) : Items.of(Material.PLAYER_HEAD);
        if (stats != null) {
            head.lore(plugin.messages().lore("gui-invite-from-lore", tag("wins", stats.wins()), tag("games", stats.games()),
                    tag("kd", decimal(stats.kd(), 2)), tag("vwins", versus[0]), tag("vlosses", versus[1]),
                    tag("vdraws", versus[2])));
        }
        set(10, head.name(plugin.messages().item("gui-invite-from", tag("player", invite.fromName()))).build());
        Kit kit = plugin.kits().get(invite.kit());
        if (kit != null) {
            set(11, KitIcon.of(plugin, kit).build());
        }
        ArenaTemplate arena = invite.arena() == null ? null : plugin.templates().get(invite.arena());
        set(12, arena != null ? ArenaIcon.player(plugin, arena).build()
                : Items.of(Material.ENDER_EYE).name(plugin.messages().item("gui-arena-random")).build());
        if (kit != null) {
            set(13, Items.of(Material.COMPARATOR).name(plugin.messages().item("gui-invite-options"))
                    .lore(plugin.games().optionsText(invite.options().with(kit))
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).build());
        }
        if (!open) {
            set(15, Items.of(Material.CLOCK).name(plugin.messages().item("gui-invite-expired")).build(),
                    click -> viewer.closeInventory());
        } else {
            set(14, Items.of(Material.LIME_CONCRETE).name(plugin.messages().item("gui-invite-accept"))
                    .lore(plugin.messages().lore("gui-invite-accept-lore",
                            tag("seconds", Math.max(0, (invite.expires() - now) / 1000)))).build(), click -> {
                viewer.closeInventory();
                plugin.games().accept(viewer, invite);
            });
            set(16, Items.of(Material.RED_CONCRETE).name(plugin.messages().item("gui-invite-decline")).build(), click -> {
                viewer.closeInventory();
                plugin.games().decline(viewer, invite);
            });
        }
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }
}
