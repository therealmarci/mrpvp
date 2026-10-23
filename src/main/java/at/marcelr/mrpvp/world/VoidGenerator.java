package at.marcelr.mrpvp.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Empty world: no terrain, caves, decorations, structures or mobs (all shouldGenerate* stay false), biome
 * "the_void" everywhere. Arenas are pasted into it.
 */
public final class VoidGenerator extends ChunkGenerator {

    private static final BiomeProvider VOID_BIOME = new BiomeProvider() {
        @Override
        public @NonNull Biome getBiome(@NonNull WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public @NonNull List<Biome> getBiomes(@NonNull WorldInfo worldInfo) {
            return List.of(Biome.THE_VOID);
        }
    };

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NonNull WorldInfo worldInfo) {
        return VOID_BIOME;
    }
}
