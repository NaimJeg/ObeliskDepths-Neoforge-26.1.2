package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellBox;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class DungeonCorridorRouter {
    public static final int ROOM_CLEARANCE_CELLS = 0;
    public static final int MAX_INDIVIDUAL_ROUTE_CELLS = 32;
    public static final int MAX_TOTAL_ROUTE_CELLS = 256;

    private static final int SEARCH_PADDING_CELLS = 8;
    private static final int MAX_EXPLORED_STATES = 24_000;
    private static final int MOVE_COST = 10;
    private static final int TURN_COST = 24;
    private static final int NEAR_UNRELATED_ROOM_COST = 12;
    private static final int OUTSIDE_LAYOUT_HULL_COST = 8;
    private static final int UNPLANNED_SIDE_COST = 80;
    private static final int MAX_ROUTE_TURNS = 3;
    private static final int ROUTE_STRETCH_ALLOWANCE = 4;
    private static final double MAX_ROUTE_STRETCH = 1.75D;

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
                .sorted(edgeOrder(nodes))
                .toList();
        RoutingBounds bounds = searchBounds(plan.nodes());
        Set<Cell> reserved = new HashSet<>();
        List<DungeonRoutedCorridor> routed = new ArrayList<>();
        int totalLength = 0;

        for (DungeonLayoutEdge edge : orderedEdges) {
            DungeonLayoutNode from = nodes.get(edge.fromRoomId());
            DungeonLayoutNode to = nodes.get(edge.toRoomId());

            if (from == null || to == null) {
                throw new IllegalArgumentException(
                        "Cannot route corridor with missing endpoint: " + edge.id()
                );
            }

            RouteCandidate candidate = routeEdge(
                    plan.nodes(),
                    reserved,
                    bounds,
                    edge,
                    from,
                    to
            ).orElseThrow(() -> new IllegalArgumentException(
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
            DungeonRoutedCorridor corridor = candidate.corridor();

            validateRouteQuality(
                    corridor,
                    candidate.start(),
                    candidate.goal()
            );

            totalLength += corridor.lengthCells();
            if (totalLength > MAX_TOTAL_ROUTE_CELLS) {
                throw new IllegalArgumentException(
                        "Total corridor route length exceeds budget: "
                                + totalLength
                );
            }

            for (DungeonCellPos pos : corridor.path()) {
                reserved.add(new Cell(pos.x(), pos.z()));
            }
            routed.add(corridor);
        }

        return new DungeonRoutingResult(routed);
    }

    private static Optional<RouteCandidate> routeEdge(
            List<DungeonLayoutNode> rooms,
            Set<Cell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to
    ) {
        if (!edge.plannedPath().isEmpty()) {
            return plannedRoute(
                    rooms,
                    reserved,
                    bounds,
                    edge,
                    from,
                    to
            );
        }

        if (edge.kind() == DungeonGraphEdgeKind.TREE) {
            Optional<RouteCandidate> direct = directTreeRoute(
                    rooms,
                    reserved,
                    bounds,
                    edge,
                    from,
                    to
            );

            if (direct.isPresent()) {
                return direct;
            }
        }

        Optional<RouteCandidate> planned = bestRouteForSides(
                rooms,
                reserved,
                bounds,
                edge,
                from,
                to,
                List.of(edge.fromSide()),
                List.of(edge.toSide())
        );

        if (planned.isPresent()) {
            return planned;
        }

        // Keep the layout authoritative: alternatives are considered only as a
        // failure recovery path, and they are heavily penalized.
        return bestRouteForSides(
                rooms,
                reserved,
                bounds,
                edge,
                from,
                to,
                orderedSides(from, to, edge.fromSide()),
                orderedSides(to, from, edge.toSide())
        );
    }

    private static Optional<RouteCandidate> plannedRoute(
            List<DungeonLayoutNode> rooms,
            Set<Cell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to
    ) {
        List<Cell> path = edge.plannedPath()
                .stream()
                .map(pos -> new Cell(pos.x(), pos.z()))
                .toList();

        if (path.isEmpty()) {
            return Optional.empty();
        }

        Cell start = path.getFirst();
        Cell goal = path.getLast();

        if (!exteriorPorts(from, edge.fromSide()).contains(start)
                || !exteriorPorts(to, edge.toSide()).contains(goal)) {
            throw new IllegalArgumentException(
                    "Planned corridor endpoints do not match connector sides: "
                            + edge.id()
            );
        }

        for (Cell cell : path) {
            if (!bounds.containsSearch(cell)
                    || reserved.contains(cell)
                    || insideAnyRoom(rooms, cell)) {
                throw new IllegalArgumentException(
                        "Planned corridor is obstructed: edge="
                                + edge.id()
                                + " cell="
                                + cell
                );
            }
        }

        DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                edge.id(),
                edge.fromRoomId(),
                edge.toRoomId(),
                edge.fromSide(),
                edge.toSide(),
                edge.kind(),
                edge.plannedPath()
        );
        return Optional.of(new RouteCandidate(
                corridor,
                start,
                goal,
                path.size() * MOVE_COST
                        + countCellTurns(path) * TURN_COST
        ));
    }

    private static Optional<RouteCandidate> directTreeRoute(
            List<DungeonLayoutNode> rooms,
            Set<Cell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to
    ) {
        Set<Cell> blocked = blockedCells(
                rooms,
                from.roomId(),
                to.roomId()
        );
        blocked.addAll(reserved);
        RouteCandidate best = null;

        for (Cell start : orderedPorts(from, edge.fromSide(), to)) {
            for (Cell goal : orderedPorts(to, edge.toSide(), from)) {
                List<Cell> path = axisAlignedPath(start, goal);

                if (path.isEmpty()) {
                    continue;
                }

                boolean legal = true;
                for (Cell cell : path) {
                    if (!bounds.containsSearch(cell)
                            || blocked.contains(cell)) {
                        legal = false;
                        break;
                    }
                }

                if (!legal) {
                    continue;
                }

                DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                        edge.id(),
                        edge.fromRoomId(),
                        edge.toRoomId(),
                        edge.fromSide(),
                        edge.toSide(),
                        edge.kind(),
                        path.stream()
                                .map(cell -> new DungeonCellPos(
                                        cell.x(),
                                        0,
                                        cell.z()
                                ))
                                .toList()
                );
                RouteCandidate candidate = new RouteCandidate(
                        corridor,
                        start,
                        goal,
                        path.size() * MOVE_COST
                );

                if (best == null
                        || routeCandidateOrder().compare(candidate, best) < 0) {
                    best = candidate;
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static List<Cell> axisAlignedPath(
            Cell start,
            Cell goal
    ) {
        List<Cell> path = new ArrayList<>();

        if (start.x() == goal.x()) {
            for (int z = Math.min(start.z(), goal.z());
                 z <= Math.max(start.z(), goal.z());
                 z++) {
                path.add(new Cell(start.x(), z));
            }
        } else if (start.z() == goal.z()) {
            for (int x = Math.min(start.x(), goal.x());
                 x <= Math.max(start.x(), goal.x());
                 x++) {
                path.add(new Cell(x, start.z()));
            }
        }

        if (!path.isEmpty() && !path.get(0).equals(start)) {
            java.util.Collections.reverse(path);
        }

        return path;
    }

    private static Optional<RouteCandidate> bestRouteForSides(
            List<DungeonLayoutNode> rooms,
            Set<Cell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to,
            List<DungeonConnectorSide> fromSides,
            List<DungeonConnectorSide> toSides
    ) {
        RouteCandidate best = null;

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
                        List<Cell> path = aStar(
                                rooms,
                                reserved,
                                bounds,
                                from.roomId(),
                                to.roomId(),
                                start,
                                goal
                        );

                        if (path.isEmpty()) {
                            continue;
                        }

                        int sidePenalty = 0;
                        if (fromSide != edge.fromSide()) {
                            sidePenalty += UNPLANNED_SIDE_COST;
                        }
                        if (toSide != edge.toSide()) {
                            sidePenalty += UNPLANNED_SIDE_COST;
                        }

                        int score = path.size() * MOVE_COST
                                + countCellTurns(path) * TURN_COST
                                + sidePenalty;
                        DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                                edge.id(),
                                edge.fromRoomId(),
                                edge.toRoomId(),
                                fromSide,
                                toSide,
                                edge.kind(),
                                path.stream()
                                        .map(cell -> new DungeonCellPos(
                                                cell.x(),
                                                0,
                                                cell.z()
                                        ))
                                        .toList()
                        );
                        RouteCandidate candidate = new RouteCandidate(
                                corridor,
                                start,
                                goal,
                                score
                        );

                        if (best == null
                                || routeCandidateOrder().compare(candidate, best) < 0) {
                            best = candidate;
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static List<Cell> aStar(
            List<DungeonLayoutNode> rooms,
            Set<Cell> reserved,
            RoutingBounds bounds,
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

        RouteState startState = new RouteState(start, null);
        Map<RouteState, Integer> bestCost = new HashMap<>();
        Map<RouteState, RouteState> previous = new HashMap<>();
        PriorityQueue<SearchNode> open = new PriorityQueue<>(searchNodeOrder());
        long sequence = 0L;

        bestCost.put(startState, 0);
        open.add(new SearchNode(
                startState,
                0,
                heuristic(start, goal),
                sequence++
        ));

        int explored = 0;

        while (!open.isEmpty() && explored++ < MAX_EXPLORED_STATES) {
            SearchNode currentNode = open.remove();
            RouteState current = currentNode.state();
            int knownCost = bestCost.getOrDefault(current, Integer.MAX_VALUE);

            if (currentNode.cost() != knownCost) {
                continue;
            }

            if (current.cell().equals(goal)) {
                return reconstruct(previous, current);
            }

            for (Step step : orderedSteps(current.cell(), goal)) {
                Cell nextCell = step.cell();

                if (!bounds.containsSearch(nextCell)
                        || blocked.contains(nextCell)) {
                    continue;
                }

                int stepCost = MOVE_COST
                        + turnCost(current.direction(), step.direction())
                        + roomProximityPenalty(
                                nextCell,
                                rooms,
                                fromRoomId,
                                toRoomId
                        )
                        + bounds.outsideHullDistance(nextCell)
                        * OUTSIDE_LAYOUT_HULL_COST;
                int tentativeCost = currentNode.cost() + stepCost;
                RouteState next = new RouteState(
                        nextCell,
                        step.direction()
                );

                if (tentativeCost >= bestCost.getOrDefault(
                        next,
                        Integer.MAX_VALUE
                )) {
                    continue;
                }

                bestCost.put(next, tentativeCost);
                previous.put(next, current);
                open.add(new SearchNode(
                        next,
                        tentativeCost,
                        tentativeCost + heuristic(nextCell, goal),
                        sequence++
                ));
            }
        }

        return List.of();
    }

    private static List<Cell> reconstruct(
            Map<RouteState, RouteState> previous,
            RouteState current
    ) {
        ArrayList<Cell> path = new ArrayList<>();
        path.add(current.cell());

        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.add(current.cell());
        }

        java.util.Collections.reverse(path);
        return path;
    }

    private static void validateRouteQuality(
            DungeonRoutedCorridor corridor,
            Cell start,
            Cell goal
    ) {
        if (corridor.lengthCells() > MAX_INDIVIDUAL_ROUTE_CELLS) {
            throw new IllegalArgumentException(
                    "Corridor route exceeds maximum length: "
                            + corridor.edgeId()
                            + " lengthCells="
                            + corridor.lengthCells()
            );
        }

        int directDistance = manhattan(start, goal);
        int routedSteps = Math.max(0, corridor.lengthCells() - 1);
        double maximumStretch = corridor.kind() == DungeonGraphEdgeKind.LOOP
                ? 2.25D
                : MAX_ROUTE_STRETCH;
        int stretchAllowance = corridor.kind() == DungeonGraphEdgeKind.LOOP
                ? ROUTE_STRETCH_ALLOWANCE + 2
                : ROUTE_STRETCH_ALLOWANCE;
        int maximumSteps = Math.max(
                4,
                (int) Math.ceil(directDistance * maximumStretch)
                        + stretchAllowance
        );

        if (routedSteps > maximumSteps) {
            throw new IllegalArgumentException(
                    "Corridor has excessive route stretch: edge="
                            + corridor.edgeId()
                            + " directSteps="
                            + directDistance
                            + " routedSteps="
                            + routedSteps
                            + " maximumSteps="
                            + maximumSteps
            );
        }

        int turns = countDungeonTurns(corridor.path());
        int maximumTurns = corridor.kind() == DungeonGraphEdgeKind.LOOP
                ? MAX_ROUTE_TURNS + 2
                : MAX_ROUTE_TURNS;
        if (turns > maximumTurns) {
            throw new IllegalArgumentException(
                    "Corridor has too many turns: edge="
                            + corridor.edgeId()
                            + " turns="
                            + turns
                            + " maximum="
                            + maximumTurns
            );
        }
    }

    private static Set<Cell> blockedCells(
            List<DungeonLayoutNode> rooms,
            String fromRoomId,
            String toRoomId
    ) {
        Set<Cell> blocked = new HashSet<>();

        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box = room.cellBox().expanded(
                    ROOM_CLEARANCE_CELLS
            );

            for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    blocked.add(new Cell(x, z));
                }
            }
        }

        return blocked;
    }

    private static int roomProximityPenalty(
            Cell cell,
            List<DungeonLayoutNode> rooms,
            String fromRoomId,
            String toRoomId
    ) {
        int penalty = 0;

        for (DungeonLayoutNode room : rooms) {
            if (room.roomId().equals(fromRoomId)
                    || room.roomId().equals(toRoomId)) {
                continue;
            }

            int distance = distanceToBox(cell, room.cellBox());
            if (distance == 1) {
                penalty += NEAR_UNRELATED_ROOM_COST;
            } else if (distance == 2) {
                penalty += NEAR_UNRELATED_ROOM_COST / 2;
            }
        }

        return penalty;
    }

    private static int distanceToBox(
            Cell cell,
            DungeonCellBox box
    ) {
        int dx;
        if (cell.x() < box.minX()) {
            dx = box.minX() - cell.x();
        } else if (cell.x() >= box.maxXExclusive()) {
            dx = cell.x() - box.maxXExclusive() + 1;
        } else {
            dx = 0;
        }

        int dz;
        if (cell.z() < box.minZ()) {
            dz = box.minZ() - cell.z();
        } else if (cell.z() >= box.maxZExclusive()) {
            dz = cell.z() - box.maxZExclusive() + 1;
        } else {
            dz = 0;
        }

        return dx + dz;
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

    private static List<Step> orderedSteps(
            Cell current,
            Cell goal
    ) {
        List<Step> steps = new ArrayList<>(List.of(
                new Step(
                        new Cell(current.x(), current.z() - 1),
                        DungeonConnectorSide.NORTH
                ),
                new Step(
                        new Cell(current.x() + 1, current.z()),
                        DungeonConnectorSide.EAST
                ),
                new Step(
                        new Cell(current.x(), current.z() + 1),
                        DungeonConnectorSide.SOUTH
                ),
                new Step(
                        new Cell(current.x() - 1, current.z()),
                        DungeonConnectorSide.WEST
                )
        ));
        steps.sort(Comparator
                .comparingInt((Step step) -> manhattan(step.cell(), goal))
                .thenComparingInt(step -> step.direction().ordinal()));
        return steps;
    }

    private static List<DungeonConnectorSide> orderedSides(
            DungeonLayoutNode source,
            DungeonLayoutNode target,
            DungeonConnectorSide planned
    ) {
        DungeonConnectorSide preferred = preferredSide(source, target);
        ArrayList<DungeonConnectorSide> sides = new ArrayList<>(
                HORIZONTAL_SIDES
        );
        sides.sort(Comparator
                .comparingInt((DungeonConnectorSide side) -> {
                    if (side == planned) {
                        return 0;
                    }
                    if (side == preferred) {
                        return 1;
                    }
                    if (side == planned.opposite()) {
                        return 3;
                    }
                    return 2;
                })
                .thenComparingInt(Enum::ordinal));
        return sides;
    }

    private static DungeonConnectorSide preferredSide(
            DungeonLayoutNode source,
            DungeonLayoutNode target
    ) {
        Cell sourceCenter = roomCenter(source);
        Cell targetCenter = roomCenter(target);
        int dx = targetCenter.x() - sourceCenter.x();
        int dz = targetCenter.z() - sourceCenter.z();

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0
                    ? DungeonConnectorSide.EAST
                    : DungeonConnectorSide.WEST;
        }

        return dz >= 0
                ? DungeonConnectorSide.SOUTH
                : DungeonConnectorSide.NORTH;
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
                        .comparingInt((Cell cell) -> manhattan(
                                cell,
                                targetCenter
                        ))
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
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical routing is not supported: " + side
            );
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

    private static Comparator<DungeonLayoutEdge> edgeOrder(
            Map<String, DungeonLayoutNode> nodes
    ) {
        return Comparator
                .comparingInt((DungeonLayoutEdge edge) -> edgePriority(
                        edge,
                        nodes
                ))
                .thenComparing(
                        Comparator.comparingInt(
                                (DungeonLayoutEdge edge) -> estimatedDistance(
                                        edge,
                                        nodes
                                )
                        ).reversed()
                )
                .thenComparing(DungeonLayoutEdge::id);
    }

    private static int edgePriority(
            DungeonLayoutEdge edge,
            Map<String, DungeonLayoutNode> nodes
    ) {
        DungeonLayoutNode from = nodes.get(edge.fromRoomId());
        DungeonLayoutNode to = nodes.get(edge.toRoomId());

        if (from != null
                && to != null
                && ((from.type() == DungeonRoomType.BOSS
                && to.type() == DungeonRoomType.EXIT)
                || (from.type() == DungeonRoomType.EXIT
                && to.type() == DungeonRoomType.BOSS))) {
            return 0;
        }

        return switch (edge.kind()) {
            case TREE -> 1;
            case LOOP -> 2;
            case SECRET -> 3;
        };
    }

    private static int estimatedDistance(
            DungeonLayoutEdge edge,
            Map<String, DungeonLayoutNode> nodes
    ) {
        DungeonLayoutNode from = nodes.get(edge.fromRoomId());
        DungeonLayoutNode to = nodes.get(edge.toRoomId());

        if (from == null || to == null) {
            return 0;
        }

        return manhattan(roomCenter(from), roomCenter(to));
    }

    private static RoutingBounds searchBounds(
            List<DungeonLayoutNode> rooms
    ) {
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

        return new RoutingBounds(
                minX,
                minZ,
                maxX,
                maxZ,
                minX - SEARCH_PADDING_CELLS,
                minZ - SEARCH_PADDING_CELLS,
                maxX + SEARCH_PADDING_CELLS,
                maxZ + SEARCH_PADDING_CELLS
        );
    }

    private static Comparator<RouteCandidate> routeCandidateOrder() {
        return Comparator
                .comparingInt(RouteCandidate::score)
                .thenComparingInt(candidate -> candidate.start().x())
                .thenComparingInt(candidate -> candidate.start().z())
                .thenComparingInt(candidate -> candidate.goal().x())
                .thenComparingInt(candidate -> candidate.goal().z());
    }

    private static Comparator<SearchNode> searchNodeOrder() {
        return Comparator
                .comparingInt(SearchNode::estimatedTotalCost)
                .thenComparingInt(SearchNode::cost)
                .thenComparingInt(node -> node.state().cell().x())
                .thenComparingInt(node -> node.state().cell().z())
                .thenComparingInt(node -> node.state().direction() == null
                        ? -1
                        : node.state().direction().ordinal())
                .thenComparingLong(SearchNode::sequence);
    }

    private static int heuristic(
            Cell current,
            Cell goal
    ) {
        return manhattan(current, goal) * MOVE_COST;
    }

    private static int turnCost(
            DungeonConnectorSide previous,
            DungeonConnectorSide next
    ) {
        return previous == null || previous == next ? 0 : TURN_COST;
    }

    private static int countCellTurns(List<Cell> path) {
        if (path.size() < 3) {
            return 0;
        }

        int turns = 0;
        int previousDx = Integer.compare(
                path.get(1).x() - path.get(0).x(),
                0
        );
        int previousDz = Integer.compare(
                path.get(1).z() - path.get(0).z(),
                0
        );

        for (int index = 2; index < path.size(); index++) {
            int dx = Integer.compare(
                    path.get(index).x() - path.get(index - 1).x(),
                    0
            );
            int dz = Integer.compare(
                    path.get(index).z() - path.get(index - 1).z(),
                    0
            );

            if (dx != previousDx || dz != previousDz) {
                turns++;
            }
            previousDx = dx;
            previousDz = dz;
        }

        return turns;
    }

    private static int countDungeonTurns(List<DungeonCellPos> path) {
        if (path.size() < 3) {
            return 0;
        }

        int turns = 0;
        int previousDx = Integer.compare(
                path.get(1).x() - path.get(0).x(),
                0
        );
        int previousDz = Integer.compare(
                path.get(1).z() - path.get(0).z(),
                0
        );

        for (int index = 2; index < path.size(); index++) {
            int dx = Integer.compare(
                    path.get(index).x() - path.get(index - 1).x(),
                    0
            );
            int dz = Integer.compare(
                    path.get(index).z() - path.get(index - 1).z(),
                    0
            );

            if (dx != previousDx || dz != previousDz) {
                turns++;
            }
            previousDx = dx;
            previousDz = dz;
        }

        return turns;
    }

    private static int manhattan(
            Cell first,
            Cell second
    ) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.z() - second.z());
    }

    private record Cell(
            int x,
            int z
    ) {
    }

    private record Step(
            Cell cell,
            DungeonConnectorSide direction
    ) {
    }

    private record RouteState(
            Cell cell,
            DungeonConnectorSide direction
    ) {
    }

    private record SearchNode(
            RouteState state,
            int cost,
            int estimatedTotalCost,
            long sequence
    ) {
    }

    private record RouteCandidate(
            DungeonRoutedCorridor corridor,
            Cell start,
            Cell goal,
            int score
    ) {
    }

    private record RoutingBounds(
            int hullMinX,
            int hullMinZ,
            int hullMaxXExclusive,
            int hullMaxZExclusive,
            int searchMinX,
            int searchMinZ,
            int searchMaxXExclusive,
            int searchMaxZExclusive
    ) {
        private boolean containsSearch(Cell cell) {
            return cell.x() >= this.searchMinX
                    && cell.x() < this.searchMaxXExclusive
                    && cell.z() >= this.searchMinZ
                    && cell.z() < this.searchMaxZExclusive;
        }

        private int outsideHullDistance(Cell cell) {
            int dx = 0;
            if (cell.x() < this.hullMinX) {
                dx = this.hullMinX - cell.x();
            } else if (cell.x() >= this.hullMaxXExclusive) {
                dx = cell.x() - this.hullMaxXExclusive + 1;
            }

            int dz = 0;
            if (cell.z() < this.hullMinZ) {
                dz = this.hullMinZ - cell.z();
            } else if (cell.z() >= this.hullMaxZExclusive) {
                dz = cell.z() - this.hullMaxZExclusive + 1;
            }

            return dx + dz;
        }
    }
}
