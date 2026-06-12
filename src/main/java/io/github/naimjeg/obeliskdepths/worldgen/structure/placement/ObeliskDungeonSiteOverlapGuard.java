package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import java.util.Optional;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ObeliskDungeonSiteOverlapGuard {
    private ObeliskDungeonSiteOverlapGuard() {
    }

    public static Optional<Rejection> findRejection(
            long worldSeed,
            ChunkPos currentChunk,
            BoundingBox currentBounds
    ) {
        int radiusBlocks = conservativeRadiusFor(currentBounds);
        int minimumCenterSeparationBlocks = ObeliskDungeonPlacementSettings.SEPARATION * 16;

        if (radiusBlocks * 2 <= minimumCenterSeparationBlocks) {
            ObeliskDepths.LOGGER.debug(
                    "[OD structure] cheap overlap check passed chunk={} radiusBlocks={} minimumCenterSeparationBlocks={}",
                    currentChunk,
                    radiusBlocks,
                    minimumCenterSeparationBlocks
            );
            return Optional.empty();
        }

        ObeliskDepths.LOGGER.warn(
                "[OD structure] conservative dungeon radius {} exceeds placement separation {}; rejecting candidate chunk={} without neighbor layout prediction",
                radiusBlocks,
                minimumCenterSeparationBlocks,
                currentChunk
        );
        return Optional.of(new Rejection(currentChunk, currentBounds));
    }

    public static ChunkPos candidateChunk(
            long seed,
            int regionX,
            int regionZ
    ) {
        /*
         * Match minecraft:random_spread placement.
         *
         * Do not use java.util.Random or a manually mixed seed here. The candidate
         * must be identical to the chunk selected by vanilla StructurePlacement.
         *
         * This implementation assumes the structure-set JSON uses:
         *
         *     "spread_type": "linear"
         */
        WorldgenRandom random =
                new WorldgenRandom(new LegacyRandomSource(0L));

        random.setLargeFeatureWithSalt(
                seed,
                regionX,
                regionZ,
                ObeliskDungeonPlacementSettings.SALT
        );

        int spread =
                ObeliskDungeonPlacementSettings.SPACING
                        - ObeliskDungeonPlacementSettings.SEPARATION;

        int offsetX = random.nextInt(spread);
        int offsetZ = random.nextInt(spread);

        return new ChunkPos(
                regionX * ObeliskDungeonPlacementSettings.SPACING + offsetX,
                regionZ * ObeliskDungeonPlacementSettings.SPACING + offsetZ
        );
    }

    private static int conservativeRadiusFor(BoundingBox currentBounds) {
        int footprintBlocks = Math.max(
                currentBounds.maxX() - currentBounds.minX() + 1,
                currentBounds.maxZ() - currentBounds.minZ() + 1
        );
        return Math.max(
                ObeliskDungeonPlacementSettings.MAX_SITE_RADIUS_BLOCKS,
                (footprintBlocks + 1) / 2
        );
    }

    public record Rejection(
            ChunkPos winnerChunk,
            BoundingBox winnerBounds
    ) {
    }
}
