package io.github.naimjeg.obeliskdepths.dungeon.presence;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.spatial.DungeonSpatialIndex;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonPhysicalPresenceService {
    private DungeonPhysicalPresenceService() {
    }

    public static Optional<DungeonInstanceId> findCurrentPhysicalInstance(
            ServerLevel dungeonLevel,
            ServerPlayer player
    ) {
        if (!dungeonLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return Optional.empty();
        }

        if (!player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return Optional.empty();
        }

        /*
         * DungeonSpatialIndex remains a physical world-space lookup only. Player
         * membership and encounter participation are recorded by session runtime
         * state, not owned by the spatial index.
         */
        return DungeonSpatialIndex.findPhysicalOwnerAt(
                dungeonLevel,
                player.blockPosition()
        );
    }

    public static void tickPlayerPhysicalPresence(
            ServerLevel dungeonLevel,
            ServerPlayer player
    ) {
        findCurrentPhysicalInstance(dungeonLevel, player)
                .ifPresent(instanceId -> recordPhysicalPresence(
                        dungeonLevel,
                        player,
                        instanceId
                ));
    }

    public static boolean isPhysicallyPresentIn(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return findCurrentPhysicalInstance(dungeonLevel, player)
                .map(instanceId::equals)
                .orElse(false);
    }

    private static void recordPhysicalPresence(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        Optional<DungeonInstance> instance =
                io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService.get(
                        dungeonLevel,
                        instanceId
                );

        if (instance.isEmpty() || !allowsPhysicalPresence(instance.get().status())) {
            return;
        }

        /*
         * Physical entry through the dungeon dimension is not portal entry. Do
         * not apply portal access rules here, do not overwrite PlayerDungeonData
         * return information, and do not refresh/recalculate difficulty. The
         * difficulty was fixed at original instance reservation time.
         *
         * TODO: If physical-only players need return mechanics later, introduce
         * an explicit physical-presence binding instead of overloading
         * PlayerDungeonData.
         */
        DungeonSessionManager.registerPhysicalParticipant(
                dungeonLevel,
                instanceId,
                player.getUUID()
        );
    }

    private static boolean allowsPhysicalPresence(DungeonStatus status) {
        return status == DungeonStatus.ACTIVE
                || status == DungeonStatus.REWARD_PHASE;
    }
}
