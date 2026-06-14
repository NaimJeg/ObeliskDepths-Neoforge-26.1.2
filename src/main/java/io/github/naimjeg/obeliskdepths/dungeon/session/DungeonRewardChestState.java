package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonRewardChestState implements StringRepresentable {
    NOT_SPAWNED("not_spawned"),
    PLACEMENT_PENDING("placement_pending"),
    AVAILABLE("available"),
    SPAWNED("spawned"),
    OPENED("opened"),
    CLAIMED("claimed"),
    EXPIRED("expired");

    public static final Codec<DungeonRewardChestState> CODEC =
            StringRepresentable.fromEnum(DungeonRewardChestState::values);

    private final String serializedName;

    DungeonRewardChestState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
