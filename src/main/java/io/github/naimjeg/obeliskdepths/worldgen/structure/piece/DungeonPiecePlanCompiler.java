package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridorDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridors;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRooms;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonSpatialLayoutValidator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonPiecePlanCompiler {
    private static final int SITE_BOUNDS_BUFFER_BLOCKS = 2;
    private static final Map<Identifier, DungeonRoomDefinition> ROOM_DEFINITIONS =
            BuiltinDungeonRoomDefinitions.all();
    private static final Map<Identifier, DungeonCorridorDefinition> CORRIDOR_DEFINITIONS =
            BuiltinDungeonCorridorDefinitions.all();

    private DungeonPiecePlanCompiler() {
    }

    public static DungeonPiecePlan compile(
            BlockPos layoutOrigin,
            DungeonLayoutPlan layout,
            String primaryEntryRoomId
    ) {
        DungeonSpatialLayoutValidator.validate(layout);

        List<DungeonPieceMetadata> roomPieces = layout.nodes()
                .stream()
                .map(node -> roomPiece(layoutOrigin, node, primaryEntryRoomId))
                .toList();
        DungeonRoutingResult routing = DungeonCorridorRouter.route(layout);
        List<DungeonPieceMetadata> corridorPieces = new ArrayList<>();

        for (DungeonRoutedCorridor corridor : routing.corridors()) {
            corridorPieces.addAll(corridorPieces(layoutOrigin, corridor));
        }

        BoundingBox union = null;

        for (DungeonPieceMetadata room : roomPieces) {
            union = include(union, room.bounds());
        }

        for (DungeonPieceMetadata corridor : corridorPieces) {
            union = include(union, corridor.bounds());
        }

        if (union == null) {
            throw new IllegalArgumentException("Cannot compile piece plan for empty layout");
        }

        List<DungeonPieceMetadata> pieces = new ArrayList<>();
        pieces.addAll(corridorPieces);
        pieces.addAll(roomPieces);

        BoundingBox siteBounds = inflate(union, SITE_BOUNDS_BUFFER_BLOCKS);
        DungeonPieceMetadata primaryEntry = roomPieces.stream()
                .filter(piece -> piece.id().equals(primaryEntryRoomId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Piece plan missing START room"))
                ;

        if (primaryEntry.role() != ObeliskDungeonPieceRole.START_ROOM) {
            throw new IllegalArgumentException(
                    "Primary entry room is not a START room: " + primaryEntryRoomId
            );
        }

        BlockPos primaryEntryAnchor = primaryEntry.anchor();

        DungeonSpatialLayoutValidator.validatePieceBounds(
                layout,
                siteBounds,
                pieces.stream().map(DungeonPieceMetadata::bounds).toList(),
                primaryEntryAnchor
        );
        validateCorridorIntersections(roomPieces, corridorPieces);

        return new DungeonPiecePlan(
                layoutOrigin,
                siteBounds,
                primaryEntryRoomId,
                primaryEntryAnchor,
                routing.corridors(),
                pieces
        );
    }

    private static DungeonPieceMetadata roomPiece(
            BlockPos layoutOrigin,
            DungeonLayoutNode node,
            String primaryEntryRoomId
    ) {
        Identifier definitionId = roomDefinitionIdFor(node.type());
        DungeonRoomDefinition definition = requireRoomDefinition(definitionId);
        DungeonCellPos pieceOrigin = definitionOriginFor(node, definition);
        BoundingBox bounds = definition.footprint()
                .toBlockBounds(layoutOrigin, pieceOrigin);
        BlockPos templateOrigin = new BlockPos(
                bounds.minX() + definition.templateOffset().getX(),
                bounds.minY() + definition.templateOffset().getY(),
                bounds.minZ() + definition.templateOffset().getZ()
        );
        BlockPos anchor = new BlockPos(
                bounds.minX() + definition.anchor().getX(),
                bounds.minY() + definition.anchor().getY(),
                bounds.minZ() + definition.anchor().getZ()
        );

        return new DungeonPieceMetadata(
                roleFor(node.type()),
                node.roomId(),
                anchor,
                bounds,
                node.roomId().equals(primaryEntryRoomId),
                Optional.of(definitionId),
                Optional.of(definition.template()),
                DungeonRoomRotation.NONE,
                false,
                templateOrigin,
                true
        );
    }

    private static DungeonCellPos definitionOriginFor(
            DungeonLayoutNode node,
            DungeonRoomDefinition definition
    ) {
        int x = alignedOrigin(
                node.cellOrigin().x(),
                node.footprint().widthCells(),
                definition.footprint().widthCells(),
                node.connectorSides().contains(DungeonConnectorSide.WEST),
                node.connectorSides().contains(DungeonConnectorSide.EAST)
        );
        int z = alignedOrigin(
                node.cellOrigin().z(),
                node.footprint().depthCells(),
                definition.footprint().depthCells(),
                node.connectorSides().contains(DungeonConnectorSide.NORTH),
                node.connectorSides().contains(DungeonConnectorSide.SOUTH)
        );

        return new DungeonCellPos(x, node.cellOrigin().y(), z);
    }

    private static int alignedOrigin(
            int plannedOrigin,
            int plannedSize,
            int definitionSize,
            boolean lowSideConnector,
            boolean highSideConnector
    ) {
        if (highSideConnector && !lowSideConnector) {
            return plannedOrigin + plannedSize - definitionSize;
        }

        if (lowSideConnector && !highSideConnector) {
            return plannedOrigin;
        }

        return plannedOrigin + Math.floorDiv(plannedSize - definitionSize, 2);
    }

    private static List<DungeonPieceMetadata> corridorPieces(
            BlockPos layoutOrigin,
            DungeonRoutedCorridor corridor
    ) {
        List<DungeonPieceMetadata> pieces = new ArrayList<>();
        List<DungeonCellPos> path = corridor.path();

        for (int index = 0; index < path.size(); index++) {
            DungeonCellPos cell = path.get(index);
            EnumSet<DungeonConnectorSide> connections =
                    corridorConnections(corridor, path, index);
            DungeonConnectorShapeType shape = shapeFor(connections);
            Identifier definitionId = corridorDefinitionIdFor(shape, index);
            DungeonCorridorDefinition definition =
                    requireCorridorDefinition(definitionId);
            DungeonRoomRotation rotation = corridorRotation(shape, connections);
            BoundingBox bounds = definition.footprint()
                    .toBlockBounds(layoutOrigin, cell);
            BlockPos templateOrigin = new BlockPos(
                    bounds.minX(),
                    bounds.minY(),
                    bounds.minZ()
            );

            pieces.add(new DungeonPieceMetadata(
                    ObeliskDungeonPieceRole.CORRIDOR,
                    corridor.edgeId() + "_cell_" + index,
                    bounds.getCenter(),
                    bounds,
                    false,
                    Optional.of(definitionId),
                    Optional.of(definition.template()),
                    rotation,
                    false,
                    templateOrigin,
                    true
            ));
        }

        return pieces;
    }

    private static void validateCorridorIntersections(
            List<DungeonPieceMetadata> roomPieces,
            List<DungeonPieceMetadata> corridorPieces
    ) {
        for (int i = 0; i < corridorPieces.size(); i++) {
            DungeonPieceMetadata first = corridorPieces.get(i);

            for (int j = i + 1; j < corridorPieces.size(); j++) {
                DungeonPieceMetadata second = corridorPieces.get(j);

                if (intersects(first.bounds(), second.bounds())) {
                    throw new IllegalArgumentException(
                            "Compiled corridors overlap: "
                                    + first.id()
                                    + " bounds="
                                    + first.bounds()
                                    + " and "
                                    + second.id()
                                    + " bounds="
                                    + second.bounds()
                    );
                }
            }

            for (DungeonPieceMetadata room : roomPieces) {
                if (intersects(first.bounds(), room.bounds())) {
                    throw new IllegalArgumentException(
                            "Compiled corridor intersects room: corridor="
                                    + first.id()
                                    + " room="
                                    + room.id()
                                    + " corridorBounds="
                                    + first.bounds()
                                    + " roomBounds="
                                    + room.bounds()
                    );
                }
            }
        }
    }

    private static BoundingBox include(
            BoundingBox current,
            BoundingBox next
    ) {
        if (current == null) {
            return next;
        }

        return new BoundingBox(
                Math.min(current.minX(), next.minX()),
                Math.min(current.minY(), next.minY()),
                Math.min(current.minZ(), next.minZ()),
                Math.max(current.maxX(), next.maxX()),
                Math.max(current.maxY(), next.maxY()),
                Math.max(current.maxZ(), next.maxZ())
        );
    }

    private static BoundingBox inflate(
            BoundingBox bounds,
            int amount
    ) {
        return new BoundingBox(
                bounds.minX() - amount,
                bounds.minY() - amount,
                bounds.minZ() - amount,
                bounds.maxX() + amount,
                bounds.maxY() + amount,
                bounds.maxZ() + amount
        );
    }

    private static boolean intersects(
            BoundingBox first,
            BoundingBox second
    ) {
        return first.minX() <= second.maxX()
                && first.maxX() >= second.minX()
                && first.minY() <= second.maxY()
                && first.maxY() >= second.minY()
                && first.minZ() <= second.maxZ()
                && first.maxZ() >= second.minZ();
    }

    private static Identifier roomDefinitionIdFor(DungeonRoomType type) {
        return switch (type) {
            case START -> BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION;
            case COMBAT -> BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION;
            case TREASURE -> BuiltinDungeonRooms.GREAT_SWAMP_TREASURE_OBELISK_SANCTUM;
            case BOSS -> BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR;
        };
    }

    private static DungeonRoomDefinition requireRoomDefinition(Identifier id) {
        DungeonRoomDefinition definition = ROOM_DEFINITIONS.get(id);

        if (definition == null) {
            throw new IllegalStateException("Missing built-in room definition: " + id);
        }

        return definition;
    }

    private static Identifier corridorDefinitionIdFor(
            DungeonConnectorShapeType shape,
            int index
    ) {
        return switch (shape) {
            case STRAIGHT -> BuiltinDungeonCorridors.STRAIGHTS.get(
                    Math.floorMod(index, BuiltinDungeonCorridors.STRAIGHTS.size())
            );
            case CORNER -> BuiltinDungeonCorridors.CORNERS.get(
                    Math.floorMod(index, BuiltinDungeonCorridors.CORNERS.size())
            );
            case T -> BuiltinDungeonCorridors.TEES.get(
                    Math.floorMod(index, BuiltinDungeonCorridors.TEES.size())
            );
            default -> BuiltinDungeonCorridors.STRAIGHTS.get(
                    Math.floorMod(index, BuiltinDungeonCorridors.STRAIGHTS.size())
            );
        };
    }

    private static DungeonCorridorDefinition requireCorridorDefinition(Identifier id) {
        DungeonCorridorDefinition definition = CORRIDOR_DEFINITIONS.get(id);

        if (definition == null) {
            throw new IllegalStateException("Missing built-in corridor definition: " + id);
        }

        return definition;
    }

    private static EnumSet<DungeonConnectorSide> corridorConnections(
            DungeonRoutedCorridor corridor,
            List<DungeonCellPos> path,
            int index
    ) {
        EnumSet<DungeonConnectorSide> connections =
                EnumSet.noneOf(DungeonConnectorSide.class);
        DungeonCellPos cell = path.get(index);

        if (index == 0) {
            connections.add(corridor.fromSide().opposite());
        } else {
            connections.add(directionBetween(cell, path.get(index - 1)));
        }

        if (index == path.size() - 1) {
            connections.add(corridor.toSide().opposite());
        } else {
            connections.add(directionBetween(cell, path.get(index + 1)));
        }

        return connections;
    }

    private static DungeonConnectorSide directionBetween(
            DungeonCellPos from,
            DungeonCellPos to
    ) {
        int dx = Integer.compare(to.x() - from.x(), 0);
        int dz = Integer.compare(to.z() - from.z(), 0);

        if (Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) != 1
                || from.y() != to.y()) {
            throw new IllegalArgumentException(
                    "Routed corridor cells must be horizontally adjacent: "
                            + from
                            + " -> "
                            + to
            );
        }

        if (dx > 0) {
            return DungeonConnectorSide.EAST;
        }
        if (dx < 0) {
            return DungeonConnectorSide.WEST;
        }
        if (dz > 0) {
            return DungeonConnectorSide.SOUTH;
        }

        return DungeonConnectorSide.NORTH;
    }

    private static DungeonConnectorShapeType shapeFor(
            EnumSet<DungeonConnectorSide> connections
    ) {
        if (connections.size() >= 3) {
            return DungeonConnectorShapeType.T;
        }

        if (connections.size() == 2) {
            boolean eastWest = connections.contains(DungeonConnectorSide.EAST)
                    && connections.contains(DungeonConnectorSide.WEST);
            boolean northSouth = connections.contains(DungeonConnectorSide.NORTH)
                    && connections.contains(DungeonConnectorSide.SOUTH);
            return eastWest || northSouth
                    ? DungeonConnectorShapeType.STRAIGHT
                    : DungeonConnectorShapeType.CORNER;
        }

        return DungeonConnectorShapeType.STRAIGHT;
    }

    private static DungeonRoomRotation corridorRotation(
            DungeonConnectorShapeType shape,
            EnumSet<DungeonConnectorSide> connections
    ) {
        return switch (shape) {
            case STRAIGHT -> connections.contains(DungeonConnectorSide.NORTH)
                    ? DungeonRoomRotation.CLOCKWISE_90
                    : DungeonRoomRotation.NONE;
            case CORNER -> cornerRotation(connections);
            case T -> teeRotation(connections);
            default -> DungeonRoomRotation.NONE;
        };
    }

    private static DungeonRoomRotation cornerRotation(
            EnumSet<DungeonConnectorSide> connections
    ) {
        if (connections.contains(DungeonConnectorSide.EAST)
                && connections.contains(DungeonConnectorSide.SOUTH)) {
            return DungeonRoomRotation.NONE;
        }
        if (connections.contains(DungeonConnectorSide.SOUTH)
                && connections.contains(DungeonConnectorSide.WEST)) {
            return DungeonRoomRotation.CLOCKWISE_90;
        }
        if (connections.contains(DungeonConnectorSide.WEST)
                && connections.contains(DungeonConnectorSide.NORTH)) {
            return DungeonRoomRotation.CLOCKWISE_180;
        }

        return DungeonRoomRotation.COUNTERCLOCKWISE_90;
    }

    private static DungeonRoomRotation teeRotation(
            EnumSet<DungeonConnectorSide> connections
    ) {
        if (!connections.contains(DungeonConnectorSide.EAST)) {
            return DungeonRoomRotation.NONE;
        }
        if (!connections.contains(DungeonConnectorSide.SOUTH)) {
            return DungeonRoomRotation.CLOCKWISE_90;
        }
        if (!connections.contains(DungeonConnectorSide.WEST)) {
            return DungeonRoomRotation.CLOCKWISE_180;
        }

        return DungeonRoomRotation.COUNTERCLOCKWISE_90;
    }

    private static ObeliskDungeonPieceRole roleFor(DungeonRoomType type) {
        return switch (type) {
            case START -> ObeliskDungeonPieceRole.START_ROOM;
            case COMBAT -> ObeliskDungeonPieceRole.COMBAT_ROOM;
            case TREASURE -> ObeliskDungeonPieceRole.TREASURE_ROOM;
            case BOSS -> ObeliskDungeonPieceRole.BOSS_ROOM;
        };
    }
}
