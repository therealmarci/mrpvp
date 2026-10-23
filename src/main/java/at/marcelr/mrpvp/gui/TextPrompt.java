package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** A native Minecraft dialog with one text field - for names and the like (no typing into the chat). */
public final class TextPrompt {

    private static final String FIELD = "value";
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(30)).build();

    private TextPrompt() {
    }

    /**
     * @param key    language keys {@code <key>-title}, {@code <key>-hint} and {@code <key>-label}
     * @param ok     gets the entered text (trimmed) on the main thread
     * @param cancel runs on "Cancel" (null = just close)
     */
    public static void ask(MRPvp plugin, Player viewer, String key, String initial, int maxLength,
                           Consumer<String> ok, Runnable cancel) {
        Messages m = plugin.messages();
        m.run(viewer, () -> {
            String start = initial == null ? "" : initial.length() > maxLength ? initial.substring(0, maxLength) : initial;
            DialogAction confirm = action(plugin, viewer, response -> {
                String value = response.getText(FIELD);
                ok.accept(value == null ? "" : value.trim());
            });
            DialogAction back = cancel == null ? null : action(plugin, viewer, response -> cancel.run());
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(m.get(key + "-title"))
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(m.get(key + "-hint"), 300)))
                            .inputs(List.of(DialogInput.text(FIELD, m.get(key + "-label"))
                                    .width(260).maxLength(maxLength).initial(start).build()))
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.create(m.get("prompt-ok"), null, 120, confirm),
                            ActionButton.create(m.get("prompt-cancel"), null, 120, back))));
            viewer.closeInventory();
            viewer.showDialog(dialog);
        });
    }

    private static DialogAction action(MRPvp plugin, Player viewer, Consumer<DialogResponseView> run) {
        return DialogAction.customClick((response, audience) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                plugin.messages().run(viewer, () -> run.accept(response));
            }
        }), ONCE);
    }
}
