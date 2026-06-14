package io.github.naimjeg.obeliskdepths.dungeon.artifact;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class DungeonRuntimeArtifactCleanupService {
    private DungeonRuntimeArtifactCleanupService() {
    }

    public static void cleanupInstanceArtifacts(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        int rewardChestsRemoved = 0;
        int rewardChestsPending = 0;

        for (DungeonRuntimeArtifactRecord artifact : data.runtimeArtifactsForInstance(instanceId)) {
            if (artifact.type() != DungeonRuntimeArtifactType.REWARD_CHEST
                    || artifact.pos().isEmpty()) {
                continue;
            }

            if (!dungeonLevel.hasChunkAt(artifact.pos().get())) {
                rewardChestsPending++;
                continue;
            }

            if (dungeonLevel.getBlockState(artifact.pos().get()).is(Blocks.CHEST)) {
                dungeonLevel.removeBlock(artifact.pos().get(), false);
                rewardChestsRemoved++;
            }
        }

        if (rewardChestsPending == 0) {
            data.removeRuntimeArtifactsForInstance(
                    instanceId,
                    DungeonRuntimeArtifactType.REWARD_CHEST
            );

            data.findRewardByInstance(instanceId).ifPresent(DungeonRewardRecord::markCleaned);
            data.markRewardsDirty();
        }

        ObeliskDepths.LOGGER.debug(
                "Dungeon runtime artifact cleanup: level={}, instance={}, rewardChestsRemoved={}, rewardChestsPending={}",
                dungeonLevel.dimension().identifier(),
                instanceId,
                rewardChestsRemoved,
                rewardChestsPending
        );
    }
}
