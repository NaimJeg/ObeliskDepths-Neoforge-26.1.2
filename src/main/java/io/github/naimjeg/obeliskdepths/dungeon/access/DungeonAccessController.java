package io.github.naimjeg.obeliskdepths.dungeon.access;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonAccessMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;

import java.util.UUID;

public final class DungeonAccessController {
    private DungeonAccessController() {
    }

    public static DungeonAccessResult canEnter(
            UUID playerId,
            PortalSession session,
            DungeonInstance instance,
            long gameTime
    ) {
        if (session.isExpired(gameTime)) {
            return DungeonAccessResult.DENY_PORTAL_EXPIRED;
        }

        if (instance.status() != DungeonStatus.ACTIVE) {
            return DungeonAccessResult.DENY_INSTANCE_CLOSED;
        }

        if (session.accessMode() == DungeonAccessMode.SOLO
                && !session.opener().equals(playerId)) {
            return DungeonAccessResult.DENY_SOLO_NOT_OPENER;
        }

        int maxParticipants = session.accessMode() == DungeonAccessMode.SOLO ? 1 : 4;

        if (!instance.isParticipant(playerId)
                && instance.participants().size() >= maxParticipants) {
            return DungeonAccessResult.DENY_MAX_PARTICIPANTS;
        }

        return DungeonAccessResult.ALLOW;
    }
}