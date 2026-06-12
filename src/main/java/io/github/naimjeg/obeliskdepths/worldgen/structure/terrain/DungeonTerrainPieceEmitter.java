package io.github.naimjeg.obeliskdepths.worldgen.structure.terrain;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPiece;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public final class DungeonTerrainPieceEmitter {
    private DungeonTerrainPieceEmitter() {
    }

    public static void emit(
            StructurePiecesBuilder builder,
            DungeonTerrainPlan plan
    ) {
        builder.addPiece(new ObeliskDungeonPiece(
                ObeliskDungeonPieceRole.SITE,
                "site",
                plan.origin(),
                plan.outerBounds()
        ));

        for (DungeonCorridorVolume corridor : plan.corridors()) {
            builder.addPiece(new ObeliskDungeonPiece(
                    ObeliskDungeonPieceRole.CORRIDOR,
                    corridor.id(),
                    corridor.bounds().getCenter(),
                    corridor.bounds()
            ));
        }

        for (DungeonRoomVolume room : plan.rooms()) {
            builder.addPiece(new ObeliskDungeonPiece(
                    roleFor(room.type()),
                    room.roomId(),
                    room.anchorPos(),
                    room.outerBounds()
            ));
        }
    }

    private static ObeliskDungeonPieceRole roleFor(DungeonRoomType type) {
        return switch (type) {
            case START -> ObeliskDungeonPieceRole.START_ROOM;
            case COMBAT -> ObeliskDungeonPieceRole.COMBAT_ROOM;
            case TREASURE -> ObeliskDungeonPieceRole.TREASURE_ROOM;
            case BOSS -> ObeliskDungeonPieceRole.BOSS_ROOM;
            case EXIT -> ObeliskDungeonPieceRole.EXIT_ROOM;
        };
    }
}
