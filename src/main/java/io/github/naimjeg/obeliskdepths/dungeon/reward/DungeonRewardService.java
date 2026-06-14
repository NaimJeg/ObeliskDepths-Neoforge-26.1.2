package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactRecord;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactType;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonRewardService {
    private static final int MAX_PLACEMENT_FAILURES = 5;

    private DungeonRewardService() {
    }

    public static boolean isRewardEligible(DungeonRoomState room) {
        return canSpawnRewardChest(room);
    }

    public static boolean canSpawnRewardChest(DungeonRoomState room) {
        return room.status() == DungeonRoomStatus.CLEARED
                && !room.rewardClaimed()
                && isRewardRoomType(room.type());
    }

    public static DungeonRewardRecord onBossDefeated(
            ServerLevel level,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRewardRecord> existing = data.findRewardByInstance(instanceId);

        if (existing.isPresent()) {
            tryPlaceReward(level, existing.get());
            return existing.get();
        }

        Optional<DungeonRoomId> bossRoomId = data.roomStates(instanceId)
                .stream()
                .filter(room -> room.type() == DungeonRoomType.BOSS)
                .map(DungeonRoomState::roomId)
                .findFirst();

        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                instanceId,
                bossRoomId,
                level.getGameTime()
        );
        data.addReward(reward);
        tryPlaceReward(level, reward);
        return reward;
    }

    public static boolean tryPlaceReward(
            ServerLevel level,
            DungeonRewardRecord reward
    ) {
        if (reward.status().terminal() || reward.status() == DungeonRewardStatus.AVAILABLE) {
            return false;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        reward.markPlacementPending();

        Optional<BlockPos> pos = reward.rewardPos().or(() -> resolveRewardPos(level, reward));

        if (pos.isEmpty() || !placeRewardChest(level, pos.get())) {
            reward.recordPlacementFailure();
            data.markRewardsDirty();

            if (reward.placementFailures() >= MAX_PLACEMENT_FAILURES) {
                ObeliskDepths.LOGGER.warn(
                        "Dungeon reward placement still pending after bounded retries: instance={}, reward={}, failures={}",
                        reward.instanceId(),
                        reward.rewardId(),
                        reward.placementFailures()
                );
            }

            return false;
        }

        reward.markAvailable(pos.get());
        data.registerRuntimeArtifact(new DungeonRuntimeArtifactRecord(
                reward.instanceId(),
                DungeonRuntimeArtifactType.REWARD_CHEST,
                Optional.of(pos.get()),
                Optional.of(reward.rewardId()),
                false
        ));
        data.markRewardsDirty();

        ObeliskDepths.LOGGER.debug(
                "Dungeon reward chest placed: instance={}, reward={}, pos={}",
                reward.instanceId(),
                reward.rewardId(),
                pos.get()
        );
        return true;
    }

    public static DungeonRewardClaimResult tryOpenReward(
            ServerPlayer player,
            DungeonInstanceId instanceId,
            BlockPos rewardPos
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return DungeonRewardClaimResult.ROOM_MISSING;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()
                || !io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager.canAccessDungeon(
                player,
                session.get()
        )) {
            return DungeonRewardClaimResult.ROOM_NOT_CLEARED;
        }

        Optional<DungeonRewardRecord> reward = data.findRewardAt(instanceId, rewardPos);

        if (reward.isEmpty()) {
            return DungeonRewardClaimResult.ROOM_MISSING;
        }

        DungeonRewardRecord record = reward.get();

        if (record.status() == DungeonRewardStatus.CLAIMED) {
            return DungeonRewardClaimResult.ALREADY_OPENED;
        }

        if (record.status() != DungeonRewardStatus.AVAILABLE
                && record.status() != DungeonRewardStatus.OPENED) {
            return DungeonRewardClaimResult.ROOM_NOT_CLEARED;
        }

        record.markOpened();
        sprayRewardContents(level, record, rewardPos);
        record.markClaimed();
        data.markRewardsDirty();
        record.roomId().ifPresent(roomId -> data.markRewardClaimed(instanceId, roomId));

        return DungeonRewardClaimResult.SUCCESS;
    }

    public static boolean markRewardClaimed(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        return markRewardChestOpened(level, instanceId, roomId);
    }

    public static boolean markRewardChestOpened(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        return tryMarkRewardChestOpened(level, instanceId, roomId)
                == DungeonRewardClaimResult.SUCCESS;
    }

    public static DungeonRewardClaimResult tryMarkRewardChestOpened(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        Optional<DungeonRewardRecord> reward = DungeonManagerSavedData.get(level)
                .findRewardByInstance(instanceId)
                .filter(record -> record.roomId().map(roomId::equals).orElse(false));

        if (reward.isEmpty()) {
            return DungeonRewardClaimResult.ROOM_MISSING;
        }

        if (reward.get().rewardPos().isEmpty()) {
            return DungeonRewardClaimResult.ROOM_NOT_CLEARED;
        }

        if (reward.get().status() == DungeonRewardStatus.CLAIMED) {
            return DungeonRewardClaimResult.ALREADY_OPENED;
        }

        return reward.get().status() == DungeonRewardStatus.AVAILABLE
                ? DungeonRewardClaimResult.SUCCESS
                : DungeonRewardClaimResult.ROOM_NOT_CLEARED;
    }

    public static void sprayRewardContents(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        DungeonManagerSavedData.get(level)
                .findRewardByInstance(instanceId)
                .filter(reward -> reward.roomId().map(roomId::equals).orElse(false))
                .flatMap(DungeonRewardRecord::rewardPos)
                .ifPresent(pos -> sprayRewardContents(
                        level,
                        DungeonManagerSavedData.get(level).findRewardByInstance(instanceId).orElseThrow(),
                        pos
                ));
    }

    public static void sprayRewardContents(DungeonRewardContext context) {
        ObeliskDepths.LOGGER.debug(
                "Dungeon reward spray requested without authoritative reward record: instance={}, room={}, type={}",
                context.instanceId(),
                context.roomId(),
                context.roomType().getSerializedName()
        );
    }

    private static void sprayRewardContents(
            ServerLevel level,
            DungeonRewardRecord reward,
            BlockPos pos
    ) {
        Random random = new Random(reward.rewardSeed());
        int emeralds = 2 + random.nextInt(4);
        int diamonds = random.nextInt(2);

        Containers.dropItemStack(
                level,
                pos.getX() + 0.5D,
                pos.getY() + 1.0D,
                pos.getZ() + 0.5D,
                new ItemStack(Items.EMERALD, emeralds)
        );

        if (diamonds > 0) {
            Containers.dropItemStack(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 0.5D,
                    new ItemStack(Items.DIAMOND, diamonds)
            );
        }
    }

    private static Optional<BlockPos> resolveRewardPos(
            ServerLevel level,
            DungeonRewardRecord reward
    ) {
        Optional<DungeonSite> site = DungeonManagerSavedData.get(level)
                .getInstance(reward.instanceId())
                .flatMap(instance -> DungeonSiteProjectionCache.read(level, instance.siteKey())
                        .map(ResolvedDungeonSite::site)
                        .or(() -> DungeonManagerSavedData.get(level).getSiteSnapshot(instance.siteKey())));

        return site.flatMap(value -> value.rooms()
                .stream()
                .filter(room -> reward.roomId().map(room.id()::equals).orElse(room.type() == DungeonRoomType.BOSS))
                .findFirst()
                .map(DungeonGeneratedRoom::anchorPos));
    }

    private static boolean placeRewardChest(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState current = level.getBlockState(pos);

        if (!current.isAir() && !current.canBeReplaced()) {
            return current.is(Blocks.CHEST);
        }

        return level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
    }

    private static boolean isRewardRoomType(DungeonRoomType type) {
        return type == DungeonRoomType.BOSS
                || type == DungeonRoomType.TREASURE;
    }
}
