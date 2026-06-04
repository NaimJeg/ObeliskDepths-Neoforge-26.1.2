package io.github.naimjeg.obeliskdepths.dungeon.instance;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonStatus implements StringRepresentable {
    ACTIVE("active"),
    PORTAL_CLOSED("portal_closed"),
    CLEARED("cleared"),
    EXPIRED("expired");

    public static final Codec<DungeonStatus> CODEC =
            StringRepresentable.fromEnum(DungeonStatus::values);

    private final String serializedName;

    DungeonStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}