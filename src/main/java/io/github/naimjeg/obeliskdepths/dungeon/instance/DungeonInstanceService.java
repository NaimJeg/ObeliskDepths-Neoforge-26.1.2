package io.github.naimjeg.obeliskdepths.dungeon.instance;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.WorldgenDungeonSiteLocator;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

public final class DungeonInstanceService {
    private DungeonInstanceService() {
    }

    /*
     * Authoritative site reservation model:
     *
     * Runtime reserves an unreached vanilla-generated dungeon site and creates
     * instance state for it. It does not generate terrain or accept planned
     * prototype projections as real dungeon metadata.
     *
     * Worldgen owns physical geometry: rooms, corridors, flat debug planes today,
     * and later .nbt template-backed pieces. Runtime only binds to the projected
     * DungeonSite contract: site key, bounds, start position, generated room
     * ids/types/bounds/anchors, and future connector metadata.
     */
    public static Optional<DungeonInstance> reserveNearestUnreachedWorldgenSite(
            ServerLevel dungeonLevel,
            BlockPos origin,
            DungeonDifficulty difficulty
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        Optional<ResolvedDungeonSite> resolved =
                WorldgenDungeonSiteLocator.findNearestReservableSite(
                        dungeonLevel,
                        origin,
                        data::isSiteUnreached
                );

        if (resolved.isEmpty()) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "No authoritative dungeon site found near origin={} in level={}. " +
                            "Check structure JSON, biome tag, placement, generated chunk/structure-start lookup, " +
                            "or use debug commands to enter/warm the ObeliskDepths dimension. " +
                            "Runtime will not fallback to prototype metadata.",
                    origin,
                    dungeonLevel.dimension().identifier()
            );

            return Optional.empty();
        }

        if (!resolved.get().authoritative()) {
            throw new IllegalStateException(
                    "Runtime dungeon reservation requires authoritative worldgen site metadata."
            );
        }

        DungeonSite site = resolved.get().site();

        DungeonInstance instance = data.reserveSiteForNewInstance(
                difficulty,
                site,
                dungeonLevel.getGameTime()
        );

        DungeonSiteProjectionCache.putAuthoritative(
                dungeonLevel,
                site,
                resolved.get().source()
        );

        return Optional.of(instance);
    }

    public static boolean releaseFailedReservation(
            ServerLevel dungeonLevel,
            DungeonInstanceId id
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .releaseFailedReservation(id);
    }

    public static boolean retireRuntimeInstance(
            ServerLevel dungeonLevel,
            DungeonInstanceId id,
            DungeonSiteUsageStatus finalStatus
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .retireRuntimeInstance(
                        id,
                        finalStatus,
                        dungeonLevel.getGameTime()
                );
    }

    public static boolean markCompleted(
            ServerLevel dungeonLevel,
            DungeonInstanceId id
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .setInstanceStatus(id, DungeonStatus.CLEARED);
    }

    public static Optional<DungeonInstance> get(
            ServerLevel dungeonLevel,
            DungeonInstanceId id
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).getInstance(id);
    }

    public static boolean addParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId id,
            UUID playerId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).addParticipant(
                id,
                playerId,
                dungeonLevel.getGameTime()
        );
    }

    public static boolean removeParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId id,
            UUID playerId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).removeParticipant(
                id,
                playerId
        );
    }

    public static boolean setStatus(
            ServerLevel dungeonLevel,
            DungeonInstanceId id,
            DungeonStatus status
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).setInstanceStatus(
                id,
                status
        );
    }

    public static int closeEmptyActiveInstances(ServerLevel dungeonLevel) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .closeEmptyActiveInstances(dungeonLevel.getGameTime());
    }
}
