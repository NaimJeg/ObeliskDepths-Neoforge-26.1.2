package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record SolvedDungeonCorridor(
        DungeonConnectionSpec spec,
        BlockPos anchor,
        BoundingBox bounds
) {
    public SolvedDungeonCorridor {
        if (spec == null) {
            throw new IllegalArgumentException("Solved dungeon corridor requires a spec");
        }

        if (anchor == null) {
            throw new IllegalArgumentException("Solved dungeon corridor " + spec.id() + " requires an anchor");
        }

        if (bounds == null) {
            throw new IllegalArgumentException("Solved dungeon corridor " + spec.id() + " requires bounds");
        }
    }
}
