package io.github.naimjeg.obeliskdepths.worldgen.structure;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.SolvedDungeonCorridor;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.SolvedDungeonLayout;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.SolvedDungeonRoom;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public final class ObeliskDungeonPieceEmitter {
    private ObeliskDungeonPieceEmitter() {
    }

    public static void emit(
            StructurePiecesBuilder builder,
            SolvedDungeonLayout layout
    ) {
        for (SolvedDungeonRoom room : layout.rooms()) {
            ObeliskDepths.LOGGER.debug(
                    "[OD layout] emit room id={} role={} anchor={} bounds={}",
                    room.spec().id(),
                    room.spec().role(),
                    room.anchor(),
                    room.bounds()
            );
            builder.addPiece(roomPiece(room));
        }

        for (SolvedDungeonCorridor corridor : layout.corridors()) {
            ObeliskDepths.LOGGER.debug(
                    "[OD layout] emit corridor id={} from={} to={} anchor={} bounds={}",
                    corridor.spec().id(),
                    corridor.spec().fromRoomId(),
                    corridor.spec().toRoomId(),
                    corridor.anchor(),
                    corridor.bounds()
            );
            builder.addPiece(corridorPiece(corridor));
        }
    }

    private static ObeliskDungeonPiece roomPiece(SolvedDungeonRoom room) {
        return new ObeliskDungeonPiece(
                room.spec().role(),
                room.spec().id(),
                room.anchor(),
                room.bounds()
        );
    }

    private static ObeliskDungeonPiece corridorPiece(SolvedDungeonCorridor corridor) {
        return new ObeliskDungeonPiece(
                ObeliskDungeonPieceRole.CORRIDOR,
                corridor.spec().id(),
                corridor.anchor(),
                corridor.bounds()
        );
    }
}
