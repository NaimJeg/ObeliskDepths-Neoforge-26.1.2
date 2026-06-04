package io.github.naimjeg.obeliskdepths.dungeon.player;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionManager;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class PlayerDungeonReturnService {
    private PlayerDungeonReturnService() {
    }

    public static PlayerDungeonReturnResult returnPlayer(ServerPlayer player) {
        Optional<PlayerDungeonData> optionalData = PlayerDungeonTracker.get(player);

        if (optionalData.isEmpty()) {
            return PlayerDungeonReturnResult.NO_DUNGEON_BINDING;
        }

        PlayerDungeonData data = optionalData.get();

        Optional<DungeonInstanceId> optionalInstanceId = data.currentInstanceId();
        Optional<ResourceKey<Level>> optionalReturnDimension = data.returnDimension();
        Optional<BlockPos> optionalReturnPos = data.returnPos();

        if (optionalInstanceId.isEmpty()
                || optionalReturnDimension.isEmpty()
                || optionalReturnPos.isEmpty()) {
            return PlayerDungeonReturnResult.INCOMPLETE_RETURN_DATA;
        }

        MinecraftServer server = player.level().getServer();

        ServerLevel returnLevel = server.getLevel(optionalReturnDimension.get());

        if (returnLevel == null) {
            return PlayerDungeonReturnResult.RETURN_LEVEL_MISSING;
        }

        Optional<ServerPlayer> returnedPlayer =
                ObeliskDepthsTeleporter.teleportToLevel(
                        player,
                        returnLevel,
                        optionalReturnPos.get()
                );

        if (returnedPlayer.isEmpty()) {
            return PlayerDungeonReturnResult.TELEPORT_FAILED;
        }

        ServerPlayer effectivePlayer = returnedPlayer.get();
        DungeonInstanceId instanceId = optionalInstanceId.get();

        ServerLevel dungeonLevel = server.getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel != null) {
            DungeonInstanceService.removeParticipant(
                    dungeonLevel,
                    instanceId,
                    effectivePlayer.getUUID()
            );

            PortalSessionManager.removeParticipantFromInstanceSessions(
                    dungeonLevel,
                    instanceId,
                    effectivePlayer.getUUID()
            );
        }

        PlayerDungeonTracker.clear(effectivePlayer);

        return PlayerDungeonReturnResult.SUCCESS;
    }
}