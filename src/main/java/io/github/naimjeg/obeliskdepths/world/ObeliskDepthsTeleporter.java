package io.github.naimjeg.obeliskdepths.world;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class ObeliskDepthsTeleporter {
    private ObeliskDepthsTeleporter() {
    }

    public static Optional<ServerPlayer> teleportToInstanceStart(
            ServerPlayer player,
            DungeonInstance instance
    ) {
        ServerLevel targetLevel = player.level()
                .getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (targetLevel == null) {
            return Optional.empty();
        }

        BlockPos spawnPos = resolveInstanceSpawnPos(
                targetLevel,
                instance
        );

        return teleportToLevel(player, targetLevel, spawnPos);
    }

    private static BlockPos resolveInstanceSpawnPos(
            ServerLevel targetLevel,
            DungeonInstance instance
    ) {
        return DungeonSiteProjectionCache.read(
                        targetLevel,
                        instance.siteKey()
                )
                .flatMap(resolved -> resolved.site().startRoom())
                .map(DungeonGeneratedRoom::spawnPos)
                .orElse(instance.startPos());
    }

    public static Optional<ServerPlayer> teleportToLevel(
            ServerPlayer player,
            ServerLevel targetLevel,
            BlockPos targetPos
    ) {
        Vec3 target = Vec3.atCenterOf(targetPos);

        ServerPlayer teleportedPlayer = player.teleport(new TeleportTransition(
                targetLevel,
                target,
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                TeleportTransition.DO_NOTHING
        ));

        return Optional.ofNullable(teleportedPlayer);
    }
}