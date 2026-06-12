package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ObeliskDungeonLayoutAlgorithm {
    private ObeliskDungeonLayoutAlgorithm() {
    }

    /*
     * Prototype topology rules:
     * - dungeon topology is connected and tree-shaped: a required path plus
     *   optional branches/caps later
     * - start connects through the required path to boss/exit
     * - treasure rooms are optional branch/cap rooms
     * - corridors are pieces with restricted connectors, not arbitrary overlap
     *
     * Runtime reads the emitted room/corridor ids, bounds, anchors, and future
     * graph/connector metadata. Runtime must not regenerate topology. These flat
     * planes are temporary geometry; later .nbt templates should provide
     * connector metadata that this layout algorithm can snap together.
     */
    public static SolvedDungeonLayout solveLinearPrototype(BlockPos startAnchor) {
        BlockPos layoutOrigin = startAnchor.offset(
                -DungeonLayoutConstants.CELL_SIZE,
                -1,
                -DungeonLayoutConstants.CELL_SIZE
        );
        DungeonLayoutPlan plan = PreliminaryDungeonLayoutPlanner.plan(layoutOrigin);

        return solvePlan(layoutOrigin, plan);
    }

    public static SolvedDungeonLayout solvePlan(
            BlockPos layoutOrigin,
            DungeonLayoutPlan plan
    ) {
        List<SolvedDungeonRoom> rooms = plan.nodes()
                .stream()
                .map(node -> solveRoom(layoutOrigin, node))
                .toList();

        SolvedDungeonLayout roomsOnly = new SolvedDungeonLayout(rooms, List.of());
        List<SolvedDungeonCorridor> corridors = new ArrayList<>();

        for (DungeonLayoutEdge edge : plan.edges()) {
            corridors.add(solveCorridor(roomsOnly, edge));
        }

        return new SolvedDungeonLayout(rooms, corridors);
    }

    private static SolvedDungeonRoom solveRoom(
            BlockPos layoutOrigin,
            DungeonLayoutNode node
    ) {
        BlockPos anchor = node.blockAnchor(layoutOrigin);
        DungeonRoomSpec spec = new DungeonRoomSpec(
                node.roomId(),
                roleFor(node.type()),
                node.footprint().widthBlocks(),
                node.footprint().heightBlocks(),
                node.footprint().depthBlocks()
        );
        BoundingBox bounds = node.footprint().toBlockBounds(layoutOrigin, node.cellOrigin());

        ObeliskDepths.LOGGER.debug(
                "[OD layout] node id={} type={} footprint={}x{}x{} connectors={} shape={} criticalPath={} branchCap={}",
                node.roomId(),
                node.type(),
                node.footprint().widthCells(),
                node.footprint().heightCells(),
                node.footprint().depthCells(),
                node.connectorSides(),
                node.connectorShapeType(),
                node.criticalPath(),
                node.branchCap()
        );

        SolvedDungeonRoom room = new SolvedDungeonRoom(spec, anchor, bounds);
        ObeliskDepths.LOGGER.debug(
                "[OD layout] room id={} role={} anchor={} bounds={}",
                spec.id(),
                spec.role(),
                anchor,
                bounds
        );
        return room;
    }

    private static SolvedDungeonCorridor solveCorridor(
            SolvedDungeonLayout layout,
            DungeonLayoutEdge edge
    ) {
        SolvedDungeonRoom from = layout.room(edge.fromRoomId());
        SolvedDungeonRoom to = layout.room(edge.toRoomId());
        DungeonConnectionSpec spec = new DungeonConnectionSpec(
                edge.id(),
                edge.fromRoomId(),
                edge.toRoomId(),
                edge.widthCells() * DungeonLayoutConstants.CELL_SIZE
        );

        if (edge.fromSide().dx() != 0) {
            return solveCorridorX(from, to, edge, spec);
        }

        return solveCorridorZ(from, to, edge, spec);
    }

    private static SolvedDungeonCorridor solveCorridorX(
            SolvedDungeonRoom from,
            SolvedDungeonRoom to,
            DungeonLayoutEdge edge,
            DungeonConnectionSpec spec
    ) {
        SolvedDungeonRoom left = from.bounds().minX() <= to.bounds().minX() ? from : to;
        SolvedDungeonRoom right = left == from ? to : from;
        int minX = left.bounds().maxX() + 1;
        int maxX = right.bounds().minX() - 1;

        if (maxX < minX) {
            throw new IllegalStateException(
                    "Invalid corridor gap for "
                            + edge.id()
                            + ": left="
                            + left.spec().id()
                            + " bounds="
                            + left.bounds()
                            + ", right="
                            + right.spec().id()
                            + " bounds="
                            + right.bounds()
            );
        }

        int centerZ = from.anchor().getZ();
        int widthBlocks = edge.widthCells() * DungeonLayoutConstants.CELL_SIZE;
        int minZ = centerZ - widthBlocks / 2;
        int minY = left.bounds().minY();
        int maxY = minY + DungeonLayoutConstants.CELL_SIZE_Y - 1;

        BoundingBox bounds = new BoundingBox(
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                minZ + widthBlocks - 1
        );

        BlockPos anchor = new BlockPos(
                (minX + maxX) / 2,
                left.anchor().getY(),
                minZ + widthBlocks / 2
        );

        SolvedDungeonCorridor corridor = new SolvedDungeonCorridor(spec, anchor, bounds);
        ObeliskDepths.LOGGER.debug(
                "[OD layout] corridor id={} from={} to={} anchor={} bounds={}",
                spec.id(),
                spec.fromRoomId(),
                spec.toRoomId(),
                anchor,
                bounds
        );
        return corridor;
    }

    private static SolvedDungeonCorridor solveCorridorZ(
            SolvedDungeonRoom from,
            SolvedDungeonRoom to,
            DungeonLayoutEdge edge,
            DungeonConnectionSpec spec
    ) {
        SolvedDungeonRoom north = from.bounds().minZ() <= to.bounds().minZ() ? from : to;
        SolvedDungeonRoom south = north == from ? to : from;
        int minZ = north.bounds().maxZ() + 1;
        int maxZ = south.bounds().minZ() - 1;

        if (maxZ < minZ) {
            throw new IllegalStateException(
                    "Invalid corridor gap for "
                            + edge.id()
                            + ": north="
                            + north.spec().id()
                            + " bounds="
                            + north.bounds()
                            + ", south="
                            + south.spec().id()
                            + " bounds="
                            + south.bounds()
            );
        }

        int centerX = from.anchor().getX();
        int widthBlocks = edge.widthCells() * DungeonLayoutConstants.CELL_SIZE;
        int minX = centerX - widthBlocks / 2;
        int minY = north.bounds().minY();
        int maxY = minY + DungeonLayoutConstants.CELL_SIZE_Y - 1;

        BoundingBox bounds = new BoundingBox(
                minX,
                minY,
                minZ,
                minX + widthBlocks - 1,
                maxY,
                maxZ
        );

        BlockPos anchor = new BlockPos(
                minX + widthBlocks / 2,
                north.anchor().getY(),
                (minZ + maxZ) / 2
        );

        SolvedDungeonCorridor corridor = new SolvedDungeonCorridor(spec, anchor, bounds);
        ObeliskDepths.LOGGER.debug(
                "[OD layout] corridor id={} from={} to={} anchor={} bounds={}",
                spec.id(),
                spec.fromRoomId(),
                spec.toRoomId(),
                anchor,
                bounds
        );
        return corridor;
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
