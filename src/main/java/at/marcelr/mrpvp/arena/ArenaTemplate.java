package at.marcelr.mrpvp.arena;

import at.marcelr.mrpvp.kit.Kit;
import at.marcelr.mrpvp.kit.KitRules;

import java.util.List;

/**
 * An arena template: templates/&lt;id&gt;.nbt (blocks) + templates/&lt;id&gt;.yml (this).
 *
 * @param version        raised on every save - slots with an older version are pasted again
 * @param sizeX          size of the structure (and the arena region)
 * @param spawn1         spawn of the inviting player, relative to the lowest corner
 * @param allowBuild     players may place and break blocks (survival instead of adventure)
 * @param maxBuildHeight blocks above the lowest corner up to which building is allowed
 * @param kits           allowed kits, empty = all
 * @param elytraOnly     only kits that bring an elytra (big sky arenas)
 * @param pool           slots kept pasted and ready for this template
 * @param editorPlot     plot in the editor area where the source build stands
 * @param editorX        lowest corner of the build in the editor (absolute)
 */
public record ArenaTemplate(String id, String name, String icon, int version, int sizeX, int sizeY, int sizeZ,
                            Spawn spawn1, Spawn spawn2, boolean allowBuild, int maxBuildHeight, List<String> kits,
                            boolean elytraOnly, boolean enabled, int pool, int editorPlot, int editorX, int editorY, int editorZ) {

    /** A spawn point relative to the lowest corner of the arena. */
    public record Spawn(double x, double y, double z, float yaw, float pitch) {
    }

    public boolean allowsKit(String kit) {
        return kits.isEmpty() || kits.contains(kit);
    }

    /** Arena and kit go together: the arena allows the kit, and the kit's own rules allow the arena. */
    public boolean fits(Kit kit) {
        if (kit == null) {
            return true;
        }
        KitRules rules = kit.rules();
        return allowsKit(kit.id()) && (!elytraOnly || kit.hasElytra())
                && (rules.arenas().isEmpty() || rules.arenas().contains(id))
                && (!rules.build() || allowBuild);
    }

    public ArenaTemplate withEnabled(boolean value) {
        return new ArenaTemplate(id, name, icon, version, sizeX, sizeY, sizeZ, spawn1, spawn2, allowBuild,
                maxBuildHeight, kits, elytraOnly, value, pool, editorPlot, editorX, editorY, editorZ);
    }

    public ArenaTemplate withPool(int value) {
        return new ArenaTemplate(id, name, icon, version, sizeX, sizeY, sizeZ, spawn1, spawn2, allowBuild,
                maxBuildHeight, kits, elytraOnly, enabled, value, editorPlot, editorX, editorY, editorZ);
    }
}
