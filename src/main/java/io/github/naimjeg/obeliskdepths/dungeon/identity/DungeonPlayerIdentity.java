package io.github.naimjeg.obeliskdepths.dungeon.identity;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class DungeonPlayerIdentity {
    private DungeonPlayerIdentity() {
    }

    public static Optional<DungeonInstanceId> currentInstanceId(ServerPlayer player) {
        return PlayerDungeonTracker.currentInstanceId(player);
    }

    public static boolean isInDungeonDimension(ServerPlayer player) {
        return player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL);
    }

    public static boolean belongsToInstance(
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return PlayerDungeonTracker.isBoundToInstance(player, instanceId);
    }

    public static boolean isActiveParticipantOf(
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return isInDungeonDimension(player)
                && belongsToInstance(player, instanceId)
                && player.isAlive();
    }
}