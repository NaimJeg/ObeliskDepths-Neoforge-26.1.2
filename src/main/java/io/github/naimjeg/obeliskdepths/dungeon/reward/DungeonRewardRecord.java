package io.github.naimjeg.obeliskdepths.dungeon.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class DungeonRewardRecord {
    public static final Codec<DungeonRewardRecord> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonRewardId.CODEC.fieldOf("reward_id")
                            .forGetter(DungeonRewardRecord::rewardId),
                    DungeonInstanceId.CODEC.fieldOf("instance_id")
                            .forGetter(DungeonRewardRecord::instanceId),
                    DungeonRoomId.CODEC.optionalFieldOf("room_id")
                            .forGetter(DungeonRewardRecord::roomId),
                    DungeonRewardStatus.CODEC
                            .optionalFieldOf("status", DungeonRewardStatus.NOT_READY)
                            .forGetter(DungeonRewardRecord::status),
                    BlockPos.CODEC.optionalFieldOf("reward_pos")
                            .forGetter(DungeonRewardRecord::rewardPos),
                    Codec.LONG.optionalFieldOf("reward_seed", 0L)
                            .forGetter(DungeonRewardRecord::rewardSeed),
                    Codec.INT.optionalFieldOf("placement_failures", 0)
                            .forGetter(DungeonRewardRecord::placementFailures),
                    Codec.LONG.optionalFieldOf("created_game_time", 0L)
                            .forGetter(DungeonRewardRecord::createdGameTime)
            ).apply(instance, DungeonRewardRecord::new));

    private final DungeonRewardId rewardId;
    private final DungeonInstanceId instanceId;
    private final Optional<DungeonRoomId> roomId;
    private DungeonRewardStatus status;
    private Optional<BlockPos> rewardPos;
    private final long rewardSeed;
    private int placementFailures;
    private final long createdGameTime;

    public DungeonRewardRecord(
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            DungeonRewardStatus status,
            Optional<BlockPos> rewardPos,
            long rewardSeed,
            int placementFailures,
            long createdGameTime
    ) {
        this.rewardId = rewardId;
        this.instanceId = instanceId;
        this.roomId = roomId == null ? Optional.empty() : roomId;
        this.status = status == null ? DungeonRewardStatus.NOT_READY : status;
        this.rewardPos = rewardPos == null ? Optional.empty() : rewardPos;
        this.rewardSeed = rewardSeed;
        this.placementFailures = Math.max(0, placementFailures);
        this.createdGameTime = createdGameTime;
    }

    public static DungeonRewardRecord bossDefeated(
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            long gameTime
    ) {
        long seed = UUID.randomUUID().getMostSignificantBits()
                ^ UUID.randomUUID().getLeastSignificantBits();
        return new DungeonRewardRecord(
                DungeonRewardId.create(),
                instanceId,
                roomId,
                DungeonRewardStatus.BOSS_DEFEATED,
                Optional.empty(),
                seed,
                0,
                gameTime
        );
    }

    public DungeonRewardId rewardId() {
        return this.rewardId;
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public Optional<DungeonRoomId> roomId() {
        return this.roomId;
    }

    public DungeonRewardStatus status() {
        return this.status;
    }

    public Optional<BlockPos> rewardPos() {
        return this.rewardPos;
    }

    public long rewardSeed() {
        return this.rewardSeed;
    }

    public int placementFailures() {
        return this.placementFailures;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public boolean markPlacementPending() {
        return setStatus(DungeonRewardStatus.PLACEMENT_PENDING);
    }

    public boolean markAvailable(BlockPos pos) {
        boolean changed = setStatus(DungeonRewardStatus.AVAILABLE);
        Optional<BlockPos> nextPos = Optional.of(pos);

        if (!nextPos.equals(this.rewardPos)) {
            this.rewardPos = nextPos;
            changed = true;
        }

        return changed;
    }

    public boolean recordPlacementFailure() {
        this.placementFailures++;
        return setStatus(DungeonRewardStatus.PLACEMENT_PENDING) || true;
    }

    public boolean markOpened() {
        return setStatus(DungeonRewardStatus.OPENED);
    }

    public boolean markClaimed() {
        return setStatus(DungeonRewardStatus.CLAIMED);
    }

    public boolean markCleaned() {
        return setStatus(DungeonRewardStatus.CLEANED);
    }

    private boolean setStatus(DungeonRewardStatus status) {
        if (this.status == status) {
            return false;
        }

        this.status = status;
        return true;
    }
}
