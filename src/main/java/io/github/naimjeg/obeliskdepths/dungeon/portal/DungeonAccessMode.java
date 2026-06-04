package io.github.naimjeg.obeliskdepths.dungeon.portal;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonAccessMode implements StringRepresentable {
    SOLO("solo"),
    PARTY_OPEN("party_open");

    public static final Codec<DungeonAccessMode> CODEC =
            StringRepresentable.fromEnum(DungeonAccessMode::values);

    private final String serializedName;

    DungeonAccessMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}