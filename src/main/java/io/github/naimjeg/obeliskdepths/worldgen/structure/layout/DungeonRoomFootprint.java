package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonRoomFootprint(
        int widthCells,
        int heightCells,
        int depthCells
) {
    public static final int MAX_CELLS = 16;

    public DungeonRoomFootprint {
        if (widthCells <= 0 || heightCells <= 0 || depthCells <= 0) {
            throw new IllegalArgumentException("Room footprint cells must be positive");
        }

        if (widthCells > MAX_CELLS || heightCells > MAX_CELLS || depthCells > MAX_CELLS) {
            throw new IllegalArgumentException(
                    "Room footprint exceeds conservative max "
                            + MAX_CELLS
                            + " cells: "
                            + widthCells
                            + "x"
                            + heightCells
                            + "x"
                            + depthCells
            );
        }
    }

    public int widthBlocks() {
        return this.widthCells * DungeonLayoutConstants.CELL_SIZE_X;
    }

    public int heightBlocks() {
        return this.heightCells * DungeonLayoutConstants.CELL_SIZE_Y;
    }

    public int depthBlocks() {
        return this.depthCells * DungeonLayoutConstants.CELL_SIZE_Z;
    }

    public boolean containsCell(
            int localX,
            int localZ
    ) {
        return localX >= 0
                && localX < this.widthCells
                && localZ >= 0
                && localZ < this.depthCells;
    }

    public DungeonCellBox toCellBox(DungeonCellPos origin) {
        return new DungeonCellBox(
                origin.x(),
                origin.y(),
                origin.z(),
                this.widthCells,
                this.heightCells,
                this.depthCells
        );
    }

    public BoundingBox toBlockBounds(
            BlockPos layoutOrigin,
            DungeonCellPos origin
    ) {
        return toCellBox(origin).toBlockBounds(layoutOrigin);
    }

    public BoundingBox toBounds(
            BlockPos origin,
            int height
    ) {
        return new BoundingBox(
                origin.getX(),
                origin.getY() - 1,
                origin.getZ(),
                origin.getX() + this.widthBlocks() - 1,
                origin.getY() + height - 2,
                origin.getZ() + this.depthBlocks() - 1
        );
    }
}
