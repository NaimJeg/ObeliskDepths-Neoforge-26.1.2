package io.github.naimjeg.obeliskdepths.block;

import net.minecraft.util.StringRepresentable;

public enum ObeliskPart implements StringRepresentable {
    BOTTOM("bottom"),
    MIDDLE("middle"),
    TOP("top");

    private final String name;

    ObeliskPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}