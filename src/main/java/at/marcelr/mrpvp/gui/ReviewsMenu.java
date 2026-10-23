package at.marcelr.mrpvp.gui;

import at.marcelr.mrpvp.MRPvp;
import at.marcelr.mrpvp.game.Reviews;
import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.stats.StatsStore;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

import static at.marcelr.mrpvp.Messages.tag;

/** Conspicuous rounds waiting for a decision, newest first; click opens the round with "count" / "discard". */
public final class ReviewsMenu extends Menu {

    private List<StatsStore.Pending> pending;

    public ReviewsMenu(MRPvp plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-reviews-title"));
        load(c -> StatsStore.pending(c, 45), list -> pending = list);
    }

    @Override
    protected void draw() {
        var m = plugin.messages();
        set(45, Items.of(Material.ARROW).name(m.item("gui-back")).build(), click -> {
            clickSound();
            new AdminMenu(plugin, viewer).open();
        });
        set(49, Items.of(Material.BOOK).name(m.item("gui-reviews-info"))
                .lore(m.lore("gui-reviews-info-lore", tag("days", plugin.settings().review().autoDays()),
                        tag("decision", m.plain(plugin.settings().review().autoCount() == null ? "review-auto-none"
                                : plugin.settings().review().autoCount() ? "review-auto-count" : "review-auto-discard"))))
                .build());
        fillRow(5);
        if (pending == null) {
            set(22, loading());
            return;
        }
        if (pending.isEmpty()) {
            set(22, Items.of(Material.LIME_DYE).name(m.item("gui-reviews-empty")).build());
        }
        for (int i = 0; i < pending.size() && i < 45; i++) {
            StatsStore.Pending p = pending.get(i);
            Kit kit = plugin.kits().get(p.kit());
            String result = String.valueOf(p.result()).toLowerCase(Locale.ROOT);
            String winner = p.winner() == null ? "-" : p.winner().equals(p.one()) ? p.oneName() : p.twoName();
            set(i, Items.of(kit == null ? Material.PAPER : kit.icon()).flags()
                    .name(m.item("gui-reviews-entry", tag("id", p.id()), tag("one", p.oneName()), tag("two", p.twoName())))
                    .lore(m.lore("gui-reviews-entry-lore", tag("flags", plugin.reviews().flagNames(Reviews.flags(p.flags()))),
                            tag("result", m.get("outcome-" + result)), tag("winner", winner),
                            tag("reason", m.plain("end-reason-" + String.valueOf(p.reason()).toLowerCase(Locale.ROOT))),
                            tag("date", m.dateTime(p.endedAt())))).build(), click -> {
                clickSound();
                new GameMenu(plugin, viewer, p.id(), true, () -> new ReviewsMenu(plugin, viewer).open()).open();
            });
        }
    }
}
