package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.kit.KitSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

import static at.marcelr.mrpvp.Messages.tag;

/** Menu icon of a kit: name, description and what is in it. */
final class KitIcon {

    /** More lines would make the tooltip taller than the screen. */
    private static final int MAX_LINES = 12;

    private KitIcon() {
    }

    static Items of(MRPvp plugin, Kit kit) {
        Items icon = Items.of(kit.icon()).name(plugin.messages().miniItem(kit.name()));
        for (String line : kit.description()) {
            icon.lore(plugin.messages().miniItem(line));
        }
        icon.lore(Component.empty());
        int shown = 0;
        for (Map.Entry<KitSlot, ItemStack> entry : kit.items().entrySet()) {
            if (shown == MAX_LINES) {
                icon.lore(plugin.messages().item("gui-kit-more", tag("count", kit.items().size() - shown)));
                break;
            }
            ItemStack item = entry.getValue();
            Component line = Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(item.getAmount() > 1 ? item.getAmount() + "× " : "", NamedTextColor.GRAY))
                    .append(item.effectiveName().colorIfAbsent(NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false);
            if (!item.getEnchantments().isEmpty()) {
                line = line.append(Component.text(" ✦", NamedTextColor.LIGHT_PURPLE));
            }
            icon.lore(line);
            shown++;
        }
        return icon.flags();
    }
}
