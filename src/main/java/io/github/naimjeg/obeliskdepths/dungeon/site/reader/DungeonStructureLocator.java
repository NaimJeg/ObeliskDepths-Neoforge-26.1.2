package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.function.Predicate;

public final class DungeonStructureLocator {
    private static final int LOADED_SEARCH_RADIUS_CHUNKS = 16;
    private static final int GENERATED_SEARCH_RADIUS_CHUNKS = 64;

    private DungeonStructureLocator() {
    }

    public static Optional<DungeonSiteKey> findNearestStart(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        Optional<DungeonSiteKey> loadedResult = findNearestStart(
                level,
                origin,
                canUseSite,
                LOADED_SEARCH_RADIUS_CHUNKS,
                false
        );

        if (loadedResult.isPresent()) {
            return loadedResult;
        }

        return findNearestStart(
                level,
                origin,
                canUseSite,
                GENERATED_SEARCH_RADIUS_CHUNKS,
                true
        );
    }

    private static Optional<DungeonSiteKey> findNearestStart(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite,
            int radiusChunks,
            boolean generateStructureStarts
    ) {
        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

        DungeonSiteKey bestKey = null;
        double bestDistanceSqr = Double.MAX_VALUE;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                DungeonSiteKey key = new DungeonSiteKey(
                        originChunkX + dx,
                        originChunkZ + dz
                );

                if (!canUseSite.test(key)) {
                    continue;
                }

                boolean hasStart = generateStructureStarts
                        ? DungeonStructureStartReader.readOrGenerate(level, key).isPresent()
                        : DungeonStructureStartReader.read(level, key).isPresent();

                if (!hasStart) {
                    continue;
                }

                ChunkPos chunkPos = key.toChunkPos();

                BlockPos candidate = new BlockPos(
                        chunkPos.getMiddleBlockX(),
                        origin.getY(),
                        chunkPos.getMiddleBlockZ()
                );

                double distanceSqr = candidate.distSqr(origin);

                if (distanceSqr < bestDistanceSqr) {
                    bestDistanceSqr = distanceSqr;
                    bestKey = key;
                }
            }
        }

        return Optional.ofNullable(bestKey);
    }
}