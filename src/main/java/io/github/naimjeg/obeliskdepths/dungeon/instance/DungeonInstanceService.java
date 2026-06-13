package io.github.naimjeg.obeliskdepths.dungeon.instance;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.*;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/*
 * ARCHITECTURAL INVARIANT — VANILLA WORLDGEN REMAINS AUTHORITATIVE
 *
 * Physical dungeon geometry must be produced exclusively by Minecraft's
 * structure/chunk world-generation pipeline.
 *
 * Runtime allocation may request bounded vanilla chunk generation for a valid
 * structure-placement candidate when no generated site is available.
 *
 * Runtime code must never manually place dungeon blocks, fabricate a
 * StructureStart, fabricate generated room metadata, or promote prototype
 * planning data into an authoritative DungeonSite.
 *
 * After generation, runtime metadata must always be read back from the actual
 * vanilla StructureStart and serialized ObeliskDungeonPiece instances.
 */
public final class DungeonInstanceService {
    private DungeonInstanceService() {
    }

    /*
     * This method reserves an existing generated site only.
     *
     * Do not add planning, template placement, block writes, piece materialization,
     * terrain repair, or fallback generation here. If no suitable generated site
     * exists, allocation must fail without modifying dungeon geometry.
     */
    public static Optional<DungeonInstance> reserveNearestUnreachedWorldgenSite(
            ServerLevel dungeonLevel,
            BlockPos origin,
            DungeonDifficulty difficulty
    ) {
        long startNanos = System.nanoTime();
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        Optional<ResolvedDungeonSite> resolved =
                WorldgenDungeonSiteProvisioner.findOrGenerateReservableSite(
                        dungeonLevel,
                        origin,
                        data
                );

        if (resolved.isEmpty()) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] no generated dungeon site available origin={} elapsedMicros={}",
                    origin,
                    (System.nanoTime() - startNanos) / 1_000L
            );
            return Optional.empty();
        }

        if (!resolved.get().authoritative()) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] rejected non-authoritative site source={} key={}",
                    resolved.get().source(),
                    resolved.get().site().key()
            );
            return Optional.empty();
        }

        DungeonSite site = resolved.get().site();

        if (data.isSiteReserved(site.key())) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] reservation conflict site={}",
                    site.key()
            );
            return Optional.empty();
        }

        Optional<DungeonGeneratedRoom> primaryEntry = site.primaryEntryRoom();

        if (primaryEntry.isEmpty() || !primaryEntry.get().contains(site.startPos())) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] rejected generated site with invalid primary entry site={} start={} source={}",
                    site.key(),
                    site.startPos(),
                    resolved.get().source()
            );
            return Optional.empty();
        }

        DungeonInstance instance = data.reserveSiteForNewInstance(
                difficulty,
                site,
                dungeonLevel.getGameTime()
        );

        DungeonSiteProjectionCache.putAuthoritative(dungeonLevel, resolved.get());

        io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.debug(
                "[OD reservation] reserved generated site={} source={} instance={} elapsedMicros={}",
                site.key(),
                resolved.get().source(),
                instance.id(),
                (System.nanoTime() - startNanos) / 1_000L
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
