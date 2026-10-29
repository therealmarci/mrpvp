package at.marcelr.mrpvp.snapshot;

import java.util.UUID;

/**
 * Everything about a player at one moment, as plain data (items already as Paper bytes). Built by
 * {@link PlayerState#capture}, stored by {@link SnapshotStore}, put back by {@link PlayerState#apply}.
 *
 * @param id           database id, 0 before it is stored
 * @param gameId       Game-ID for {@link Purpose#GAME}, otherwise null
 * @param inventory    the 36 main slots (hotbar 0-8, then the rest) - {@code ItemStack.serializeItemsAsBytes}
 * @param armor        4 slots: boots, leggings, chestplate, helmet
 * @param offhand      1 slot
 * @param enderchest   27 slots - only recorded, never changed by MRpvp
 * @param toolbarBelts MRtoolbar's belts as handed out by its API, or null without MRtoolbar
 * @param effects      potion effects as YAML text
 * @param restoredAt   when the player got this state back (epoch ms), null = still owed
 */
public record Snapshot(
        long id,
        UUID uuid,
        String name,
        Purpose purpose,
        Long gameId,
        Phase phase,
        long takenAt,
        byte[] inventory,
        byte[] armor,
        byte[] offhand,
        byte[] enderchest,
        byte[] toolbarBelts,
        int level,
        float exp,
        int totalExp,
        double health,
        double absorption,
        int food,
        float saturation,
        float exhaustion,
        int air,
        int fireTicks,
        String effects,
        String gameMode,
        boolean flying,
        boolean allowFlight,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        Long restoredAt
) {

    /** Why the state was taken. */
    public enum Purpose {
        /** a duel round */
        GAME,
        /** an admin in the arena editor */
        EDITOR,
        /** /mrpvp debug arena */
        DEBUG,
        /** an admin editing the items of a kit in their inventory */
        KIT,
        /** an admin watching a round */
        SPECTATE
    }

    public enum Phase {
        /** before entering - this is what the player gets back */
        JOIN,
        /** when the round ended - only for looking at */
        END
    }

    /** Same data with the database id. */
    public Snapshot withId(long newId) {
        return new Snapshot(newId, uuid, name, purpose, gameId, phase, takenAt, inventory, armor, offhand, enderchest,
                toolbarBelts, level, exp, totalExp, health, absorption, food, saturation, exhaustion, air, fireTicks,
                effects, gameMode, flying, allowFlight, world, x, y, z, yaw, pitch, restoredAt);
    }

    /** Whether the player still has to get this state back. */
    public boolean owed() {
        return phase == Phase.JOIN && restoredAt == null;
    }
}
