package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

public final class DungeonLayoutConstants {
    /*
     * Layout-grid volume, not a universal room size.
     *
     * A layout cell is an 8x8x8 volume. Current shallow generation uses one
     * vertical layer with heightCells=1. Future generators may add UP/DOWN
     * connectors, stairs, shafts, stacked branches, or multi-level rooms.
     *
     * Rooms express physical footprint in cell counts. Block-space conversion
     * happens after layout placement so future .nbt template snapping can keep
     * the same graph metadata contract.
     */
    public static final int CELL_SIZE_X = 8;
    public static final int CELL_SIZE_Y = 8;
    public static final int CELL_SIZE_Z = 8;

    public static final int CELL_SIZE = CELL_SIZE_X;

    private DungeonLayoutConstants() {
    }
}
