package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static at.marcelr.mrpvp.Messages.tag;

/** All kits for admins, plus "new kit". */
public final class KitListMenu extends Menu {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    public KitListMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-kitlist-title"));
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        List<Kit> kits = List.copyOf(plugin.kits().all());
        for (int i = 0; i < kits.size() && i < 45; i++) {
            Kit kit = kits.get(i);
            set(i, KitIcon.of(plugin, kit).lore(m.lore("gui-kitlist-click", tag("permission", kit.permission().isEmpty() ? "-" : kit.permission()))).build(), click -> {
                clickSound();
                new KitEditorMenu(plugin, viewer, KitEditorMenu.Draft.of(kit), false).open();
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new AdminMenu(plugin, viewer).open();
        });
        set(49, Items.of(Material.WRITABLE_BOOK).name(m.item("gui-kitlist-new")).lore(m.lore("gui-kitlist-new-lore")).build(),
                click -> askId(""));
        fillRow(5);
    }

    private void askId(String initial) {
        TextPrompt.ask(plugin, viewer, "prompt-kit-id", initial, 32, value -> {
            String id = value.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!ID.matcher(id).matches()) {
                plugin.messages().send(viewer, "editor-id-invalid");
                askId(id);
                return;
            }
            if (plugin.kits().get(id) != null) {
                plugin.messages().send(viewer, "kit-id-taken", tag("id", id));
                askId(id);
                return;
            }
            new KitEditorMenu(plugin, viewer, KitEditorMenu.Draft.empty(id), true).open();
        }, this::open);
    }
}
