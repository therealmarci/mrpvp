package at.marcelr.mrpvp.snapshot;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Takes a player's state ({@link #capture}), clears it for a round ({@link #reset}) and puts it back ({@link #apply}). */
public final class PlayerState {

    public static final int INVENTORY_SLOTS = 36;
    public static final int ARMOR_SLOTS = 4;
    public static final int ENDERCHEST_SLOTS = 27;

    private PlayerState() {
    }

    /**
     * The player's full state right now.
     *
     * @param toolbarBelts MRtoolbar's belts (only recorded), or null
     */
    public static Snapshot capture(Player player, Snapshot.Purpose purpose, Long gameId, Snapshot.Phase phase,
                                   byte[] toolbarBelts) {
        PlayerInventory inv = player.getInventory();
        Location at = player.getLocation();
        return new Snapshot(0, player.getUniqueId(), player.getName(), purpose, gameId, phase,
                System.currentTimeMillis(),
                items(inv.getStorageContents(), INVENTORY_SLOTS),
                items(inv.getArmorContents(), ARMOR_SLOTS),
                items(new ItemStack[] {inv.getItemInOffHand()}, 1),
                items(player.getEnderChest().getStorageContents(), ENDERCHEST_SLOTS),
                toolbarBelts,
                player.getLevel(), player.getExp(), player.getTotalExperience(),
                player.getHealth(), player.getAbsorptionAmount(),
                player.getFoodLevel(), player.getSaturation(), player.getExhaustion(),
                player.getRemainingAir(), player.getFireTicks(),
                effects(player.getActivePotionEffects()),
                player.getGameMode().name(), player.isFlying(), player.getAllowFlight(),
                at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch(),
                null);
    }

    /** Empty inventory, no effects, full health and hunger - the start of a round (or the editor). */
    public static void reset(Player player, GameMode mode) {
        player.closeInventory();
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[ARMOR_SLOTS]);
        inv.setItemInOffHand(null);
        player.setItemOnCursor(null);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(mode);
        player.setHealth(maxHealth(player));
        player.setAbsorptionAmount(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setLevel(0);
        player.setExp(0);
    }

    /**
     * Gives the player the state back: inventory, armor, offhand, XP, health, hunger, effects, game mode and
     * position. The ender chest stays as it is (MRpvp never touches it).
     *
     * @param fallback where to go if the saved world no longer exists
     * @return false if the saved items could not be read (nothing was changed then)
     */
    public static boolean apply(Player player, Snapshot s, Location fallback, Logger log) {
        ItemStack[] inventory;
        ItemStack[] armor;
        ItemStack[] offhand;
        try {
            inventory = fit(ItemStack.deserializeItemsFromBytes(s.inventory()), INVENTORY_SLOTS);
            armor = fit(ItemStack.deserializeItemsFromBytes(s.armor()), ARMOR_SLOTS);
            offhand = fit(ItemStack.deserializeItemsFromBytes(s.offhand()), 1);
        } catch (RuntimeException e) {
            log.severe("Snapshot " + s.id() + " of " + s.name() + " has unreadable items: " + e);
            return false;
        }

        player.closeInventory();
        player.setItemOnCursor(null);
        // Game mode first: switching it can change flight and similar.
        GameMode mode = gameMode(s.gameMode());
        player.setGameMode(mode);
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setStorageContents(inventory);
        inv.setArmorContents(armor);
        inv.setItemInOffHand(offhand[0]);

        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.addPotionEffects(effects(s.effects(), log));

        player.setLevel(Math.max(0, s.level()));
        player.setExp(Math.clamp(s.exp(), 0f, 1f));
        player.setTotalExperience(Math.max(0, s.totalExp()));
        // Max health can come from effects (health boost) - set after them, and never above the maximum.
        player.setHealth(Math.clamp(s.health(), 0.5, maxHealth(player)));
        player.setAbsorptionAmount(Math.max(0, s.absorption()));
        player.setFoodLevel(Math.clamp(s.food(), 0, 20));
        player.setSaturation(Math.max(0, s.saturation()));
        player.setExhaustion(Math.max(0, s.exhaustion()));
        player.setRemainingAir(Math.min(s.air(), player.getMaximumAir()));
        player.setFireTicks(Math.max(0, s.fireTicks()));
        player.setFallDistance(0);

        World world = Bukkit.getWorld(s.world());
        Location target = world == null ? fallback : new Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
        player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setAllowFlight(s.allowFlight());
            player.setFlying(s.allowFlight() && s.flying());
        }
        // Write the player file now: if the server crashes right after this, the player keeps what they got back.
        player.saveData();
        return true;
    }

    public static double maxHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    /** The items as Paper bytes, always exactly {@code size} slots (empty ones included). */
    static byte[] items(ItemStack[] items, int size) {
        return ItemStack.serializeItemsAsBytes(fit(items, size));
    }

    private static ItemStack[] fit(ItemStack[] items, int size) {
        ItemStack[] result = Arrays.copyOf(items, size);
        for (int i = 0; i < size; i++) {
            if (result[i] != null && result[i].isEmpty()) {
                result[i] = null;
            }
        }
        return result;
    }

    private static GameMode gameMode(String name) {
        try {
            return GameMode.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    static String effects(Iterable<PotionEffect> effects) {
        List<PotionEffect> list = new ArrayList<>();
        effects.forEach(list::add);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("effects", list);
        return yaml.saveToString();
    }

    static List<PotionEffect> effects(String text, Logger log) {
        List<PotionEffect> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            log.warning("Unreadable potion effects in a snapshot: " + e.getMessage());
            return result;
        }
        List<?> list = yaml.getList("effects");
        if (list != null) {
            for (Object entry : list) {
                if (entry instanceof PotionEffect effect) {
                    result.add(effect);
                }
            }
        }
        return result;
    }
}
