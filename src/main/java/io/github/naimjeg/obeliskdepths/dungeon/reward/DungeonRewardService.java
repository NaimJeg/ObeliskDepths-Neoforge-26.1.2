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

    public static boolean isRewardEligible(DungeonRoomState room) {
        return room.status() == DungeonRoomStatus.CLEARED
                && !room.rewardClaimed()
                && isRewardRoomType(room.type());
    }

    public static boolean markRewardClaimed(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRoomState> room = data.getRoomState(instanceId, roomId);

        if (room.isEmpty() || !isRewardEligible(room.get())) {
            return false;
        }

        // TODO: Generate final loot using DungeonDifficulty.rewardCeilingTier()
        // and DungeonDifficulty.rewardWeightMultiplier(). Tempering template
        // rewards should use TemperingTemplateItems.createRewardTemplate().
        boolean changed = data.markRewardClaimed(instanceId, roomId);

        if (changed) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon reward claimed: instance={}, room={}",
                    instanceId,
                    roomId
            );
        }

        return changed;
    }

    private static boolean isRewardRoomType(DungeonRoomType type) {
        return type == DungeonRoomType.COMBAT
                || type == DungeonRoomType.BOSS
                || type == DungeonRoomType.TREASURE;
    }
}
