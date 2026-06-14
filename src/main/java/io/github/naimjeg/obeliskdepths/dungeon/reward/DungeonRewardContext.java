package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import net.minecraft.server.level.ServerLevel;

public record DungeonRewardContext(
        ServerLevel level,
        DungeonInstanceId instanceId,
        DungeonRoomId roomId,
        DungeonRoomType roomType
) {
}
