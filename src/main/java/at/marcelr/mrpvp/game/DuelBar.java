package at.marcelr.mrpvp.game;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The boss bar of players in a round and of spectators: round, score, opponent's health, time left. One bar per
 * player, created on first use and changed in place afterwards. Main thread only.
 */
final class DuelBar {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    void show(Player player, Component text, float progress, BossBar.Color color) {
        float value = Math.clamp(progress, 0f, 1f);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(text, value, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
        } else {
            bar.name(text);
            bar.progress(value);
            bar.color(color);
        }
        // Showing again is harmless; it also brings the bar back after a reconnect.
        player.showBossBar(bar);
    }

    void hide(UUID player) {
        BossBar bar = bars.remove(player);
        Player online = Bukkit.getPlayer(player);
        if (bar != null && online != null) {
            online.hideBossBar(bar);
        }
    }

    void hideAll() {
        for (UUID player : Map.copyOf(bars).keySet()) {
            hide(player);
        }
    }
}
