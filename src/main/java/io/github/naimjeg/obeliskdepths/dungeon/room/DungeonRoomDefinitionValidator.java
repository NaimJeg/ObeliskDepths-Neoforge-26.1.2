package io.github.naimjeg.obeliskdepths.dungeon.room;

import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DungeonRoomDefinitionValidator {
    private DungeonRoomDefinitionValidator() {
    }

    public static List<String> validate(
            Identifier definitionId,
            DungeonRoomDefinition definition
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

        for (RoomConnectorDefinition port : definition.ports()) {
            if (!portIds.add(port.id())) {
                errors.add("duplicate port id: " + port.id());
            }

            validatePort(definition, port, errors);
        }

        if (definition.type() == DungeonRoomType.START
                && definition.ports().stream().noneMatch(
                DungeonRoomDefinitionValidator::horizontalUsablePort
        )) {
            errors.add("START room requires at least one horizontal port");
        }

        if ((definition.type() == DungeonRoomType.COMBAT
                || definition.type() == DungeonRoomType.BOSS)
                && definition.ports().stream().noneMatch(
                DungeonRoomDefinitionValidator::horizontalUsablePort
        )) {
            errors.add(definition.type()
                    + " room requires at least one horizontal port");
        }

        return List.copyOf(errors);
    }

    private static void validatePort(
            DungeonRoomDefinition definition,
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
            DungeonRoomDefinition definition,
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

    private static boolean horizontalUsablePort(RoomConnectorDefinition port) {
        return port != null && !port.facing().vertical();
    }
}
