package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonSiteOverlapGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class DungeonStructureLocator {
    private static final int MAX_CANDIDATE_ATTEMPTS =
            ObeliskDungeonPlacementSettings.MAX_LOOKUP_CANDIDATES;

    private DungeonStructureLocator() {
    }

    public static Optional<DungeonSiteKey> findNearestStart(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return findNearestStarts(
                level,
                origin,
                canUseSite
        ).stream().findFirst();
    }

    public static List<DungeonSiteKey> findNearestStarts(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return findNearestStartCandidates(level, origin, canUseSite);
    }

    private static List<DungeonSiteKey> findNearestStartCandidates(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        long startNanos = System.nanoTime();
        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        int originRegionX = Math.floorDiv(originChunkX, ObeliskDungeonPlacementSettings.SPACING);
        int originRegionZ = Math.floorDiv(originChunkZ, ObeliskDungeonPlacementSettings.SPACING);
        List<ChunkPos> candidates = nearestCandidateChunks(
                level.getSeed(),
                origin,
                originRegionX,
                originRegionZ,
                MAX_CANDIDATE_ATTEMPTS
        );

        List<DungeonSiteKey> found = new ArrayList<>();
        int attempts = 0;
        int rejectedUnavailable = 0;
        int rejectedPredicate = 0;

        for (ChunkPos chunkPos : candidates) {
            DungeonSiteKey key = new DungeonSiteKey(chunkPos.x(), chunkPos.z());
            attempts++;

            if (!canUseSite.test(key)) {
                rejectedPredicate++;
                ObeliskDepths.LOGGER.debug(
                        "[OD locator] rejected candidate reason=unusable_or_reserved key={} chunk={}",
                        key,
                        chunkPos
                );
                continue;
            }

            Optional<?> start = DungeonStructureStartReader.read(level, key);

            if (start.isEmpty()) {
                rejectedUnavailable++;
                ObeliskDepths.LOGGER.debug(
                        "[OD locator] rejected candidate reason=no_loaded_generated_structure key={} chunk={}",
                        key,
                        chunkPos
                );
                continue;
            }

            found.add(key);
        }

        ObeliskDepths.LOGGER.debug(
                "[OD locator] candidate lookup origin={} candidateCount={} attempts={} generatedChunkAttempts=0 rejectedUnavailable={} rejectedPredicate={} found={} elapsedMicros={}",
                origin,
                candidates.size(),
                attempts,
                rejectedUnavailable,
                rejectedPredicate,
                found.size(),
                (System.nanoTime() - startNanos) / 1_000L
        );

        return List.copyOf(found);
    }

    private static List<ChunkPos> nearestCandidateChunks(
            long worldSeed,
            BlockPos origin,
            int originRegionX,
            int originRegionZ,
            int limit
    ) {
        int regionRadius = 0;
        List<ChunkPos> candidates = new ArrayList<>();

        while (candidates.size() < limit) {
            for (int dx = -regionRadius; dx <= regionRadius; dx++) {
                for (int dz = -regionRadius; dz <= regionRadius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != regionRadius) {
                        continue;
                    }

                    candidates.add(ObeliskDungeonSiteOverlapGuard.candidateChunk(
                            worldSeed,
                            originRegionX + dx,
                            originRegionZ + dz
                    ));
                }
            }

            regionRadius++;
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((ChunkPos chunk) -> candidateCenter(chunk, origin).distSqr(origin))
                        .thenComparingInt(ChunkPos::x)
                        .thenComparingInt(ChunkPos::z))
                .limit(limit)
                .toList();
    }

    private static BlockPos candidateCenter(
            ChunkPos chunk,
            BlockPos origin
    ) {
        return new BlockPos(
                chunk.getMiddleBlockX(),
                origin.getY(),
                chunk.getMiddleBlockZ()
        );
    }
}
