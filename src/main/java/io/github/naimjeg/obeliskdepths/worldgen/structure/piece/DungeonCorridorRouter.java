package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellBox;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class DungeonCorridorRouter {
    public static final int ROOM_CLEARANCE_CELLS = 0;
    public static final int MAX_INDIVIDUAL_ROUTE_CELLS = 80;
    public static final int MAX_TOTAL_ROUTE_CELLS = 640;
    private static final int SEARCH_PADDING_CELLS = 12;

    private static final List<DungeonConnectorSide> HORIZONTAL_SIDES = List.of(
            DungeonConnectorSide.NORTH,
            DungeonConnectorSide.EAST,
            DungeonConnectorSide.SOUTH,
            DungeonConnectorSide.WEST
    );

    private DungeonCorridorRouter() {
    }

    public static DungeonRoutingResult route(DungeonLayoutPlan plan) {
        Map<String, DungeonLayoutNode> nodes = new LinkedHashMap<>();
        for (DungeonLayoutNode node : plan.nodes()) {
            nodes.put(node.roomId(), node);
        }

        List<DungeonLayoutEdge> orderedEdges = plan.edges()
                .stream()
                .sorted(edgeOrder())
                .toList();
        Bounds bounds = searchBounds(plan.nodes());
        Set<Cell> reserved = new HashSet<>();
        List<DungeonRoutedCorridor> routed = new ArrayList<>();
        int totalLength = 0;

        for (DungeonLayoutEdge edge : orderedEdges) {
            DungeonLayoutNode from = nodes.get(edge.fromRoomId());
            DungeonLayoutNode to = nodes.get(edge.toRoomId());

            if (from == null || to == null) {
                throw new IllegalArgumentException("Cannot route corridor with missing endpoint: " + edge.id());
            }

            DungeonRoutedCorridor corridor = routeEdge(plan.nodes(), reserved, bounds, edge, from, to)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No non-overlapping route for corridor "
                                    + edge.id()
                                    + " from="
                                    + edge.fromRoomId()
                                    + " to="
                                    + edge.toRoomId()
                                    + " fromBox="
                                    + from.cellBox()
                                    + " toBox="
                                    + to.cellBox()
                                    + " reservedCells="
                                    + reserved.size()
                    ));

            if (corridor.lengthCells() > MAX_INDIVIDUAL_ROUTE_CELLS) {
                throw new IllegalArgumentException(
                        "Corridor route exceeds maximum length: "
                                + corridor.edgeId()
                                + " lengthCells="
                                + corridor.lengthCells()
                );
            }

            totalLength += corridor.lengthCells();
            if (totalLength > MAX_TOTAL_ROUTE_CELLS) {
                throw new IllegalArgumentException("Total corridor route length exceeds budget: " + totalLength);
            }

            for (DungeonCellPos pos : corridor.path()) {
                reserved.add(new Cell(pos.x(), pos.z()));
            }
            routed.add(corridor);
        }

        return new DungeonRoutingResult(routed);
    }

    private static Optional<DungeonRoutedCorridor> routeEdge(
            List<DungeonLayoutNode> rooms,
            Set<Cell> reserved,
            Bounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to
    ) {
        List<DungeonConnectorSide> fromSides = orderedSides(from, to);
        List<DungeonConnectorSide> toSides = orderedSides(to, from);

        for (DungeonConnectorSide fromSide : fromSides) {
            List<Cell> startPorts = orderedPorts(from, fromSide, to)
                    .stream()
                    .filter(port -> !insideAnyRoom(rooms, port))
                    .toList();

            for (DungeonConnectorSide toSide : toSides) {
                List<Cell> goalPorts = orderedPorts(to, toSide, from)
                        .stream()
                        .filter(port -> !insideAnyRoom(rooms, port))
                        .toList();

                for (Cell start : startPorts) {
                    for (Cell goal : goalPorts) {
                        List<Cell> path = bfs(rooms, reserved, bounds, from.roomId(), to.roomId(), start, goal);

                        if (path.isEmpty()) {
                            continue;
                        }

                        return Optional.of(new DungeonRoutedCorridor(
                                edge.id(),
                                edge.fromRoomId(),
                                edge.toRoomId(),
                                fromSide,
                                toSide,
                                edge.kind(),
                                path.stream()
                                        .map(cell -> new DungeonCellPos(cell.x(), 0, cell.z()))
                                        .toList()
                        ));
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static List<Cell> bfs(
            List<DungeonLayoutNode> rooms,
            Set<Cell> reserved,
            Bounds bounds,
            String fromRoomId,
            String toRoomId,
            Cell start,
            Cell goal
    ) {
        Set<Cell> blocked = blockedCells(rooms, fromRoomId, toRoomId);
        blocked.addAll(reserved);

        if (reserved.contains(start) || reserved.contains(goal)) {
            return List.of();
        }

        blocked.remove(start);
        blocked.remove(goal);

        Queue<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> previous = new HashMap<>();
        Set<Cell> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Cell current = queue.remove();

            if (current.equals(goal)) {
                return reconstruct(previous, current);
            }

            if (previous.size() > MAX_INDIVIDUAL_ROUTE_CELLS * MAX_INDIVIDUAL_ROUTE_CELLS) {
                break;
            }

            for (Cell next : orderedNeighbors(current, goal)) {
                if (!bounds.contains(next) || blocked.contains(next) || !visited.add(next)) {
                    continue;
                }

                previous.put(next, current);
                queue.add(next);
            }
        }

        return List.of();
    }

    private static List<Cell> reconstruct(
            Map<Cell, Cell> previous,
            Cell current
    ) {
        ArrayList<Cell> path = new ArrayList<>();
        path.add(current);

        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.add(current);
        }

        java.util.Collections.reverse(path);
        return path;
    }

    private static Set<Cell> blockedCells(
            List<DungeonLayoutNode> rooms,
            String fromRoomId,
            String toRoomId
    ) {
        Set<Cell> blocked = new HashSet<>();

        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box = room.cellBox().expanded(ROOM_CLEARANCE_CELLS);

            for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    blocked.add(new Cell(x, z));
                }
            }

            if (room.roomId().equals(fromRoomId) || room.roomId().equals(toRoomId)) {
                for (DungeonConnectorSide side : HORIZONTAL_SIDES) {
                    blocked.removeAll(exteriorPorts(room, side));
                }
            }
        }

        return blocked;
    }

    private static boolean insideAnyRoom(
            List<DungeonLayoutNode> rooms,
            Cell cell
    ) {
        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box = room.cellBox();

            if (cell.x() >= box.minX()
                    && cell.x() < box.maxXExclusive()
                    && cell.z() >= box.minZ()
                    && cell.z() < box.maxZExclusive()) {
                return true;
            }
        }

        return false;
    }

    private static List<Cell> orderedNeighbors(
            Cell current,
            Cell goal
    ) {
        List<Cell> neighbors = new ArrayList<>(List.of(
                new Cell(current.x() + 1, current.z()),
                new Cell(current.x(), current.z() + 1),
                new Cell(current.x() - 1, current.z()),
                new Cell(current.x(), current.z() - 1)
        ));
        neighbors.sort(Comparator
                .comparingInt((Cell cell) -> Math.abs(cell.x() - goal.x()) + Math.abs(cell.z() - goal.z()))
                .thenComparingInt(Cell::x)
                .thenComparingInt(Cell::z));
        return neighbors;
    }

    private static List<DungeonConnectorSide> orderedSides(
            DungeonLayoutNode source,
            DungeonLayoutNode target
    ) {
        DungeonConnectorSide preferred = preferredSide(source, target);
        ArrayList<DungeonConnectorSide> sides = new ArrayList<>(HORIZONTAL_SIDES);
        sides.sort(Comparator
                .comparingInt((DungeonConnectorSide side) -> side == preferred ? 0 : side == preferred.opposite() ? 2 : 1)
                .thenComparingInt(Enum::ordinal));
        return sides;
    }

    private static DungeonConnectorSide preferredSide(
            DungeonLayoutNode source,
            DungeonLayoutNode target
    ) {
        int sourceCenterX = source.cellOrigin().x() + source.footprint().widthCells() / 2;
        int sourceCenterZ = source.cellOrigin().z() + source.footprint().depthCells() / 2;
        int targetCenterX = target.cellOrigin().x() + target.footprint().widthCells() / 2;
        int targetCenterZ = target.cellOrigin().z() + target.footprint().depthCells() / 2;
        int dx = targetCenterX - sourceCenterX;
        int dz = targetCenterZ - sourceCenterZ;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? DungeonConnectorSide.EAST : DungeonConnectorSide.WEST;
        }

        return dz >= 0 ? DungeonConnectorSide.SOUTH : DungeonConnectorSide.NORTH;
    }

    private static List<Cell> orderedPorts(
            DungeonLayoutNode room,
            DungeonConnectorSide side,
            DungeonLayoutNode target
    ) {
        Cell targetCenter = roomCenter(target);
        return exteriorPorts(room, side)
                .stream()
                .sorted(Comparator
                        .comparingInt((Cell cell) -> Math.abs(cell.x() - targetCenter.x()) + Math.abs(cell.z() - targetCenter.z()))
                        .thenComparingInt(Cell::x)
                        .thenComparingInt(Cell::z))
                .toList();
    }

    private static List<Cell> exteriorPorts(
            DungeonLayoutNode room,
            DungeonConnectorSide side
    ) {
        DungeonCellBox box = room.cellBox();
        List<Cell> ports = new ArrayList<>();

        switch (side) {
            case NORTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    ports.add(new Cell(x, box.minZ() - 1));
                }
            }
            case SOUTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    ports.add(new Cell(x, box.maxZExclusive()));
                }
            }
            case WEST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    ports.add(new Cell(box.minX() - 1, z));
                }
            }
            case EAST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    ports.add(new Cell(box.maxXExclusive(), z));
                }
            }
            case UP, DOWN -> throw new UnsupportedOperationException("Vertical routing is not supported: " + side);
        }

        return ports;
    }

    private static Cell roomCenter(DungeonLayoutNode room) {
        DungeonCellBox box = room.cellBox();
        return new Cell(
                box.minX() + box.sizeX() / 2,
                box.minZ() + box.sizeZ() / 2
        );
    }

    private static Comparator<DungeonLayoutEdge> edgeOrder() {
        return Comparator
                .comparingInt(DungeonCorridorRouter::edgePriority)
                .thenComparing(DungeonLayoutEdge::id);
    }

    private static int edgePriority(DungeonLayoutEdge edge) {
        if (edge.kind() == DungeonGraphEdgeKind.TREE
                && ((edge.fromRoomId().equals("boss") && edge.toRoomId().equals("exit"))
                || (edge.fromRoomId().equals("exit") && edge.toRoomId().equals("boss")))) {
            return 0;
        }

        return switch (edge.kind()) {
            case TREE -> 1;
            case LOOP -> 2;
            case SECRET -> 3;
        };
    }

    private static Bounds searchBounds(List<DungeonLayoutNode> rooms) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box = room.cellBox();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxXExclusive());
            maxZ = Math.max(maxZ, box.maxZExclusive());
        }

        return new Bounds(
                minX - SEARCH_PADDING_CELLS,
                minZ - SEARCH_PADDING_CELLS,
                maxX + SEARCH_PADDING_CELLS,
                maxZ + SEARCH_PADDING_CELLS
        );
    }

    private record Cell(
            int x,
            int z
    ) {
    }

    private record Bounds(
            int minX,
            int minZ,
            int maxXExclusive,
            int maxZExclusive
    ) {
        private boolean contains(Cell cell) {
            return cell.x() >= this.minX
                    && cell.x() < this.maxXExclusive
                    && cell.z() >= this.minZ
                    && cell.z() < this.maxZExclusive;
        }
    }
}
