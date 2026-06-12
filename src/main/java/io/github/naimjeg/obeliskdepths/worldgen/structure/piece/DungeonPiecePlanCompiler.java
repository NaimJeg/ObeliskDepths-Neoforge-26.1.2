package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellBox;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonSpatialLayoutValidator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonPiecePlanCompiler {
    private static final int SITE_BOUNDS_BUFFER_BLOCKS = 2;

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
        BoundingBox bounds = node.cellBox().toBlockBounds(layoutOrigin);

        return new DungeonPieceMetadata(
                roleFor(node.type()),
                node.roomId(),
                node.blockAnchor(layoutOrigin),
                bounds,
                node.roomId().equals(primaryEntryRoomId)
        );
    }

    private static List<DungeonPieceMetadata> corridorPieces(
            BlockPos layoutOrigin,
            DungeonRoutedCorridor corridor
    ) {
        List<DungeonPieceMetadata> pieces = new ArrayList<>();
        List<DungeonCellPos> path = corridor.path();
        int segmentStart = 0;
        int segmentIndex = 0;

        for (int index = 1; index <= path.size(); index++) {
            boolean end = index == path.size();
            boolean directionChanged = false;

            if (!end && index > segmentStart + 1) {
                DungeonCellPos previous = path.get(index - 1);
                DungeonCellPos current = path.get(index);
                DungeonCellPos segmentPrevious = path.get(segmentStart + 1);
                DungeonCellPos segmentStartPos = path.get(segmentStart);
                int currentDx = Integer.compare(current.x() - previous.x(), 0);
                int currentDz = Integer.compare(current.z() - previous.z(), 0);
                int segmentDx = Integer.compare(segmentPrevious.x() - segmentStartPos.x(), 0);
                int segmentDz = Integer.compare(segmentPrevious.z() - segmentStartPos.z(), 0);
                directionChanged = currentDx != segmentDx || currentDz != segmentDz;
            }

            if (end || directionChanged) {
                int segmentEnd = directionChanged ? index - 1 : index - 1;
                BoundingBox bounds = cellSegmentBounds(
                        layoutOrigin,
                        path.get(segmentStart),
                        path.get(segmentEnd)
                );
                pieces.add(new DungeonPieceMetadata(
                        ObeliskDungeonPieceRole.CORRIDOR,
                        corridor.edgeId() + "_segment_" + segmentIndex++,
                        bounds.getCenter(),
                        bounds,
                        false
                ));
                segmentStart = directionChanged ? index : segmentEnd;
            }
        }

        return pieces;
    }

    private static BoundingBox cellSegmentBounds(
            BlockPos layoutOrigin,
            DungeonCellPos first,
            DungeonCellPos second
    ) {
        int minX = Math.min(first.x(), second.x());
        int minZ = Math.min(first.z(), second.z());
        int sizeX = Math.abs(first.x() - second.x()) + 1;
        int sizeZ = Math.abs(first.z() - second.z()) + 1;

        if (first.x() != second.x() && first.z() != second.z()) {
            throw new IllegalArgumentException("Routed corridor segment is not axis-aligned: " + first + " -> " + second);
        }

        return new DungeonCellBox(minX, first.y(), minZ, sizeX, 1, sizeZ)
                .toBlockBounds(layoutOrigin);
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
