package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Messages;
import at.marcelr.mrpvp.storage.Database;
import at.marcelr.mrpvp.util.TitleWidth;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class of all MRpvp menus. The menu is its own {@link InventoryHolder}, which is how
 * {@link MenuListener} recognises it. {@link #render()} rebuilds all items and is called again
 * periodically, so the menu shows live data without being reopened.
 */
public abstract class Menu implements InventoryHolder {

    protected final MRPvp plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    protected Menu(MRPvp plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, fit(title));
    }

    /**
     * Keeps the title inside the window: Minecraft does not cut titles off, a long one (e.g. with a long loader or
     * player name) runs out of the box. Too long titles are shortened with "…" in the colour of their first part.
     */
    static Component fit(Component title) {
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (TitleWidth.width(plain) <= TitleWidth.MAX) {
            return title;
        }
        return Component.text(TitleWidth.fit(plain, TitleWidth.MAX), title.style());
    }

    /** Fills the inventory. Implementations call {@link #set} for every slot they use. */
    protected abstract void draw();

    public final void render() {
        actions.clear();
        inventory.clear();
        draw();
    }

    public final void open() {
        render();
        viewer.openInventory(inventory);
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    /** An item (may be null for an empty but clickable slot) with what a click does. */
    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    /** Fills all empty slots of a row with the decorative pane. */
    protected void fillRow(int row) {
        for (int slot = row * 9; slot < row * 9 + 9; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, Items.filler());
            }
        }
    }

    final void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(type);
        }
    }

    /**
     * Loads data on the storage thread and hands it to {@code done} on the main thread; the menu is drawn again if it
     * is still open. Menus show a "loading" item until then.
     */
    protected <T> void load(Database.Task<T> task, Consumer<T> done) {
        plugin.database().submit(task).whenComplete((value, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    plugin.messages().send(viewer, "database-error", Messages.tag("error", String.valueOf(error.getMessage())));
                    return;
                }
                done.accept(value);
                if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                    plugin.messages().run(viewer, this::render);
                }
            });
        });
    }

    /** Placeholder while {@link #load} is running. */
    protected ItemStack loading() {
        return Items.of(org.bukkit.Material.CLOCK).name(plugin.messages().item("gui-loading")).build();
    }

    /** Click into the viewer's own inventory below the menu (cancelled like every click). Does nothing by default. */
    protected void clickBottom(int slot, ClickType type) {
    }

    /** Runs {@code action} if the viewer has {@code permission}, otherwise tells them and plays the deny sound. */
    protected boolean allowed(String permission) {
        if (viewer.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(viewer, "no-permission");
        sound(Sound.ENTITY_VILLAGER_NO);
        return false;
    }

    /**
     * A team area the viewer has no permission for: it stays in its place as a grey lock, so the menu keeps its shape
     * (every team member sees the same symmetric menu). {@code name} is the area's normal name.
     */
    protected void locked(int slot, net.kyori.adventure.text.Component name) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        set(slot, Items.of(org.bukkit.Material.IRON_BARS)
                .name(plugin.messages().item("gui-locked-name", at.marcelr.mrpvp.Messages.tag("name", plain)))
                .lore(plugin.messages().lore("gui-locked-lore")).build(), click -> sound(Sound.ENTITY_VILLAGER_NO));
    }

    protected void sound(Sound sound) {
        if (plugin.settings().gui().sounds()) {
            viewer.playSound(viewer.getLocation(), sound, 0.6f, 1.2f);
        }
    }

    protected void clickSound() {
        sound(Sound.UI_BUTTON_CLICK);
    }

    protected void successSound() {
        sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }
}
