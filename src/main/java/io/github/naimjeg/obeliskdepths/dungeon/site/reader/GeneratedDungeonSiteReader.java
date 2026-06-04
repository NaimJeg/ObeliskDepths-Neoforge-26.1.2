package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Predicate;

/*
 * Reads dungeon site metadata from actual generated vanilla structure data.
 *
 * This is the authoritative bridge between worldgen and runtime:
 *
 *   chunk STRUCTURE_STARTS
 *     -> StructureStart
 *     -> ObeliskDungeonPiece metadata
 *     -> DungeonSite
 *
 * This class must not fabricate planned rooms or bounds. If no valid
 * StructureStart exists, it returns Optional.empty().
 */
public final class GeneratedDungeonSiteReader {
    private GeneratedDungeonSiteReader() {
    }

    public static Optional<DungeonSite> findNearestGeneratedSite(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return DungeonStructureLocator.findNearestStart(
                        level,
                        origin,
                        canUseSite
                )
                .flatMap(key -> readGeneratedSite(level, key));
    }

    public static Optional<DungeonSite> readGeneratedSite(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        return DungeonStructureStartReader.read(level, key)
                .map(start -> GeneratedDungeonSiteProjector.project(key, start));
    }
}