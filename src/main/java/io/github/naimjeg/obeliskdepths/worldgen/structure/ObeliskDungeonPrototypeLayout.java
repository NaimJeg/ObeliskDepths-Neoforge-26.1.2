package io.github.naimjeg.obeliskdepths.worldgen.structure;

import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ObeliskDungeonLayoutAlgorithm;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ObeliskDungeonLayoutValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.SolvedDungeonLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public final class ObeliskDungeonPrototypeLayout {
    private ObeliskDungeonPrototypeLayout() {
    }

    public static void addPieces(
            StructurePiecesBuilder builder,
            BlockPos startAnchor
    ) {
        SolvedDungeonLayout layout = ObeliskDungeonLayoutAlgorithm.solveLinearPrototype(startAnchor);

        ObeliskDungeonLayoutValidator.validate(layout);
        ObeliskDungeonPieceEmitter.emit(builder, layout);
    }
}
