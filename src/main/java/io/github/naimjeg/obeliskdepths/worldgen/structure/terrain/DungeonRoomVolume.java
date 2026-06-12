package io.github.naimjeg.obeliskdepths.worldgen.structure.terrain;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonRoomVolume(
        String roomId,
        DungeonRoomType type,
        BoundingBox outerBounds,
        BoundingBox interiorBounds,
        BlockPos anchorPos
) {
    public DungeonRoomVolume {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Dungeon room volume id must be non-empty");
        }

        if (type == null) {
            throw new IllegalArgumentException("Dungeon room volume type must be present: " + roomId);
        }

        if (outerBounds == null || interiorBounds == null) {
            throw new IllegalArgumentException("Dungeon room volume bounds must be present: " + roomId);
        }

        if (anchorPos == null) {
            throw new IllegalArgumentException("Dungeon room volume anchor must be present: " + roomId);
        }
    }
}
