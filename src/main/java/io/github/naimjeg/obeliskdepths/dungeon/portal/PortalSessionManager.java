package io.github.naimjeg.obeliskdepths.dungeon.portal;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

public final class PortalSessionManager {
    private PortalSessionManager() {
    }

    public static PortalSession openSession(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID opener,
            BlockPos obeliskPos,
            DungeonAccessMode accessMode,
            long currentGameTime
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).createPortalSession(
                instanceId,
                opener,
                obeliskPos,
                accessMode,
                currentGameTime
        );
    }

    public static Optional<PortalSession> get(
            ServerLevel dungeonLevel,
            PortalSessionId id
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).getPortalSession(id);
    }

    public static Optional<PortalSession> findActivePartyOpenSession(
            ServerLevel dungeonLevel,
            BlockPos obeliskPos,
            long gameTime
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).findActivePartyOpenSession(
                obeliskPos,
                gameTime
        );
    }

    public static boolean removeSession(
            ServerLevel dungeonLevel,
            PortalSessionId id
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).removePortalSession(id);
    }

    public static boolean addParticipant(
            ServerLevel dungeonLevel,
            PortalSessionId id,
            UUID playerId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).addPortalSessionParticipant(
                id,
                playerId
        );
    }

    public static boolean removeParticipant(
            ServerLevel dungeonLevel,
            PortalSessionId id,
            UUID playerId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).removePortalSessionParticipant(
                id,
                playerId
        );
    }

    public static int removeParticipantFromInstanceSessions(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .removePortalSessionParticipantFromInstanceSessions(
                        instanceId,
                        playerId
                );
    }

    public static int removeSessionsForInactiveInstances(ServerLevel dungeonLevel) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .removePortalSessionsForInactiveInstances();
    }


    public static int purgeExpired(
            ServerLevel dungeonLevel,
            long gameTime
    ) {
        return DungeonManagerSavedData.get(dungeonLevel).purgeExpiredPortalSessions(gameTime);
    }

    public static int activeSessionCount(ServerLevel dungeonLevel) {
        return DungeonManagerSavedData.get(dungeonLevel).activePortalSessionCount();
    }
}