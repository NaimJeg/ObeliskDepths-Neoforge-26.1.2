package io.github.naimjeg.obeliskdepths.dungeon.instance;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
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

/*
 * ARCHITECTURAL INVARIANT — NO RUNTIME DUNGEON GENERATION
 *
 * Physical dungeon geometry is produced exclusively by Minecraft's
 * structure/chunk world-generation pipeline.
 *
 * Runtime code may discover, validate, reserve, load, and enter an already
 * generated dungeon site. Runtime code must never create a physical site plan,
 * place or repair dungeon blocks, materialize structure pieces, or promote a
 * planned prototype to authoritative generated-site metadata.
 *
 * Loading an already-generated chunk is allowed. Writing dungeon geometry
 * because of portal use, reservation, teleportation, session creation, room
 * entry, or chunk loading is strictly forbidden.
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
                WorldgenDungeonSiteLocator.findNearestReservableSite(
                        dungeonLevel,
                        origin,
                        data::isSiteUnreached
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
