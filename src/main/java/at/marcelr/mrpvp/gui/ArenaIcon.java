package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.arena.ArenaTemplate;
import at.marcelr.mrpvp.editor.EditorSession;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

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

    /** For admins: everything incl. slots and changes under construction. */
    static Items admin(MRPvp plugin, ArenaTemplate t) {
        var m = plugin.messages();
        int[] counts = plugin.slots().counts(t.id());
        List<Component> lore = new ArrayList<>(m.lore("gui-arena-admin-lore", tag("id", t.id()), tag("version", t.version()),
                tag("size", t.sizeX() + "×" + t.sizeY() + "×" + t.sizeZ()),
                tag("state", m.plain(t.enabled() ? "gui-arena-enabled" : "gui-arena-disabled")),
                tag("pool", t.pool()), tag("ready", counts[0]), tag("used", counts[1]),
                tag("kits", (t.kits().isEmpty() ? m.plain("gui-template-kits-all") : String.join(", ", t.kits()))
                        + (t.elytraOnly() ? m.plain("gui-template-kits-elytra") : "")),
                tag("build", m.plain(t.allowBuild() ? "gui-arena-build-yes" : "gui-arena-build-no"))));
        EditorSession draft = plugin.editor().draft(t.id());
        if (draft != null) {
            lore.addAll(m.lore("gui-arenas-changes-lore", tag("builders", builders(plugin, draft))));
        }
        return Items.of(material(t)).name(m.miniItem(t.name())).flags().glint(t.enabled()).lore(lore);
    }

    /** A new arena under construction (not playable yet). */
    static Items draft(MRPvp plugin, EditorSession s) {
        var m = plugin.messages();
        Material icon = Material.matchMaterial(s.icon());
        return Items.of(icon == null || !icon.isItem() || icon.isAir() ? Material.STONE_BRICKS : icon)
                .name(m.miniItem(s.name())).flags()
                .lore(m.lore("gui-arenas-draft-lore", tag("id", s.id()), tag("builders", builders(plugin, s)),
                        tag("last", s.lastBuilder().isEmpty() ? s.creator() : s.lastBuilder()),
                        tag("date", m.dateTime(s.changed()))));
    }

    /** Who builds in a draft right now, or "nobody". */
    static String builders(MRPvp plugin, EditorSession s) {
        String names = plugin.editor().builderNames(s);
        return names.isEmpty() ? plugin.messages().plain("gui-arenas-nobody") : names;
    }
}
