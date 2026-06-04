package io.github.naimjeg.obeliskdepths.dungeon.raid;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityTracker;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class DungeonRoomRaidSpawner {
    private static final int[][] OFFSETS = {
            {0, 0},
            {2, 0},
            {-2, 0},
            {0, 2},
            {0, -2},
            {3, 2},
            {-3, 2},
            {3, -2},
            {-3, -2},
            {4, 0},
            {-4, 0},
            {0, 4},
            {0, -4}
    };

    private DungeonRoomRaidSpawner() {
    }

    public static boolean spawnNextWave(
            ServerLevel level,
            DungeonInstance instance,
            DungeonGeneratedRoom room,
            DungeonRaidInstance raid
    ) {
        /*
         * Current shallow custom spawn system:
         * room-owned raids spawn enemies for the active combat/boss room and
         * register those entities into DungeonSession. Later, the session layer
         * can own a broader "spawn all acceptable rooms" pass once detailed room
         * spawn rules, elite rules, and boss phase rules are designed.
         */
        int targetCount = spawnCount(instance.difficulty(), room.type());
        int spawned = 0;

        for (int index = 0; index < targetCount; index++) {
            EntityType<? extends Mob> entityType = entityTypeFor(index);
            Optional<BlockPos> spawnPos = findSpawnPos(room, level, index);

            if (spawnPos.isEmpty()) {
                continue;
            }

            Mob entity = entityType.spawn(
                    level,
                    spawnPos.get(),
                    EntitySpawnReason.TRIGGERED
            );

            if (entity == null) {
                continue;
            }

            DungeonEntityTracker.bindRaidMob(
                    entity,
                    instance.id(),
                    raid.id(),
                    raid.currentWave()
            );

            DungeonRaidService.markRaidMobSpawned(level, raid.id());
            DungeonSessionManager.registerSpawnedEntity(
                    level,
                    instance.id(),
                    entity.getUUID()
            );
            spawned++;
        }

        if (spawned > 0) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon raid wave spawned: instance={}, room={}, raid={}, wave={}, mobs={}",
                    instance.id(),
                    room.id(),
                    raid.id(),
                    raid.currentWave(),
                    spawned
            );
        }

        return spawned > 0;
    }

    private static int spawnCount(
            DungeonDifficulty difficulty,
            DungeonRoomType roomType
    ) {
        int baseCount = roomType == DungeonRoomType.BOSS ? 5 : 3;
        int tierBonus = Math.min(3, Math.max(0, difficulty.tier() / 2));
        int amountBonus = Math.min(
                2,
                Math.max(0, Math.round(difficulty.amountIntensity()))
        );

        return baseCount + tierBonus + amountBonus;
    }

    private static EntityType<? extends Mob> entityTypeFor(int index) {
        return index % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON;
    }

    private static Optional<BlockPos> findSpawnPos(
            DungeonGeneratedRoom room,
            ServerLevel level,
            int startOffset
    ) {
        for (int i = 0; i < OFFSETS.length; i++) {
            int[] offset = OFFSETS[(startOffset + i) % OFFSETS.length];
            BlockPos base = room.anchorPos().offset(offset[0], 0, offset[1]);

            for (int yOffset = 0; yOffset >= -2; yOffset--) {
                BlockPos candidate = base.offset(0, yOffset, 0);

                if (isValidSpawnPos(room.bounds(), level, candidate)) {
                    return Optional.of(candidate);
                }
            }

            for (int yOffset = 1; yOffset <= 2; yOffset++) {
                BlockPos candidate = base.offset(0, yOffset, 0);

                if (isValidSpawnPos(room.bounds(), level, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isValidSpawnPos(
            DungeonBounds bounds,
            ServerLevel level,
            BlockPos pos
    ) {
        if (!bounds.contains(pos) || !bounds.contains(pos.above())) {
            return false;
        }

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());

        return feet.isAir()
                && head.isAir()
                && floor.isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
