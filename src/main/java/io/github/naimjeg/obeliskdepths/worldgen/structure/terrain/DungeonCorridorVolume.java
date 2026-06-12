package io.github.naimjeg.obeliskdepths.worldgen.structure.terrain;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonCorridorVolume(
        String id,
        String fromRoomId,
        String toRoomId,
        BoundingBox bounds,
        int widthBlocks
) {
    public DungeonCorridorVolume {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dungeon corridor volume id must be non-empty");
        }

        if (fromRoomId == null || fromRoomId.isBlank() || toRoomId == null || toRoomId.isBlank()) {
            throw new IllegalArgumentException("Dungeon corridor endpoints must be present: " + id);
        }

        if (bounds == null) {
            throw new IllegalArgumentException("Dungeon corridor bounds must be present: " + id);
        }

        if (widthBlocks <= 0) {
            throw new IllegalArgumentException("Dungeon corridor width must be positive: " + id);
        }
    }
}
