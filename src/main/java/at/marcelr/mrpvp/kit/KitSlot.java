package at.marcelr.mrpvp.kit;

import java.util.Locale;

/**
 * A place in the player inventory as written in kits.yml: 0-35 (0-8 hotbar), helmet, chestplate, leggings, boots,
 * offhand.
 *
 * @param index 0-35 for the main inventory, -1 for the special slots
 * @param special HELMET ... OFFHAND, or null
 */
public record KitSlot(int index, Special special) {

    public enum Special { HELMET, CHESTPLATE, LEGGINGS, BOOTS, OFFHAND }

    /** Reads "0", "35", "helmet", "OffHand" ...; null if it is none of these. */
    public static KitSlot parse(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        try {
            int index = Integer.parseInt(value);
            return index >= 0 && index < 36 ? new KitSlot(index, null) : null;
        } catch (NumberFormatException e) {
            for (Special special : Special.values()) {
                if (special.name().toLowerCase(Locale.ROOT).equals(value)) {
                    return new KitSlot(-1, special);
                }
            }
            return null;
        }
    }

    @Override
    public String toString() {
        return special == null ? String.valueOf(index) : special.name().toLowerCase(Locale.ROOT);
    }
}
