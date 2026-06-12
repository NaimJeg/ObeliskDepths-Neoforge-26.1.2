package io.github.naimjeg.obeliskdepths.worldgen.structure.terrain;

import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonTerrainPlanner {
    private static final int SHELL_BUFFER_BLOCKS = 2;
    private static final int MIN_CORRIDOR_WIDTH_BLOCKS = 3;
    private static final int CORRIDOR_HEIGHT_BLOCKS = 4;

    private DungeonTerrainPlanner() {
    }

    /*
     * Worldgen-side terrain planning only. Runtime must read the generated
     * StructureStart and serialized pieces after generation; it must not call
     * this planner to reconstruct room topology or fabricate metadata.
     */
    public static DungeonTerrainPlan build(
            BlockPos layoutOrigin,
            DungeonLayoutPlan layout
    ) {
        List<DungeonRoomVolume> rooms = layout.nodes().stream()
                .map(node -> roomVolume(layoutOrigin, node))
                .toList();
        List<DungeonCorridorVolume> corridors = new ArrayList<>();

        for (DungeonLayoutEdge edge : layout.edges()) {
            corridors.addAll(corridorVolumes(layout, rooms, edge));
        }

        BoundingBox union = null;

        for (DungeonRoomVolume room : rooms) {
            union = include(union, room.outerBounds());
        }

        for (DungeonCorridorVolume corridor : corridors) {
            union = include(union, corridor.bounds());
        }

        if (union == null) {
            throw new IllegalArgumentException("Cannot build terrain plan for empty layout");
        }

        return new DungeonTerrainPlan(
                layoutOrigin,
                inflate(union, SHELL_BUFFER_BLOCKS),
                rooms,
                corridors
        );
    }

    private static DungeonRoomVolume roomVolume(
            BlockPos layoutOrigin,
            DungeonLayoutNode node
    ) {
        BoundingBox outer = node.cellBox().toBlockBounds(layoutOrigin);
        BoundingBox interior = inset(outer);

        return new DungeonRoomVolume(
                node.roomId(),
                node.type(),
                outer,
                interior,
                node.blockAnchor(layoutOrigin)
        );
    }

    private static List<DungeonCorridorVolume> corridorVolumes(
            DungeonLayoutPlan layout,
            List<DungeonRoomVolume> rooms,
            DungeonLayoutEdge edge
    ) {
        if (edge.fromSide().vertical() || edge.toSide().vertical()) {
            throw new UnsupportedOperationException(
                    "Vertical dungeon corridors are not implemented yet: " + edge.id()
            );
        }

        DungeonRoomVolume from = requireRoom(rooms, edge.fromRoomId());
        DungeonRoomVolume to = requireRoom(rooms, edge.toRoomId());
        BlockPos fromCenter = connectorCenter(from.outerBounds(), edge.fromSide());
        BlockPos toCenter = connectorCenter(to.outerBounds(), edge.toSide());
        int width = Math.max(
                MIN_CORRIDOR_WIDTH_BLOCKS,
                edge.widthCells() * DungeonLayoutConstants.CELL_SIZE_X
        );

        if (fromCenter.getX() == toCenter.getX() || fromCenter.getZ() == toCenter.getZ()) {
            return List.of(new DungeonCorridorVolume(
                    edge.id(),
                    edge.fromRoomId(),
                    edge.toRoomId(),
                    corridorBounds(fromCenter, toCenter, width),
                    width
            ));
        }

        BlockPos bend = new BlockPos(toCenter.getX(), fromCenter.getY(), fromCenter.getZ());

        return List.of(
                new DungeonCorridorVolume(
                        edge.id() + "_a",
                        edge.fromRoomId(),
                        edge.toRoomId(),
                        corridorBounds(fromCenter, bend, width),
                        width
                ),
                new DungeonCorridorVolume(
                        edge.id() + "_b",
                        edge.fromRoomId(),
                        edge.toRoomId(),
                        corridorBounds(bend, toCenter, width),
                        width
                )
        );
    }

    private static DungeonRoomVolume requireRoom(
            List<DungeonRoomVolume> rooms,
            String roomId
    ) {
        return rooms.stream()
                .filter(room -> room.roomId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown terrain room: " + roomId));
    }

    private static BoundingBox inset(BoundingBox outer) {
        int minX = outer.minX() + 1;
        int minY = outer.minY() + 1;
        int minZ = outer.minZ() + 1;
        int maxX = outer.maxX() - 1;
        int maxY = outer.maxY() - 1;
        int maxZ = outer.maxZ() - 1;

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return outer;
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static BlockPos connectorCenter(
            BoundingBox bounds,
            DungeonConnectorSide side
    ) {
        int centerX = bounds.minX() + bounds.getXSpan() / 2;
        int centerY = bounds.minY() + 1;
        int centerZ = bounds.minZ() + bounds.getZSpan() / 2;

        return switch (side) {
            case NORTH -> new BlockPos(centerX, centerY, bounds.minZ());
            case SOUTH -> new BlockPos(centerX, centerY, bounds.maxZ());
            case WEST -> new BlockPos(bounds.minX(), centerY, centerZ);
            case EAST -> new BlockPos(bounds.maxX(), centerY, centerZ);
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical connector center is not supported yet: " + side
            );
        };
    }

    private static BoundingBox corridorBounds(
            BlockPos first,
            BlockPos second,
            int width
    ) {
        int halfLow = width / 2;
        int halfHigh = width - halfLow - 1;
        int minY = Math.min(first.getY(), second.getY()) - 1;
        int maxY = minY + CORRIDOR_HEIGHT_BLOCKS;

        if (first.getX() == second.getX()) {
            int x = first.getX();
            return new BoundingBox(
                    x - halfLow,
                    minY,
                    Math.min(first.getZ(), second.getZ()),
                    x + halfHigh,
                    maxY,
                    Math.max(first.getZ(), second.getZ())
            );
        }

        if (first.getZ() == second.getZ()) {
            int z = first.getZ();
            return new BoundingBox(
                    Math.min(first.getX(), second.getX()),
                    minY,
                    z - halfLow,
                    Math.max(first.getX(), second.getX()),
                    maxY,
                    z + halfHigh
            );
        }

        throw new IllegalArgumentException("Corridor segment must be axis-aligned: " + first + " -> " + second);
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
}
