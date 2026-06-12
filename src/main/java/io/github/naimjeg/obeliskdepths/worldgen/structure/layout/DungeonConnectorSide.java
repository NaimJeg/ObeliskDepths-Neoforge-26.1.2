package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

public enum DungeonConnectorSide {
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    DungeonConnectorSide(
            int dx,
            int dy,
            int dz
    ) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public DungeonConnectorSide opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    public int dx() {
        return this.dx;
    }

    public int dy() {
        return this.dy;
    }

    public int dz() {
        return this.dz;
    }

    public boolean vertical() {
        return this == UP || this == DOWN;
    }
}
