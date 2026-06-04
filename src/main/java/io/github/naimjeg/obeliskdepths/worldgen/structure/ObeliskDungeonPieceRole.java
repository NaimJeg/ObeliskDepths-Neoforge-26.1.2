package io.github.naimjeg.obeliskdepths.worldgen.structure;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;

public enum ObeliskDungeonPieceRole {
    START_ROOM("start", DungeonRoomType.START),
    COMBAT_ROOM("combat", DungeonRoomType.COMBAT),
    TREASURE_ROOM("treasure", DungeonRoomType.TREASURE),
    EXIT_ROOM("exit", DungeonRoomType.EXIT),
    CORRIDOR("corridor", null);

    private final String serializedName;
    private final DungeonRoomType roomType;

    ObeliskDungeonPieceRole(
            String serializedName,
            DungeonRoomType roomType
    ) {
        this.serializedName = serializedName;
        this.roomType = roomType;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public DungeonRoomType roomType() {
        return this.roomType;
    }

    public boolean isRoom() {
        return this.roomType != null;
    }

    public static ObeliskDungeonPieceRole byName(String name) {
        for (ObeliskDungeonPieceRole role : values()) {
            if (role.serializedName.equals(name)) {
                return role;
            }
        }

        return START_ROOM;
    }
}