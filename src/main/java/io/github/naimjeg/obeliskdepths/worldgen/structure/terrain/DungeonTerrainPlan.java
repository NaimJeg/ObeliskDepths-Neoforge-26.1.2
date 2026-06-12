package io.github.naimjeg.obeliskdepths.worldgen.structure.terrain;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonTerrainPlan(
        BlockPos origin,
        BoundingBox outerBounds,
        List<DungeonRoomVolume> rooms,
        List<DungeonCorridorVolume> corridors
) {
    public DungeonTerrainPlan {
        if (origin == null || outerBounds == null) {
            throw new IllegalArgumentException("Dungeon terrain plan origin and bounds must be present");
        }

        rooms = List.copyOf(rooms);
        corridors = List.copyOf(corridors);

        if (rooms.isEmpty()) {
            throw new IllegalArgumentException("Dungeon terrain plan requires at least one room");
        }

        for (DungeonRoomVolume room : rooms) {
            requireContains(outerBounds, room.outerBounds(), "room " + room.roomId());
        }

        for (DungeonCorridorVolume corridor : corridors) {
            requireContains(outerBounds, corridor.bounds(), "corridor " + corridor.id());
        }
    }

    private static void requireContains(
            BoundingBox outer,
            BoundingBox inner,
            String label
    ) {
        if (inner.minX() < outer.minX()
                || inner.maxX() > outer.maxX()
                || inner.minY() < outer.minY()
                || inner.maxY() > outer.maxY()
                || inner.minZ() < outer.minZ()
                || inner.maxZ() > outer.maxZ()) {
            throw new IllegalArgumentException(
                    "Dungeon terrain outer bounds do not contain " + label + ": outer=" + outer + ", inner=" + inner
            );
        }
    }
}
