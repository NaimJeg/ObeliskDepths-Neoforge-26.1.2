package io.github.naimjeg.obeliskdepths.world;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnResolver;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.GeneratedDungeonSiteReader;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
            ObeliskDepths.LOGGER.error(
                    "[OD teleport] target dimension missing instance={} dimension={}",
                    instance.id(),
                    ModDimensions.OBELISK_DEPTHS_LEVEL.identifier()
            );
            return Optional.empty();
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(targetLevel);

        if (!data.reservedSite(instance.id()).filter(instance.siteKey()::equals).isPresent()) {
            ObeliskDepths.LOGGER.error(
                    "[OD teleport] instance reservation missing or mismatched instance={} site={}",
                    instance.id(),
                    instance.siteKey()
            );
            return Optional.empty();
        }

        /*
         * Teleportation may load existing chunks for validation, but it must not create
         * or repair the dungeon. Missing physical geometry is an allocation/worldgen
         * failure, not a request for runtime materialization.
         */
        Optional<DungeonSite> site = GeneratedDungeonSiteReader.readGeneratedSite(
                targetLevel,
                instance.siteKey()
        );

        if (site.isEmpty()) {
            ObeliskDepths.LOGGER.error(
                    "[OD teleport] generated structure data missing instance={} site={}",
                    instance.id(),
                    instance.siteKey()
            );
            return Optional.empty();
        }

        if (site.get().primaryEntryRoom().isEmpty()) {
            ObeliskDepths.LOGGER.error(
                    "[OD teleport] primary entry room missing instance={} site={}",
                    instance.id(),
                    instance.siteKey()
            );
            return Optional.empty();
        }

        if (!loadExistingEntryChunk(targetLevel, site.get())) {
            ObeliskDepths.LOGGER.error(
                    "[OD teleport] entry chunk is not already generated or loaded instance={} site={} start={}",
                    instance.id(),
                    instance.siteKey(),
                    site.get().startPos()
            );
            return Optional.empty();
        }

        Optional<Vec3> spawn = DungeonSafeSpawnResolver.resolvePrimaryEntrySpawn(
                targetLevel,
                site.get()
        );

        if (spawn.isEmpty()) {
            ObeliskDepths.LOGGER.error(
                    "[OD teleport] no safe spawn inside generated primary entry instance={} site={} start={}",
                    instance.id(),
                    instance.siteKey(),
                    site.get().startPos()
            );
            return Optional.empty();
        }

        return teleportToLevel(
                player,
                targetLevel,
                spawn.get()
        );
    }

    private static boolean loadExistingEntryChunk(
            ServerLevel level,
            DungeonSite site
    ) {
        ChunkPos chunkPos = new ChunkPos(
                SectionPos.blockToSectionCoord(site.startPos().getX()),
                SectionPos.blockToSectionCoord(site.startPos().getZ())
        );
        ChunkAccess chunk = level.getChunk(
                chunkPos.x(),
                chunkPos.z(),
                ChunkStatus.FULL,
                false
        );

        return chunk != null;
    }

    public static Optional<ServerPlayer> teleportToLevel(
            ServerPlayer player,
            ServerLevel targetLevel,
            BlockPos targetPos
    ) {
        return teleportToLevel(player, targetLevel, Vec3.atCenterOf(targetPos));
    }

    public static Optional<ServerPlayer> teleportToLevel(
            ServerPlayer player,
            ServerLevel targetLevel,
            Vec3 target
    ) {
        long startNanos = System.nanoTime();
        ServerPlayer teleportedPlayer = player.teleport(new TeleportTransition(
                targetLevel,
                target,
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                TeleportTransition.DO_NOTHING
        ));

        ObeliskDepths.LOGGER.debug(
                "[OD timing] teleport player={} target={} elapsedMicros={}",
                player.getGameProfile().name(),
                target,
                (System.nanoTime() - startNanos) / 1_000L
        );

        return Optional.ofNullable(teleportedPlayer);
    }

}
