package io.github.naimjeg.obeliskdepths.dungeon.raid;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class DungeonRaidService {
    private DungeonRaidService() {
    }

    public static DungeonRaidInstance createRoomRaid(
            ServerLevel dungeonLevel,
            DungeonInstanceId dungeonInstanceId,
            DungeonRoomId roomId,
            Identifier raidType,
            long gameTime
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).createRoomRaid(
                dungeonInstanceId,
                roomId,
                raidType,
                gameTime
        );
    }

    public static DungeonRaidInstance getOrCreateRoomRaid(
            ServerLevel dungeonLevel,
            DungeonInstanceId dungeonInstanceId,
            DungeonRoomId roomId,
            Identifier raidType,
            long gameTime
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).getOrCreateRoomRaid(
                dungeonInstanceId,
                roomId,
                raidType,
                gameTime
        );
    }

    public static Optional<DungeonRaidInstance> findActiveRoomRaid(
            ServerLevel dungeonLevel,
            DungeonInstanceId dungeonInstanceId,
            DungeonRoomId roomId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).findActiveRoomRaid(
                dungeonInstanceId,
                roomId
        );
    }

    public static Optional<DungeonRaidInstance> getRaid(
            ServerLevel dungeonLevel,
            DungeonRaidId raidId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).getRaid(raidId);
    }

    public static boolean removeRaid(
            ServerLevel dungeonLevel,
            DungeonRaidId raidId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).removeRaid(raidId);
    }

    public static boolean setRaidStatus(
            ServerLevel dungeonLevel,
            DungeonRaidId raidId,
            DungeonRaidStatus status
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).setRaidStatus(
                raidId,
                status
        );
    }

    public static boolean advanceRaidWave(
            ServerLevel dungeonLevel,
            DungeonRaidId raidId,
            long nextWaveGameTime
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).advanceRaidWave(
                raidId,
                nextWaveGameTime
        );
    }

    public static boolean markRaidMobSpawned(
            ServerLevel dungeonLevel,
            DungeonRaidId raidId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).markRaidMobSpawned(
                raidId
        );
    }

    public static boolean markRaidMobKilled(
            ServerLevel dungeonLevel,
            DungeonRaidId raidId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).markRaidMobKilled(
                raidId
        );
    }
}