package at.marcelr.mrpvp.editor;

import org.bukkit.Location;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An admin building or changing an arena template. Main thread only. */
public final class EditorSession {

    final UUID player;
    final String id;
    /** never saved yet */
    boolean isNew;
    final int plot;
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

    EditorSession(UUID player, String id, boolean isNew, int plot) {
        this.player = player;
        this.id = id;
        this.isNew = isNew;
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
