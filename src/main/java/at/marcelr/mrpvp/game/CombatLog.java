package at.marcelr.mrpvp.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What happened in a round, collected on the main thread and written to the database in batches
 * ({@link #drain}) - so a hit never waits for the database.
 */
final class CombatLog {

    /** Event types - stored as text, never rename. */
    enum Type {
        /** melee or projectile damage between the two */
        HIT,
        /** other damage (fall, fire, explosion ...) */
        DAMAGE,
        /** bow, crossbow, trident shot */
        SHOT,
        PEARL,
        TOTEM,
        /** tried to leave the arena sideways */
        BORDER,
        /** the round was decided */
        END
    }

    /**
     * @param t ms since the fight started
     */
    record Event(long t, Type type, UUID actor, UUID target, Double damage, String cause, String weapon,
                 double x, double y, double z) {
    }

    record Position(long t, int side, double x, double y, double z, float yaw, double health) {
    }

    private List<Event> events = new ArrayList<>();
    private List<Position> positions = new ArrayList<>();

    void add(Event event) {
        events.add(event);
    }

    void add(Position position) {
        positions.add(position);
    }

    /** Hands over what was collected since the last call (for writing) and starts new lists. */
    Batch drain() {
        Batch batch = new Batch(events, positions);
        events = new ArrayList<>();
        positions = new ArrayList<>();
        return batch;
    }

    record Batch(List<Event> events, List<Position> positions) {
        boolean isEmpty() {
            return events.isEmpty() && positions.isEmpty();
        }
    }
}
