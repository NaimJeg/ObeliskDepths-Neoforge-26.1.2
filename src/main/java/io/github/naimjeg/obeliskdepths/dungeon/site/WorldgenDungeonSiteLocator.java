package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.site.reader.GeneratedDungeonSiteReader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Predicate;

public final class WorldgenDungeonSiteLocator {
    private WorldgenDungeonSiteLocator() {
    }

    /*
     * Authoritative runtime lookup.
     *
     * This method is used by DungeonInstanceService when creating a real
     * DungeonInstance. Therefore it must not fabricate site metadata.
     *
     * Valid source:
     *   vanilla StructureStart + serialized ObeliskDungeonPiece metadata
     *
     * Invalid source:
     *   deterministic prototype planner
     *   guessed room layout
     *   guessed bounds
     */
    public static Optional<ResolvedDungeonSite> findNearestReservableSite(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canReserveSite
    ) {
        return GeneratedDungeonSiteReader.findNearestGeneratedSite(
                level,
                origin,
                canReserveSite
        ).map(site -> new ResolvedDungeonSite(
                site,
                DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
        ));
    }

    /*
     * Debug/preview lookup only.
     *
     * This is allowed to use planned prototype metadata because it is not used
     * to create a real runtime instance.
     */
    public static Optional<ResolvedDungeonSite> findNearestPrototypeSiteForDebug(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return PrototypeDungeonSitePlanner.findNearestCandidate(
                level,
                origin,
                canUseSite
        ).map(candidate -> new ResolvedDungeonSite(
                PlannedDungeonSiteProjector.project(candidate),
                DungeonSiteProjectionSource.PLANNED_PROTOTYPE
        ));
    }

    /*
     * Read a known site by key.
     *
     * This should not fallback to planned projection. If the caller needs a
     * prototype fallback, it should call the debug method explicitly.
     */
    public static Optional<ResolvedDungeonSite> readKnownAuthoritativeSite(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        return DungeonSiteProjectionCache.read(level, key);
    }
}