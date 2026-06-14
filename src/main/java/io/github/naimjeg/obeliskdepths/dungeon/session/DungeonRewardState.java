package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record DungeonRewardState(
        DungeonRewardChestState chestState,
        Optional<BlockPos> chestPos,
        boolean bossKilled
) {
    public static final Codec<DungeonRewardState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonRewardChestState.CODEC
                            .optionalFieldOf(
                                    "chest_state",
                                    DungeonRewardChestState.NOT_SPAWNED
                            )
                            .forGetter(DungeonRewardState::chestState),
                    BlockPos.CODEC.optionalFieldOf("chest_pos")
                            .forGetter(DungeonRewardState::chestPos),
                    Codec.BOOL.optionalFieldOf("boss_killed", false)
                            .forGetter(DungeonRewardState::bossKilled)
            ).apply(instance, DungeonRewardState::new));

    public DungeonRewardState {
        if (chestState == null) {
            chestState = DungeonRewardChestState.NOT_SPAWNED;
        }

        chestPos = chestPos == null ? Optional.empty() : chestPos;
    }

    public static DungeonRewardState empty() {
        return new DungeonRewardState(
                DungeonRewardChestState.NOT_SPAWNED,
                Optional.empty(),
                false
        );
    }

    public DungeonRewardState withBossKilled(Optional<BlockPos> chestPos) {
        return new DungeonRewardState(
                DungeonRewardChestState.PLACEMENT_PENDING,
                chestPos,
                true
        );
    }

    public DungeonRewardState withOpenedChest() {
        return new DungeonRewardState(
                DungeonRewardChestState.OPENED,
                this.chestPos,
                this.bossKilled
        );
    }
}
