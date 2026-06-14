package io.github.naimjeg.obeliskdepths.dungeon.corridor;

import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DungeonCorridorDefinitionValidator {
    private DungeonCorridorDefinitionValidator() {
    }

    public static List<String> validate(
            Identifier definitionId,
            DungeonCorridorDefinition definition
    ) {
        List<String> errors = new ArrayList<>();

        if (definitionId == null) {
            errors.add("definition id must not be null");
        }

        if (definition == null) {
            errors.add("definition must not be null");
            return List.copyOf(errors);
        }

        Set<String> portIds = new HashSet<>();
        EnumSet<DungeonConnectorSide> sides =
                EnumSet.noneOf(DungeonConnectorSide.class);

        for (RoomConnectorDefinition port : definition.ports()) {
            if (!portIds.add(port.id())) {
                errors.add("duplicate port id: " + port.id());
            }

            sides.add(port.facing());
            validatePort(definition, port, errors);
        }

        DungeonConnectorShapeType actual =
                DungeonConnectorShapeType.fromSides(sides);

        if (actual != definition.shape()) {
            errors.add("declared corridor shape "
                    + definition.shape().getSerializedName()
                    + " does not match port topology "
                    + actual.getSerializedName());
        }

        return List.copyOf(errors);
    }

    private static void validatePort(
            DungeonCorridorDefinition definition,
            RoomConnectorDefinition port,
            List<String> errors
    ) {
        DungeonRoomFootprint footprint = definition.footprint();
        DungeonCellPos cell = port.boundaryCell();
        DungeonConnectorSide side = port.facing();

        if (!footprint.containsCell(cell)) {
            errors.add("port "
                    + port.id()
                    + " boundary cell is not occupied by footprint: "
                    + cell);
            return;
        }

        DungeonCellPos outside = new DungeonCellPos(
                cell.x() + side.dx(),
                cell.y() + side.dy(),
                cell.z() + side.dz()
        );

        if (footprint.containsCell(outside)) {
            errors.add("port "
                    + port.id()
                    + " faces occupied neighbor instead of exposed "
                    + side.getSerializedName()
                    + " boundary: "
                    + outside);
        }

        if (side.vertical()) {
            errors.add("port "
                    + port.id()
                    + " uses unsupported vertical facing "
                    + side.getSerializedName()
                    + " for current generation");
        }

        validateOpening(definition, port, errors);
    }

    private static void validateOpening(
            DungeonCorridorDefinition definition,
            RoomConnectorDefinition port,
            List<String> errors
    ) {
        BlockPos opening = port.openingMin();
        DungeonRoomFootprint footprint = definition.footprint();

        if (opening.getX() < 0 || opening.getY() < 0 || opening.getZ() < 0) {
            errors.add("port "
                    + port.id()
                    + " opening_min must be non-negative: "
                    + opening);
            return;
        }

        boolean invalid = switch (port.facing()) {
            case NORTH -> opening.getZ() != 0
                    || opening.getX() + port.widthBlocks() > footprint.widthBlocks()
                    || opening.getY() + port.heightBlocks() > footprint.heightBlocks();
            case SOUTH -> opening.getZ() != footprint.depthBlocks() - 1
                    || opening.getX() + port.widthBlocks() > footprint.widthBlocks()
                    || opening.getY() + port.heightBlocks() > footprint.heightBlocks();
            case WEST -> opening.getX() != 0
                    || opening.getZ() + port.widthBlocks() > footprint.depthBlocks()
                    || opening.getY() + port.heightBlocks() > footprint.heightBlocks();
            case EAST -> opening.getX() != footprint.widthBlocks() - 1
                    || opening.getZ() + port.widthBlocks() > footprint.depthBlocks()
                    || opening.getY() + port.heightBlocks() > footprint.heightBlocks();
            case DOWN, UP -> opening.getX() + port.widthBlocks() > footprint.widthBlocks()
                    || opening.getY() + port.heightBlocks() > footprint.heightBlocks()
                    || opening.getZ() + port.widthBlocks() > footprint.depthBlocks();
        };

        if (invalid) {
            errors.add("port "
                    + port.id()
                    + " opening_min does not fit "
                    + port.facing().getSerializedName()
                    + " opening semantics: "
                    + opening);
        }
    }
}
