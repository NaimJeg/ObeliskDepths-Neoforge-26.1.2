package io.github.naimjeg.obeliskdepths.dungeon.site;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;


import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

public final class PrototypeDungeonSitePlanner {
    /*
     * Must match your structure_set JSON for now:
     *
     * spacing: 32
     * separation: 16
     * salt: 91827364
     */
    private static final int SPACING = 32;
    private static final int SEPARATION = 16;
    private static final int SALT = 91827364;

    private static final int SEARCH_REGIONS = 8;

    /*
     * Prototype-only deterministic site planner.
     *
     * This class does not read vanilla StructurePlacement.
     * This class does not read StructureStart.
     * This class does not prove that terrain or pieces exist.
     *
     * Its output may be useful for early debug previews, but it must not be used
     * as the authoritative source for runtime DungeonInstance reservation.
     *
     * Authoritative runtime site metadata must come from:
     *
     *     StructureStart -> ObeliskDungeonPiece -> GeneratedDungeonSiteProjector
     */
    private PrototypeDungeonSitePlanner() {
    }

    public static Optional<WorldgenDungeonSiteCandidate> findNearestCandidate(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

        int originRegionX = Math.floorDiv(originChunkX, SPACING);
        int originRegionZ = Math.floorDiv(originChunkZ, SPACING);

        WorldgenDungeonSiteCandidate best = null;
        double bestDistanceSqr = Double.MAX_VALUE;

        for (int rx = originRegionX - SEARCH_REGIONS; rx <= originRegionX + SEARCH_REGIONS; rx++) {
            for (int rz = originRegionZ - SEARCH_REGIONS; rz <= originRegionZ + SEARCH_REGIONS; rz++) {
                ChunkPos startChunk = candidateChunk(level.getSeed(), rx, rz);
                DungeonSiteKey key = DungeonSiteKey.fromStartChunk(startChunk);

                if (!canUseSite.test(key)) {
                    continue;
                }

                BlockPos startPos = new BlockPos(
                        startChunk.getMiddleBlockX(),
                        DungeonSitePlacement.PROTOTYPE_Y,
                        startChunk.getMiddleBlockZ()
                );

                double distanceSqr = startPos.distSqr(origin);

                if (distanceSqr < bestDistanceSqr) {
                    bestDistanceSqr = distanceSqr;
                    best = new WorldgenDungeonSiteCandidate(
                            key,
                            startPos,
                            (int) distanceSqr
                    );
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static ChunkPos candidateChunk(
            long seed,
            int regionX,
            int regionZ
    ) {
        long mixedSeed = seed;
        mixedSeed ^= (long) regionX * 341873128712L;
        mixedSeed ^= (long) regionZ * 132897987541L;
        mixedSeed ^= SALT;

        Random random = new Random(mixedSeed);

        int spread = SPACING - SEPARATION;

        int offsetX = random.nextInt(spread);
        int offsetZ = random.nextInt(spread);

        return new ChunkPos(
                regionX * SPACING + offsetX,
                regionZ * SPACING + offsetZ
        );
    }
}