package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import org.bukkit.Material;

import static at.marcelr.mrpvp.Messages.tag;

/** Menu icon of an arena template. */
final class ArenaIcon {

    private ArenaIcon() {
    }

    static Material material(ArenaTemplate t) {
        Material icon = Material.matchMaterial(t.icon());
        return icon == null || !icon.isItem() || icon.isAir() ? Material.STONE_BRICKS : icon;
    }

    /** For players: name, size, kits. */
    static Items player(MRPvp plugin, ArenaTemplate t) {
        var m = plugin.messages();
        return Items.of(material(t)).name(m.miniItem(t.name())).flags()
                .lore(m.lore("gui-arena-player-lore", tag("size", t.sizeX() + "×" + t.sizeZ()),
                        tag("kits", (t.kits().isEmpty() ? m.plain("gui-template-kits-all") : String.join(", ", t.kits()))
                                + (t.elytraOnly() ? m.plain("gui-template-kits-elytra") : "")),
                        tag("build", m.plain(t.allowBuild() ? "gui-arena-build-yes" : "gui-arena-build-no"))));
    }

    /** For admins: everything incl. slots. */
    static Items admin(MRPvp plugin, ArenaTemplate t) {
        var m = plugin.messages();
        int[] counts = plugin.slots().counts(t.id());
        return Items.of(material(t)).name(m.miniItem(t.name())).flags().glint(t.enabled())
                .lore(m.lore("gui-arena-admin-lore", tag("id", t.id()), tag("version", t.version()),
                        tag("size", t.sizeX() + "×" + t.sizeY() + "×" + t.sizeZ()),
                        tag("state", m.plain(t.enabled() ? "gui-arena-enabled" : "gui-arena-disabled")),
                        tag("pool", t.pool()), tag("ready", counts[0]), tag("used", counts[1]),
                        tag("kits", (t.kits().isEmpty() ? m.plain("gui-template-kits-all") : String.join(", ", t.kits()))
                                + (t.elytraOnly() ? m.plain("gui-template-kits-elytra") : "")),
                        tag("build", m.plain(t.allowBuild() ? "gui-arena-build-yes" : "gui-arena-build-no"))));
    }
}
