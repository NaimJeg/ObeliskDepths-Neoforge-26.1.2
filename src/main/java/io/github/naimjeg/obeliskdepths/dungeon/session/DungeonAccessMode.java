package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonAccessMode implements StringRepresentable {
    STARTER_ONLY("starter_only"),
    OPEN("open"),
    ALLOWLIST("allowlist");

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
