package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

public record DungeonLayoutEdge(
        String id,
        String fromRoomId,
        String toRoomId,
        DungeonConnectorSide fromSide,
        DungeonConnectorSide toSide,
        int widthCells
) {
    public DungeonLayoutEdge {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Layout edge id must be non-empty");
        }

        if (fromRoomId == null || fromRoomId.isBlank()) {
            throw new IllegalArgumentException("Layout edge missing from room: " + id);
        }

        if (toRoomId == null || toRoomId.isBlank()) {
            throw new IllegalArgumentException("Layout edge missing to room: " + id);
        }

        if (fromRoomId.equals(toRoomId)) {
            throw new IllegalArgumentException("Layout edge connects a room to itself: " + id);
        }

        if (fromSide == null || toSide == null) {
            throw new IllegalArgumentException("Layout edge sides must be present: " + id);
        }

        if (widthCells <= 0) {
            throw new IllegalArgumentException("Layout edge width must be positive: " + id);
        }

        if (fromSide.opposite() != toSide) {
            throw new IllegalArgumentException(
                    "Preliminary layout edges must use opposite connector sides: " + id
            );
        }
    }

    public boolean directOppositeConnection() {
        return this.fromSide.opposite() == this.toSide;
    }
}
