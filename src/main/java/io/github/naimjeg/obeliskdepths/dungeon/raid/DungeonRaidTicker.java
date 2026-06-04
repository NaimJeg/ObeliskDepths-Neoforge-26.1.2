package io.github.naimjeg.obeliskdepths.dungeon.raid;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRuntimeService;
import net.minecraft.server.level.ServerLevel;

public final class DungeonRaidTicker {
    private DungeonRaidTicker() {
    }

    public static void tickRaids(ServerLevel level) {
        DungeonRoomRuntimeService.tickRooms(level);
        DungeonRoomRaidResolver.tickRoomRaids(level);
    }
}
