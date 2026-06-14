package io.github.naimjeg.obeliskdepths.dungeon.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record DungeonRuntimeArtifactRecord(
        DungeonInstanceId instanceId,
        DungeonRuntimeArtifactType type,
        Optional<BlockPos> pos,
        Optional<DungeonRewardId> rewardId,
        boolean pendingCleanup
) {
    public static final Codec<DungeonRuntimeArtifactRecord> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonInstanceId.CODEC.fieldOf("instance_id")
                            .forGetter(DungeonRuntimeArtifactRecord::instanceId),
                    DungeonRuntimeArtifactType.CODEC.fieldOf("type")
                            .forGetter(DungeonRuntimeArtifactRecord::type),
                    BlockPos.CODEC.optionalFieldOf("pos")
                            .forGetter(DungeonRuntimeArtifactRecord::pos),
                    DungeonRewardId.CODEC.optionalFieldOf("reward_id")
                            .forGetter(DungeonRuntimeArtifactRecord::rewardId),
                    Codec.BOOL.optionalFieldOf("pending_cleanup", false)
                            .forGetter(DungeonRuntimeArtifactRecord::pendingCleanup)
            ).apply(instance, DungeonRuntimeArtifactRecord::new));
}
