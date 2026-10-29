package at.marcelr.mrpvp.editor;

import org.bukkit.Location;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An arena draft "under construction": a new arena or changes to an existing one. Several admins can build in it at
 * the same time; it stays (in arena-drafts.yml) when everyone leaves, until it is published or discarded. Main
 * thread only.
 */
public final class EditorSession {

    final String id;
    final int plot;
    /** players building in it right now, in the order they joined */
    final Set<UUID> builders = new LinkedHashSet<>();
    String creator = "";
    String lastBuilder = "";
    long created;
    /** last time somebody built in it */
    long changed;
    BlockVector pos1;
    BlockVector pos2;
    Location spawn1;
    Location spawn2;
    String name;
    String icon;
    boolean allowBuild;
    boolean elytraOnly;
    /** relative to the lowest corner; 0 = whole height */
    int maxBuildHeight;
    List<String> kits = new ArrayList<>();
    int pool;
    boolean enabled = true;
    int version;

    EditorSession(String id, int plot) {
        this.id = id;
        this.plot = plot;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String icon() {
        return icon;
    }

    public boolean allowBuild() {
        return allowBuild;
    }

    public int maxBuildHeight() {
        return maxBuildHeight;
    }

    public List<String> kits() {
        return kits;
    }

    public Set<UUID> builders() {
        return Set.copyOf(builders);
    }

    public String creator() {
        return creator;
    }

    public String lastBuilder() {
        return lastBuilder;
    }

    public long changed() {
        return changed;
    }

    public void name(String value) {
        name = value;
    }

    public void icon(String value) {
        icon = value;
    }

    public boolean elytraOnly() {
        return elytraOnly;
    }

    public void elytraOnly(boolean value) {
        elytraOnly = value;
    }

    public void allowBuild(boolean value) {
        allowBuild = value;
    }

    public void maxBuildHeight(int value) {
        maxBuildHeight = value;
    }

    /** Lowest corner of the selection, or null. */
    BlockVector min() {
        if (pos1 == null || pos2 == null) {
            return null;
        }
        return new BlockVector(Math.min(pos1.getBlockX(), pos2.getBlockX()), Math.min(pos1.getBlockY(), pos2.getBlockY()),
                Math.min(pos1.getBlockZ(), pos2.getBlockZ()));
    }

    /** Size of the selection (both corners included), or null. */
    BlockVector size() {
        if (pos1 == null || pos2 == null) {
            return null;
        }
        return new BlockVector(Math.abs(pos1.getBlockX() - pos2.getBlockX()) + 1,
                Math.abs(pos1.getBlockY() - pos2.getBlockY()) + 1, Math.abs(pos1.getBlockZ() - pos2.getBlockZ()) + 1);
    }
}
