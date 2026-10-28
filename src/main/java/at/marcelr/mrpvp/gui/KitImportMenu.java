package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.kit.KitTransfer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static at.marcelr.mrpvp.Messages.tag;

/**
 * Importing kit files (plugins/MRpvp/imports/*.json, e.g. exported on another server): first the file, then a preview
 * of its kits - new or already there (then renamed to "&lt;id&gt;_2" or, if switched on, replaced), arenas this server
 * does not have (left out), items that could not be read (left out). Only the ticked kits are imported.
 */
final class KitImportMenu extends Menu {

    private File file;
    private KitTransfer.Contents contents;
    private final Set<String> selected = new HashSet<>();
    private boolean replace;

    KitImportMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-kitimport-title"));
    }

    static File importFolder(MRPvp plugin) {
        return new File(plugin.getDataFolder(), "imports");
    }

    static File exportFolder(MRPvp plugin) {
        return new File(plugin.getDataFolder(), "exports");
    }

    @Override
    protected void draw() {
        if (contents == null) {
            files();
        } else {
            kits();
        }
        fillRow(5);
    }

    // ------------------------------------------------------------------ choose the file

    private void files() {
        var m = plugin.messages();
        File folder = importFolder(plugin);
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }
        File[] found = folder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".json"));
        List<File> list = found == null ? List.of()
                : Arrays.stream(found).sorted(Comparator.comparingLong(File::lastModified).reversed()).toList();
        for (int i = 0; i < list.size() && i < 45; i++) {
            File f = list.get(i);
            set(i, Items.of(Material.PAPER).name(Component.text(f.getName()).decoration(TextDecoration.ITALIC, false))
                    .lore(m.lore("gui-kitimport-file-lore", tag("size", Math.max(1, f.length() / 1024)))).build(), click -> {
                clickSound();
                load(f);
            });
        }
        if (list.isEmpty()) {
            set(22, Items.of(Material.BARRIER).name(m.item("gui-kitimport-none"))
                    .lore(m.lore("gui-kitimport-none-lore", tag("folder", "plugins/MRpvp/imports/"))).build());
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new KitListMenu(plugin, viewer).open();
        });
    }

    private void load(File f) {
        try {
            String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            contents = KitTransfer.read(text, id -> plugin.templates().get(id) != null);
            file = f;
            selected.clear();
            contents.kits().forEach(e -> selected.add(e.kit().id()));
            render();
        } catch (IOException | IllegalArgumentException e) {
            plugin.messages().send(viewer, "kit-import-invalid", tag("file", f.getName()));
        }
    }

    // ------------------------------------------------------------------ preview

    private void kits() {
        var m = plugin.messages();
        List<KitTransfer.Entry> entries = contents.kits();
        for (int i = 0; i < entries.size() && i < 45; i++) {
            KitTransfer.Entry entry = entries.get(i);
            Kit kit = entry.kit();
            boolean on = selected.contains(kit.id());
            boolean exists = plugin.kits().get(kit.id()) != null;
            List<Component> lore = new ArrayList<>();
            lore.add(m.item(!exists ? "gui-kitimport-new" : replace ? "gui-kitimport-replace"
                    : "gui-kitimport-rename", tag("id", target(kit.id()))));
            lore.add(m.item("gui-kitimport-items", tag("count", kit.items().size())));
            if (!entry.unknownArenas().isEmpty()) {
                lore.add(m.item("gui-kitimport-arenas", tag("arenas", String.join(", ", entry.unknownArenas()))));
            }
            if (entry.brokenItems() > 0) {
                lore.add(m.item("gui-kitimport-broken", tag("count", entry.brokenItems())));
            }
            lore.add(m.item(on ? "gui-kit-toggle-on" : "gui-kit-toggle-off"));
            set(i, Items.of(kit.icon()).flags().glint(on).name(m.miniItem(kit.name())).lore(lore).build(), click -> {
                if (!selected.remove(kit.id())) {
                    selected.add(kit.id());
                }
                clickSound();
                render();
            });
        }
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            contents = null;
            clickSound();
            render();
        });
        boolean otherVersion = !contents.minecraft().isEmpty() && !contents.minecraft().equals(Bukkit.getMinecraftVersion());
        set(47, Items.of(otherVersion ? Material.YELLOW_CONCRETE : Material.PAPER).name(Component.text(file.getName())
                .decoration(TextDecoration.ITALIC, false)).lore(m.lore(otherVersion ? "gui-kitimport-version-other"
                : "gui-kitimport-version", tag("version", contents.minecraft().isEmpty() ? "?" : contents.minecraft()))).build());
        set(49, Items.of(replace ? Material.LAVA_BUCKET : Material.WATER_BUCKET)
                .name(m.item(replace ? "gui-kitimport-mode-replace" : "gui-kitimport-mode-rename"))
                .lore(m.lore("gui-kitimport-mode-lore")).build(), click -> {
            replace = !replace;
            clickSound();
            render();
        });
        set(53, Items.of(Material.LIME_CONCRETE).name(m.item("gui-kitimport-start", tag("count", selected.size()))).build(),
                click -> start());
    }

    /** The id the kit gets here: its own, or with replace off and the id taken the next free "<id>_2", "<id>_3" ... */
    private String target(String id) {
        if (replace || plugin.kits().get(id) == null) {
            return id;
        }
        for (int n = 2; ; n++) {
            String candidate = (id.length() > 29 ? id.substring(0, 29) : id) + "_" + n;
            if (plugin.kits().get(candidate) == null) {
                return candidate;
            }
        }
    }

    private void start() {
        var m = plugin.messages();
        int imported = 0;
        for (KitTransfer.Entry entry : contents.kits()) {
            Kit kit = entry.kit();
            if (!selected.contains(kit.id()) || (kit.items().isEmpty() && kit.rules().isDefault())) {
                continue;
            }
            String id = target(kit.id());
            try {
                plugin.kits().save(new Kit(id, kit.names(), kit.icon(), kit.descriptions(), kit.permission(), kit.rules(),
                        kit.items()));
                imported++;
            } catch (IOException e) {
                m.send(viewer, "editor-error-io", tag("error", e.getMessage()));
                return;
            }
        }
        plugin.getLogger().info(viewer.getName() + " imported " + imported + " kit(s) from " + file.getName() + ".");
        m.send(viewer, "kit-imported", tag("count", imported), tag("file", file.getName()));
        successSound();
        new KitListMenu(plugin, viewer).open();
    }

    /** Writes all kits into plugins/MRpvp/exports/; returns the file name, or null if it failed. */
    static String exportAll(MRPvp plugin) {
        File folder = exportFolder(plugin);
        String name = "mrpvp-kits-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd-HHmm")) + ".json";
        try {
            Files.createDirectories(folder.toPath());
            Files.writeString(new File(folder, name).toPath(), KitTransfer.write(plugin.kits().all()), StandardCharsets.UTF_8);
            return name;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not export the kits: " + e.getMessage());
            return null;
        }
    }
}
