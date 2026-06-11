package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class DungeonRewardService {
    private DungeonRewardService() {
    }

    /*
     * Compatibility note:
     * DungeonRoomState.rewardClaimed currently means "room reward chest opened
     * or consumed", not gameplay ownership by a player. Reward distribution is
     * intentionally vanilla pickup based after chest contents are sprayed into
     * the world.
     */
    public static boolean isRewardEligible(DungeonRoomState room) {
        return canSpawnRewardChest(room);
    }

    public static boolean canSpawnRewardChest(DungeonRoomState room) {
        return room.status() == DungeonRoomStatus.CLEARED
                && !room.rewardClaimed()
                && isRewardRoomType(room.type());
    }

    public static boolean markRewardClaimed(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        return markRewardChestOpened(level, instanceId, roomId);
    }

    public static boolean markRewardChestOpened(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRoomState> room = data.getRoomState(instanceId, roomId);

        if (room.isEmpty() || !canSpawnRewardChest(room.get())) {
            return false;
        }

        sprayRewardContents(level, instanceId, roomId);

        boolean changed = data.markRewardClaimed(instanceId, roomId);

        if (changed) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon reward chest opened: instance={}, room={}",
                    instanceId,
                    roomId
            );
        }

        return changed;
    }

    public static void sprayRewardContents(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        /*
         * TODO: Generate final loot using DungeonDifficulty.rewardCeilingTier()
         * and DungeonDifficulty.rewardWeightMultiplier(). Tempering template
         * rewards should use TemperingTemplateItems.createRewardTemplate().
         *
         * TODO: Tag dungeon reward drops with instance id so cleanup can remove
         * unclaimed drops without deleting normal player-dropped items.
         */
        ObeliskDepths.LOGGER.debug(
                "Dungeon reward spray placeholder: instance={}, room={}",
                instanceId,
                roomId
        );
    }

    private static boolean isRewardRoomType(DungeonRoomType type) {
        return type == DungeonRoomType.BOSS
                || type == DungeonRoomType.TREASURE;
    }
}
