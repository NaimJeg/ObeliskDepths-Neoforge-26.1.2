package io.github.naimjeg.obeliskdepths.dungeon.raid;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class DungeonRoomRaidResolver {
    private DungeonRoomRaidResolver() {
    }

    public static void tickRoomRaids(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);

        data.forEachActiveInstance(instance ->
                tickInstanceRoomRaids(level, data, instance)
        );
    }

    private static void tickInstanceRoomRaids(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        Optional<ResolvedDungeonSite> resolvedSite =
                DungeonSiteProjectionCache.read(level, instance.siteKey());

        if (resolvedSite.isEmpty()) {
            return;
        }

        DungeonSite site = resolvedSite.get().site();

        for (DungeonRoomState state : data.roomStates(instance.id())) {
            if (state.status() != DungeonRoomStatus.ACTIVE) {
                continue;
            }

            Optional<DungeonGeneratedRoom> room = site.room(state.roomId());

            if (room.isEmpty()) {
                continue;
            }

            DungeonRaidService.findActiveRoomRaid(
                    level,
                    instance.id(),
                    state.roomId()
            ).ifPresent(raid ->
                    tickRoomRaid(level, data, instance, room.get(), raid)
            );
        }
    }

    private static void tickRoomRaid(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonGeneratedRoom room,
            DungeonRaidInstance raid
    ) {
        if (raid.status() == DungeonRaidStatus.PREPARING) {
            tickPreparingRaid(level, instance, room, raid);
            return;
        }

        if (raid.status() != DungeonRaidStatus.ACTIVE) {
            return;
        }

        if (raid.spawnedMobCount() <= 0) {
            return;
        }

        if (raid.killedMobCount() < raid.spawnedMobCount()) {
            return;
        }

        DungeonRaidService.setRaidStatus(
                level,
                raid.id(),
                DungeonRaidStatus.WON
        );

        ObeliskDepths.LOGGER.debug(
                "Dungeon raid won: instance={}, room={}, raid={}, killed={}/{}",
                instance.id(),
                room.id(),
                raid.id(),
                raid.killedMobCount(),
                raid.spawnedMobCount()
        );

        if (data.setRoomStatus(
                instance.id(),
                room.id(),
                DungeonRoomStatus.CLEARED
        )) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon room cleared by raid: instance={}, room={}, raid={}",
                    instance.id(),
                    room.id(),
                    raid.id()
            );
        }

        if (room.type() == DungeonRoomType.BOSS) {
            /*
             * Shallow reward hook: a later block/entity controller can place the
             * visible chest and call DungeonSessionManager.openRewardChest(...).
             */
            DungeonSessionManager.markBossKilled(
                    level,
                    instance.id(),
                    Optional.of(room.anchorPos())
            );
        }
    }

    private static void tickPreparingRaid(
            ServerLevel level,
            DungeonInstance instance,
            DungeonGeneratedRoom room,
            DungeonRaidInstance raid
    ) {
        if (level.getGameTime() < raid.nextWaveGameTime()) {
            return;
        }

        boolean spawned = DungeonRoomRaidSpawner.spawnNextWave(
                level,
                instance,
                room,
                raid
        );

        if (spawned) {
            DungeonRaidService.setRaidStatus(
                    level,
                    raid.id(),
                    DungeonRaidStatus.ACTIVE
            );
        }
    }
}
