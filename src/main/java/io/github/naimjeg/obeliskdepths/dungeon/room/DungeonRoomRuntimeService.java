// filename: DungeonRoomRuntimeService.java
package io.github.naimjeg.obeliskdepths.dungeon.room;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidPlayers;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidService;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class DungeonRoomRuntimeService {
    private DungeonRoomRuntimeService() {
    }

    public static void tickRooms(ServerLevel dungeonLevel) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        data.forEachActiveInstance(instance -> tickInstanceRooms(
                dungeonLevel,
                data,
                instance
        ));
    }

    private static void tickInstanceRooms(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        if (instance.status() != DungeonStatus.ACTIVE) {
            return;
        }

        Optional<ResolvedDungeonSite> resolvedSite =
                DungeonSiteProjectionCache.read(
                        dungeonLevel,
                        instance.siteKey()
                );

        if (resolvedSite.isEmpty()) {
            return;
        }

        for (ServerPlayer player : DungeonRaidPlayers.findActivePlayersInDungeon(
                dungeonLevel,
                instance
        )) {
            tickPlayerRoomPresence(
                    dungeonLevel,
                    data,
                    instance,
                    resolvedSite.get().site(),
                    player
            );
        }
    }

    private static void tickPlayerRoomPresence(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonSite site,
            ServerPlayer player
    ) {
        Optional<DungeonGeneratedRoom> currentRoom =
                site.roomAt(player.blockPosition());

        Optional<DungeonRoomId> previousRoomId =
                PlayerDungeonTracker.currentRoomId(player);

        Optional<DungeonRoomId> currentRoomId =
                currentRoom.map(DungeonGeneratedRoom::id);

        if (previousRoomId.equals(currentRoomId)) {
            return;
        }

        PlayerDungeonTracker.setCurrentRoom(player, currentRoomId);

        currentRoom.ifPresent(room -> onPlayerEnteredRoom(
                dungeonLevel,
                data,
                instance,
                room
        ));
    }

    private static void onPlayerEnteredRoom(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonGeneratedRoom room
    ) {
        Optional<DungeonRoomState> state =
                data.getRoomState(instance.id(), room.id());

        if (state.isEmpty()) {
            return;
        }

        DungeonRoomStatus status = state.get().status();

        if (status == DungeonRoomStatus.LOCKED
                || status == DungeonRoomStatus.ACTIVE
                || status == DungeonRoomStatus.CLEARED
                || status == DungeonRoomStatus.FAILED) {
            return;
        }

        if (status == DungeonRoomStatus.UNDISCOVERED) {
            data.setRoomStatus(
                    instance.id(),
                    room.id(),
                    DungeonRoomStatus.DISCOVERED
            );
        }

        if (room.type() == DungeonRoomType.COMBAT
                || room.type() == DungeonRoomType.BOSS) {
            activateEncounterRoom(
                    dungeonLevel,
                    data,
                    instance,
                    room
            );
        }
    }

    private static void activateEncounterRoom(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonGeneratedRoom room
    ) {
        Identifier raidType = room.type() == DungeonRoomType.BOSS
                ? BuiltinDungeonRaids.BOSS_ROOM
                : BuiltinDungeonRaids.COMBAT_ROOM;

        DungeonRaidService.getOrCreateRoomRaid(
                dungeonLevel,
                instance.id(),
                room.id(),
                raidType,
                dungeonLevel.getGameTime()
        );

        data.setRoomStatus(
                instance.id(),
                room.id(),
                DungeonRoomStatus.ACTIVE
        );
    }
}
