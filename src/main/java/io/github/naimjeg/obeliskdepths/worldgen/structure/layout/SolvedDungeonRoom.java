package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record SolvedDungeonRoom(
        DungeonRoomSpec spec,
        BlockPos anchor,
        BoundingBox bounds
) {
    public SolvedDungeonRoom {
        if (spec == null) {
            throw new IllegalArgumentException("Solved dungeon room requires a spec");
        }

        if (anchor == null) {
            throw new IllegalArgumentException("Solved dungeon room " + spec.id() + " requires an anchor");
        }

        if (bounds == null) {
            throw new IllegalArgumentException("Solved dungeon room " + spec.id() + " requires bounds");
        }
    }
}
