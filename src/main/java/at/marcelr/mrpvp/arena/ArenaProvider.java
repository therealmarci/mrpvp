package at.marcelr.mrpvp.arena;

/** Hands out arenas for rounds and takes them back. */
public interface ArenaProvider {

    /**
     * A free arena, or null if none is free right now.
     *
     * @param template wanted template, or null for any (random)
     * @param kit      the round's kit - only arenas that allow it
     */
    ArenaInstance acquire(String template, String kit);

    /** The round is over - the arena can be reset and used again. */
    void release(ArenaInstance arena);

    /** Arenas in use right now. */
    int inUse();
}
