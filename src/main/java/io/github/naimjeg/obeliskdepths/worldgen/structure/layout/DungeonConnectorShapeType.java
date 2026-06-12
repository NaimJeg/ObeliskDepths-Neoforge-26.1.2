package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import java.util.EnumSet;

public enum DungeonConnectorShapeType {
    CAP,
    STRAIGHT,
    CORNER,
    T,
    CROSS,
    VERTICAL_CAP,
    VERTICAL_THROUGH,
    MIXED_3D;

    public static DungeonConnectorShapeType fromSides(EnumSet<DungeonConnectorSide> sides) {
        boolean hasVertical = sides.stream().anyMatch(DungeonConnectorSide::vertical);

        if (hasVertical) {
            if (sides.size() == 1) {
                return VERTICAL_CAP;
            }

            if (sides.size() == 2
                    && sides.contains(DungeonConnectorSide.UP)
                    && sides.contains(DungeonConnectorSide.DOWN)) {
                return VERTICAL_THROUGH;
            }

            return MIXED_3D;
        }

        return switch (sides.size()) {
            case 1 -> CAP;
            case 2 -> sides.stream().anyMatch(side -> sides.contains(side.opposite()))
                    ? STRAIGHT
                    : CORNER;
            case 3 -> T;
            case 4 -> CROSS;
            default -> throw new IllegalArgumentException(
                    "Connector shape requires 1-4 sides: " + sides
            );
        };
    }
}
