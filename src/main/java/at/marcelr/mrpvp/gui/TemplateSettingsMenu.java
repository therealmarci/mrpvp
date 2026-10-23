package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import static at.marcelr.mrpvp.Messages.tag;

/** Settings of the template being edited - taken over on "save". */
public final class TemplateSettingsMenu extends Menu {

    private final EditorSession session;

    public TemplateSettingsMenu(MRPvp plugin, Player viewer, EditorSession session) {
        super(plugin, viewer, 3, plugin.messages().get("gui-template-title", tag("id", session.id())));
        this.session = session;
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(10, Items.of(Material.NAME_TAG).name(m.item("gui-template-name"))
                .lore(m.lore("gui-template-name-lore", tag("name", m.mini(session.name())))).build(), click ->
                TextPrompt.ask(plugin, viewer, "prompt-arena-name", session.name(), 64, value -> {
                    if (!value.isEmpty()) {
                        session.name(value);
                    }
                    open();
                }, this::open));

        Material icon = Material.matchMaterial(session.icon());
        set(11, Items.of(icon == null || !icon.isItem() ? Material.STONE_BRICKS : icon).name(m.item("gui-template-icon"))
                .lore(m.lore("gui-template-icon-lore")).flags().build(), click -> {
            Material hand = viewer.getInventory().getItemInMainHand().getType();
            if (hand.isAir() || !hand.isItem()) {
                m.send(viewer, "editor-icon-empty-hand");
                return;
            }
            session.icon(hand.name());
            clickSound();
            render();
        });

        set(13, Items.of(session.allowBuild() ? Material.GRASS_BLOCK : Material.BARRIER)
                .name(m.item(session.allowBuild() ? "gui-template-build-on" : "gui-template-build-off"))
                .lore(m.lore("gui-template-build-lore")).build(), click -> {
            session.allowBuild(!session.allowBuild());
            clickSound();
            render();
        });

        int height = session.maxBuildHeight();
        set(14, Items.of(Material.SCAFFOLDING).name(m.item("gui-template-height",
                        tag("height", height <= 0 ? m.plain("gui-template-height-all") : String.valueOf(height))))
                .lore(m.lore("gui-template-height-lore")).build(), click -> {
            int value = session.maxBuildHeight();
            int step = click.isShiftClick() ? 10 : 1;
            value = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT ? value - step : value + step;
            session.maxBuildHeight(Math.clamp(value, 0, plugin.settings().arenas().maxHeight()));
            clickSound();
            render();
        });

        set(15, Items.of(session.elytraOnly() ? Material.ELYTRA : Material.FEATHER)
                .name(m.item(session.elytraOnly() ? "gui-template-elytra-on" : "gui-template-elytra-off"))
                .lore(m.lore("gui-template-elytra-lore")).build(), click -> {
            session.elytraOnly(!session.elytraOnly());
            clickSound();
            render();
        });

        set(16, Items.of(Material.CHEST).name(m.item("gui-template-kits"))
                .lore(m.lore("gui-template-kits-lore", tag("kits", session.kits().isEmpty()
                        ? m.plain("gui-template-kits-all") : String.join(", ", session.kits())))).build(), click -> {
            clickSound();
            new KitToggleMenu(plugin, viewer, session).open();
        });

        set(22, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> viewer.closeInventory());
        fillRow(0);
        fillRow(1);
        fillRow(2);
    }
}
