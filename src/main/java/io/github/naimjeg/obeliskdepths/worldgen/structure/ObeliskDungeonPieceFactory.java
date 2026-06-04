package io.github.naimjeg.obeliskdepths.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ObeliskDungeonPieceFactory {
    private ObeliskDungeonPieceFactory() {
    }

    public static ObeliskDungeonPiece room(
            ObeliskDungeonPieceRole role,
            String roomId,
            BlockPos anchor,
            int width,
            int height,
            int depth
    ) {
        int halfX = width / 2;
        int halfZ = depth / 2;

        BoundingBox bounds = new BoundingBox(
                anchor.getX() - halfX,
                anchor.getY() - 1,
                anchor.getZ() - halfZ,
                anchor.getX() + halfX,
                anchor.getY() + height - 2,
                anchor.getZ() + halfZ
        );

        return new ObeliskDungeonPiece(
                role,
                roomId,
                anchor,
                bounds
        );
    }

    public static ObeliskDungeonPiece corridorBetweenRoomsX(
            String corridorId,
            BoundingBox leftRoom,
            BoundingBox rightRoom,
            int y,
            int centerZ,
            int width
    ) {
        int minX = leftRoom.maxX() + 1;
        int maxX = rightRoom.minX() - 1;

        if (maxX < minX) {
            throw new IllegalStateException(
                    "Invalid X corridor gap for "
                            + corridorId
                            + ": leftRoom="
                            + leftRoom
                            + ", rightRoom="
                            + rightRoom
            );
        }

        int halfWidth = width / 2;

        BoundingBox bounds = new BoundingBox(
                minX,
                y - 1,
                centerZ - halfWidth,
                maxX,
                y + 4,
                centerZ + halfWidth
        );

        BlockPos anchor = new BlockPos(
                (minX + maxX) / 2,
                y,
                centerZ
        );

        return new ObeliskDungeonPiece(
                ObeliskDungeonPieceRole.CORRIDOR,
                corridorId,
                anchor,
                bounds
        );
    }
}
