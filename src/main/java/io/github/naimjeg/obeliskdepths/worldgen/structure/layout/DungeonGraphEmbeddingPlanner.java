package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRooms;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Converts the authoritative boss-rooted topology into a compact orthogonal
 * room layout.
 *
 * <p>This is a world-generation planner only. Runtime dungeon code must consume
 * generated layout/site data and must never create or reposition rooms.</p>
 */
public final class DungeonGraphEmbeddingPlanner {
    private static final int MIN_CORRIDOR_GAP_CELLS = 1;
    private static final int MAX_CORRIDOR_GAP_CELLS = 16;
    private static final int MAX_LATERAL_OFFSET_CELLS = 24;
    private static final int MAX_PLANNED_LOOP_CELLS = 64;
    private static final int LOOP_SEARCH_PADDING_CELLS = 8;
    private static final int MAX_LOOP_SEARCH_STATES = 24_000;

    private static final int CORRIDOR_LENGTH_WEIGHT = 18;
    private static final int BOUNDING_GROWTH_WEIGHT = 3;
    private static final int CONGESTION_WEIGHT = 12;
    private static final int CONNECTOR_REUSE_WEIGHT = 48;
    private static final int LATERAL_OFFSET_WEIGHT = 2;
    private static final int PREFERRED_SIDE_WEIGHT = 64;

    private static final int LOOP_MOVE_COST = 10;
    private static final int LOOP_TURN_COST = 24;
    private static final int LOOP_ROOM_PROXIMITY_COST = 8;
    private static final int LOOP_SIDE_MISMATCH_COST = 20;

    private static final List<DungeonConnectorSide> HORIZONTAL_SIDES = List.of(
            DungeonConnectorSide.NORTH,
            DungeonConnectorSide.EAST,
            DungeonConnectorSide.SOUTH,
            DungeonConnectorSide.WEST
    );

    private static final List<DungeonConnectorSide> ROOT_CHILD_SIDE_ORDER = List.of(
            DungeonConnectorSide.NORTH,
            DungeonConnectorSide.EAST,
            DungeonConnectorSide.WEST,
            DungeonConnectorSide.SOUTH
    );

    private DungeonGraphEmbeddingPlanner() {
    }

    public static DungeonLayoutPlan embed(
            DungeonGraph graph,
            BlockPos layoutOrigin
    ) {
        DungeonGraphValidator.validate(graph);

        Map<String, Draft> drafts = new LinkedHashMap<>();
        Map<String, EdgePlan> edgePlans = new LinkedHashMap<>();
        Set<GridCell> reservedCorridors = new HashSet<>();

        DungeonGraphNode rootNode = graph.requireNode(graph.rootNodeId());
        DungeonRoomFootprint rootFootprint = footprintFor(rootNode.type());
        Draft root = new Draft(
                rootNode.id(),
                rootNode.type(),
                centeredOrigin(rootFootprint),
                rootFootprint,
                0,
                null
        );
        drafts.put(root.id(), root);

        placeTree(graph, root, drafts, edgePlans, reservedCorridors);
        planAvailableNonTreeEdges(
                graph,
                drafts,
                edgePlans,
                reservedCorridors
        );

        if (drafts.size() != graph.nodes().size()) {
            List<String> missing = graph.nodes()
                    .stream()
                    .map(DungeonGraphNode::id)
                    .filter(id -> !drafts.containsKey(id))
                    .toList();
            throw new IllegalArgumentException(
                    "Compact embedding did not place every graph node: " + missing
            );
        }

        List<String> unplannedEdges = graph.edges()
                .stream()
                .map(DungeonGraphEdge::id)
                .filter(id -> !edgePlans.containsKey(id))
                .toList();
        if (!unplannedEdges.isEmpty()) {
            throw new IllegalArgumentException(
                    "Compact embedding did not plan every graph edge: "
                            + unplannedEdges
            );
        }

        Map<String, EnumSet<DungeonConnectorSide>> connectors = new LinkedHashMap<>();
        for (String nodeId : drafts.keySet()) {
            connectors.put(nodeId, EnumSet.noneOf(DungeonConnectorSide.class));
        }

        List<DungeonLayoutEdge> edges = new ArrayList<>();
        for (DungeonGraphEdge graphEdge : graph.edges()) {
            addEmbeddedEdge(
                    edges,
                    connectors,
                    edgePlans,
                    graphEdge
            );
        }

        List<DungeonLayoutNode> nodes = drafts.values()
                .stream()
                .map(draft -> new DungeonLayoutNode(
                        draft.id(),
                        draft.type(),
                        draft.cellOrigin(),
                        draft.footprint(),
                        connectors.get(draft.id())
                ))
                .toList();

        DungeonLayoutPlan plan = new DungeonLayoutPlan(
                DungeonLayoutConstants.CELL_SIZE,
                nodes,
                edges
        );
        DungeonSpatialLayoutValidator.validate(plan);
        return plan;
    }

    private static void placeTree(
            DungeonGraph graph,
            Draft root,
            Map<String, Draft> drafts,
            Map<String, EdgePlan> edgePlans,
            Set<GridCell> reservedCorridors
    ) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(root.id());

        while (!queue.isEmpty()) {
            String parentId = queue.remove();
            Draft parent = requireDraft(drafts, parentId);
            List<DungeonGraphEdge> childEdges = graph.treeEdges()
                    .stream()
                    .filter(edge -> edge.sourceNodeId().equals(parentId))
                    .toList();

            for (int childIndex = 0; childIndex < childEdges.size(); childIndex++) {
                DungeonGraphEdge edge = childEdges.get(childIndex);
                DungeonGraphNode childNode = graph.requireNode(edge.targetNodeId());

                if (drafts.containsKey(childNode.id())) {
                    throw new IllegalArgumentException(
                            "Tree node was placed more than once: " + childNode.id()
                    );
                }

                DungeonRoomFootprint footprint = footprintFor(childNode.type());
                DungeonConnectorSide preferredSide = preferredSide(
                        graph,
                        parent,
                        childIndex
                );
                PlacementCandidate candidate = chooseBestPlacement(
                        childNode.id(),
                        parent,
                        footprint,
                        preferredSide,
                        drafts,
                        reservedCorridors
                );
                Draft child = new Draft(
                        childNode.id(),
                        childNode.type(),
                        candidate.origin(),
                        footprint,
                        parent.treeDepth() + 1,
                        candidate.parentSide().opposite()
                );

                acceptTreePlacement(
                        parent,
                        child,
                        edge,
                        candidate,
                        drafts,
                        edgePlans,
                        reservedCorridors
                );
                queue.add(child.id());

                planAvailableNonTreeEdges(
                        graph,
                        drafts,
                        edgePlans,
                        reservedCorridors
                );
            }
        }
    }

    private static DungeonConnectorSide preferredSide(
            DungeonGraph graph,
            Draft parent,
            int childIndex
    ) {
        if (parent.id().equals(graph.rootNodeId())) {
            return ROOT_CHILD_SIDE_ORDER.get(
                    childIndex % ROOT_CHILD_SIDE_ORDER.size()
            );
        }

        // Preserve the first two sector bands so the guaranteed inner loop has
        // spatially nearby endpoints. Deeper bands are free to fold inward.
        if (parent.treeDepth() == 1 && parent.incomingSide() != null) {
            return parent.incomingSide().opposite();
        }

        return null;
    }

    private static void acceptTreePlacement(
            Draft parent,
            Draft child,
            DungeonGraphEdge edge,
            PlacementCandidate candidate,
            Map<String, Draft> drafts,
            Map<String, EdgePlan> edgePlans,
            Set<GridCell> reservedCorridors
    ) {
        parent.reserve(candidate.parentSide());
        child.reserve(candidate.parentSide().opposite());
        drafts.put(child.id(), child);
        reservedCorridors.addAll(candidate.corridorCells());
        edgePlans.put(
                edge.id(),
                new EdgePlan(
                        candidate.parentSide(),
                        candidate.parentSide().opposite(),
                        candidate.corridorCells()
                )
        );
    }

    private static void planAvailableNonTreeEdges(
            DungeonGraph graph,
            Map<String, Draft> drafts,
            Map<String, EdgePlan> edgePlans,
            Set<GridCell> reservedCorridors
    ) {
        for (DungeonGraphEdge edge : graph.edges()) {
            if (edge.kind() == DungeonGraphEdgeKind.TREE
                    || edgePlans.containsKey(edge.id())
                    || !drafts.containsKey(edge.sourceNodeId())
                    || !drafts.containsKey(edge.targetNodeId())) {
                continue;
            }

            Draft source = requireDraft(drafts, edge.sourceNodeId());
            Draft target = requireDraft(drafts, edge.targetNodeId());
            EdgePlan plan = planNonTreeEdge(
                    edge,
                    source,
                    target,
                    drafts,
                    reservedCorridors
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Unable to reserve a compact planned route for "
                            + edge.kind()
                            + " edge "
                            + edge.id()
            ));

            source.reserve(plan.fromSide());
            target.reserve(plan.toSide());
            edgePlans.put(edge.id(), plan);
            reservedCorridors.addAll(plan.path());
        }
    }

    private static Optional<EdgePlan> planNonTreeEdge(
            DungeonGraphEdge edge,
            Draft source,
            Draft target,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        SearchBounds bounds = searchBounds(drafts.values());
        DungeonConnectorSide preferredSource = connectorSide(source, target);
        DungeonConnectorSide preferredTarget = preferredSource.opposite();
        PlannedRoute best = null;

        for (DungeonConnectorSide sourceSide : HORIZONTAL_SIDES) {
            for (DungeonConnectorSide targetSide : HORIZONTAL_SIDES) {
                for (GridCell start : exteriorPorts(source, sourceSide)) {
                    for (GridCell goal : exteriorPorts(target, targetSide)) {
                        List<GridCell> path = aStar(
                                drafts,
                                reservedCorridors,
                                bounds,
                                source.id(),
                                target.id(),
                                start,
                                goal
                        );

                        if (path.isEmpty()
                                || path.size() > MAX_PLANNED_LOOP_CELLS) {
                            continue;
                        }

                        int score = path.size() * LOOP_MOVE_COST
                                + countTurns(path) * LOOP_TURN_COST;
                        if (sourceSide != preferredSource) {
                            score += LOOP_SIDE_MISMATCH_COST;
                        }
                        if (targetSide != preferredTarget) {
                            score += LOOP_SIDE_MISMATCH_COST;
                        }

                        PlannedRoute candidate = new PlannedRoute(
                                sourceSide,
                                targetSide,
                                path,
                                score
                        );
                        if (best == null
                                || plannedRouteOrder().compare(candidate, best) < 0) {
                            best = candidate;
                        }
                    }
                }
            }
        }

        if (best == null) {
            return Optional.empty();
        }

        return Optional.of(new EdgePlan(
                best.fromSide(),
                best.toSide(),
                best.path()
        ));
    }

    private static PlacementCandidate chooseBestPlacement(
            String nodeId,
            Draft parent,
            DungeonRoomFootprint footprint,
            DungeonConnectorSide preferredSide,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        List<PlacementCandidate> roomLegalCandidates = new ArrayList<>();
        List<PlacementCandidate> straightCandidates = new ArrayList<>();

        for (DungeonConnectorSide side : HORIZONTAL_SIDES) {
            for (int gap = MIN_CORRIDOR_GAP_CELLS;
                 gap <= MAX_CORRIDOR_GAP_CELLS;
                 gap++) {
                for (int lateralOffset : lateralOffsets()) {
                    PlacementCandidate candidate = candidateFor(
                            parent,
                            footprint,
                            side,
                            gap,
                            lateralOffset
                    );
                    DungeonCellBox candidateBox = footprint.toCellBox(
                            candidate.origin()
                    );

                    if (!isRoomPlacementLegal(
                            candidateBox,
                            drafts,
                            reservedCorridors
                    )) {
                        continue;
                    }

                    roomLegalCandidates.add(candidate.withScore(
                            basePlacementScore(
                                    candidate,
                                    candidateBox,
                                    parent,
                                    preferredSide,
                                    drafts
                            )
                    ));

                    if (isCorridorLegal(
                            candidate.corridorCells(),
                            parent,
                            drafts,
                            reservedCorridors
                    )) {
                        straightCandidates.add(candidate.withScore(
                                placementScore(
                                        candidate,
                                        candidateBox,
                                        parent,
                                        preferredSide,
                                        drafts
                                )
                        ));
                    }
                }
            }
        }

        Optional<PlacementCandidate> straight = selectBestCandidate(
                straightCandidates,
                preferredSide
        );
        if (straight.isPresent()) {
            return straight.get();
        }

        List<PlacementCandidate> fallbackCandidates = roomLegalCandidates
                .stream()
                .sorted(candidateOrder())
                .toList();
        List<PlacementCandidate> routedCandidates = new ArrayList<>();

        for (PlacementCandidate candidate : fallbackCandidates) {
            Optional<List<GridCell>> path = planFallbackTreePath(
                    nodeId,
                    parent,
                    footprint,
                    candidate,
                    drafts,
                    reservedCorridors
            );

            if (path.isEmpty()) {
                continue;
            }

            PlacementCandidate routed = candidate.withCorridorCells(
                    path.get()
            );
            DungeonCellBox candidateBox = footprint.toCellBox(
                    candidate.origin()
            );
            routedCandidates.add(routed.withScore(placementScore(
                    routed,
                    candidateBox,
                    parent,
                    preferredSide,
                    drafts
            )));
        }

        return selectBestCandidate(routedCandidates, preferredSide)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to place dungeon room "
                                + nodeId
                                + " near parent "
                                + parent.id()
                                + " parentBox="
                                + parent.cellBox()
                                + " childFootprint="
                                + footprint
                                + " roomLegalCandidates="
                                + roomLegalCandidates.size()
                                + " straightCandidates="
                                + straightCandidates.size()
                                + " routedCandidates="
                                + routedCandidates.size()
                ));
    }

    private static Optional<PlacementCandidate> selectBestCandidate(
            List<PlacementCandidate> candidates,
            DungeonConnectorSide preferredSide
    ) {
        List<PlacementCandidate> eligible = candidates;

        if (preferredSide != null) {
            List<PlacementCandidate> preferred = candidates.stream()
                    .filter(candidate -> candidate.parentSide() == preferredSide)
                    .toList();

            if (!preferred.isEmpty()) {
                eligible = preferred;
            }
        }

        return eligible.stream().min(candidateOrder());
    }

    private static Comparator<PlacementCandidate> candidateOrder() {
        return Comparator
                .comparingInt(PlacementCandidate::score)
                .thenComparingInt(candidate -> candidate.parentSide().ordinal())
                .thenComparingInt(PlacementCandidate::gap)
                .thenComparingInt(candidate -> Math.abs(candidate.lateralOffset()))
                .thenComparingInt(PlacementCandidate::lateralOffset)
                .thenComparingInt(candidate -> candidate.origin().x())
                .thenComparingInt(candidate -> candidate.origin().z());
    }

    private static boolean isRoomPlacementLegal(
            DungeonCellBox candidateBox,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        if (overlapsAny(candidateBox, drafts)) {
            return false;
        }

        for (GridCell reserved : reservedCorridors) {
            if (insideBox(reserved, candidateBox)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isCorridorLegal(
            List<GridCell> corridorCells,
            Draft parent,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        if (corridorCells.isEmpty()) {
            return false;
        }

        for (GridCell cell : corridorCells) {
            if (reservedCorridors.contains(cell)
                    || insideAnyRoom(cell, drafts, parent.id())) {
                return false;
            }
        }

        return true;
    }

    private static Optional<List<GridCell>> planFallbackTreePath(
            String nodeId,
            Draft parent,
            DungeonRoomFootprint footprint,
            PlacementCandidate candidate,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        Draft child = new Draft(
                nodeId,
                DungeonRoomType.COMBAT,
                candidate.origin(),
                footprint,
                parent.treeDepth() + 1,
                candidate.parentSide().opposite()
        );
        Map<String, Draft> temporaryDrafts = new LinkedHashMap<>(drafts);
        temporaryDrafts.put(nodeId, child);
        SearchBounds bounds = searchBounds(temporaryDrafts.values());
        List<GridCell> best = List.of();
        int bestScore = Integer.MAX_VALUE;

        for (GridCell start : exteriorPorts(parent, candidate.parentSide())) {
            for (GridCell goal : exteriorPorts(
                    child,
                    candidate.parentSide().opposite()
            )) {
                List<GridCell> path = aStar(
                        temporaryDrafts,
                        reservedCorridors,
                        bounds,
                        parent.id(),
                        child.id(),
                        start,
                        goal
                );

                if (!acceptableTreePath(path, start, goal)) {
                    continue;
                }

                int score = path.size() * LOOP_MOVE_COST
                        + countTurns(path) * LOOP_TURN_COST;
                if (score < bestScore) {
                    best = path;
                    bestScore = score;
                }
            }
        }

        return best.isEmpty() ? Optional.empty() : Optional.of(best);
    }

    private static boolean acceptableTreePath(
            List<GridCell> path,
            GridCell start,
            GridCell goal
    ) {
        if (path.isEmpty()
                || path.size() > MAX_PLANNED_LOOP_CELLS) {
            return false;
        }

        int directSteps = manhattan(start, goal);
        int routedSteps = path.size() - 1;
        int maximumSteps = Math.max(
                8,
                (int) Math.ceil(directSteps * 3.0D) + 8
        );
        return routedSteps <= maximumSteps;
    }

    private static int basePlacementScore(
            PlacementCandidate candidate,
            DungeonCellBox candidateBox,
            Draft parent,
            DungeonConnectorSide preferredSide,
            Map<String, Draft> drafts
    ) {
        int preferredSidePenalty = preferredSide == null
                || preferredSide == candidate.parentSide()
                ? 0
                : 1;

        return candidate.gap() * CORRIDOR_LENGTH_WEIGHT
                + boundingAreaGrowth(candidateBox, drafts)
                * BOUNDING_GROWTH_WEIGHT
                + nearbyRoomCount(candidateBox, parent.id(), drafts)
                * CONGESTION_WEIGHT
                + parent.sideUseCount(candidate.parentSide())
                * CONNECTOR_REUSE_WEIGHT
                + Math.abs(candidate.lateralOffset())
                * LATERAL_OFFSET_WEIGHT
                + preferredSidePenalty * PREFERRED_SIDE_WEIGHT;
    }

    private static int placementScore(
            PlacementCandidate candidate,
            DungeonCellBox candidateBox,
            Draft parent,
            DungeonConnectorSide preferredSide,
            Map<String, Draft> drafts
    ) {
        int preferredSidePenalty = preferredSide == null
                || preferredSide == candidate.parentSide()
                ? 0
                : 1;

        return candidate.corridorCells().size() * CORRIDOR_LENGTH_WEIGHT
                + boundingAreaGrowth(candidateBox, drafts)
                * BOUNDING_GROWTH_WEIGHT
                + nearbyRoomCount(candidateBox, parent.id(), drafts)
                * CONGESTION_WEIGHT
                + parent.sideUseCount(candidate.parentSide())
                * CONNECTOR_REUSE_WEIGHT
                + Math.abs(candidate.lateralOffset())
                * LATERAL_OFFSET_WEIGHT
                + preferredSidePenalty * PREFERRED_SIDE_WEIGHT;
    }

    private static PlacementCandidate candidateFor(
            Draft parent,
            DungeonRoomFootprint childFootprint,
            DungeonConnectorSide side,
            int gap,
            int lateralOffset
    ) {
        DungeonCellBox parentBox = parent.cellBox();
        int childWidth = childFootprint.widthCells();
        int childDepth = childFootprint.depthCells();
        int centeredX = parentBox.minX()
                + (parentBox.sizeX() - childWidth) / 2;
        int centeredZ = parentBox.minZ()
                + (parentBox.sizeZ() - childDepth) / 2;

        DungeonCellPos origin = switch (side) {
            case NORTH -> new DungeonCellPos(
                    centeredX + lateralOffset,
                    parent.cellOrigin().y(),
                    parentBox.minZ() - gap - childDepth
            );
            case SOUTH -> new DungeonCellPos(
                    centeredX + lateralOffset,
                    parent.cellOrigin().y(),
                    parentBox.maxZExclusive() + gap
            );
            case WEST -> new DungeonCellPos(
                    parentBox.minX() - gap - childWidth,
                    parent.cellOrigin().y(),
                    centeredZ + lateralOffset
            );
            case EAST -> new DungeonCellPos(
                    parentBox.maxXExclusive() + gap,
                    parent.cellOrigin().y(),
                    centeredZ + lateralOffset
            );
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical dungeon embedding is not supported: " + side
            );
        };
        DungeonCellBox childBox = childFootprint.toCellBox(origin);

        return new PlacementCandidate(
                origin,
                side,
                gap,
                lateralOffset,
                straightCorridorCells(parentBox, childBox, side),
                Integer.MAX_VALUE
        );
    }

    private static List<GridCell> straightCorridorCells(
            DungeonCellBox parent,
            DungeonCellBox child,
            DungeonConnectorSide side
    ) {
        List<GridCell> result = new ArrayList<>();

        switch (side) {
            case NORTH, SOUTH -> {
                int overlapMin = Math.max(parent.minX(), child.minX());
                int overlapMax = Math.min(
                        parent.maxXExclusive(),
                        child.maxXExclusive()
                );
                if (overlapMin >= overlapMax) {
                    return List.of();
                }

                int x = overlapMin + (overlapMax - overlapMin - 1) / 2;
                int firstZ = side == DungeonConnectorSide.NORTH
                        ? parent.minZ() - 1
                        : parent.maxZExclusive();
                int lastZ = side == DungeonConnectorSide.NORTH
                        ? child.maxZExclusive()
                        : child.minZ() - 1;
                int step = Integer.compare(lastZ, firstZ);
                for (int z = firstZ; ; z += step) {
                    result.add(new GridCell(x, z));
                    if (z == lastZ) {
                        break;
                    }
                }
            }
            case WEST, EAST -> {
                int overlapMin = Math.max(parent.minZ(), child.minZ());
                int overlapMax = Math.min(
                        parent.maxZExclusive(),
                        child.maxZExclusive()
                );
                if (overlapMin >= overlapMax) {
                    return List.of();
                }

                int z = overlapMin + (overlapMax - overlapMin - 1) / 2;
                int firstX = side == DungeonConnectorSide.WEST
                        ? parent.minX() - 1
                        : parent.maxXExclusive();
                int lastX = side == DungeonConnectorSide.WEST
                        ? child.maxXExclusive()
                        : child.minX() - 1;
                int step = Integer.compare(lastX, firstX);
                for (int x = firstX; ; x += step) {
                    result.add(new GridCell(x, z));
                    if (x == lastX) {
                        break;
                    }
                }
            }
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical dungeon embedding is not supported: " + side
            );
        }

        return List.copyOf(result);
    }

    private static List<GridCell> aStar(
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors,
            SearchBounds bounds,
            String sourceRoomId,
            String targetRoomId,
            GridCell start,
            GridCell goal
    ) {
        if (reservedCorridors.contains(start)
                || reservedCorridors.contains(goal)) {
            return List.of();
        }

        Set<GridCell> blocked = blockedRoomCells(
                drafts,
                sourceRoomId,
                targetRoomId
        );
        if (blocked.contains(start) || blocked.contains(goal)) {
            return List.of();
        }
        blocked.addAll(reservedCorridors);

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
        while (!open.isEmpty() && explored++ < MAX_LOOP_SEARCH_STATES) {
            SearchNode currentNode = open.remove();
            RouteState current = currentNode.state();
            if (currentNode.cost()
                    != bestCost.getOrDefault(current, Integer.MAX_VALUE)) {
                continue;
            }

            if (current.cell().equals(goal)) {
                return reconstruct(previous, current);
            }

            for (Step step : orderedSteps(current.cell(), goal)) {
                if (!bounds.contains(step.cell())
                        || blocked.contains(step.cell())) {
                    continue;
                }

                int cost = currentNode.cost()
                        + LOOP_MOVE_COST
                        + turnCost(current.direction(), step.direction())
                        + roomProximityPenalty(
                                step.cell(),
                                drafts,
                                sourceRoomId,
                                targetRoomId
                        );
                RouteState next = new RouteState(
                        step.cell(),
                        step.direction()
                );

                if (cost >= bestCost.getOrDefault(next, Integer.MAX_VALUE)) {
                    continue;
                }

                bestCost.put(next, cost);
                previous.put(next, current);
                open.add(new SearchNode(
                        next,
                        cost,
                        cost + heuristic(step.cell(), goal),
                        sequence++
                ));
            }
        }

        return List.of();
    }

    private static Set<GridCell> blockedRoomCells(
            Map<String, Draft> drafts,
            String sourceRoomId,
            String targetRoomId
    ) {
        Set<GridCell> blocked = new HashSet<>();

        for (Draft draft : drafts.values()) {
            DungeonCellBox box = draft.cellBox();
            for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    blocked.add(new GridCell(x, z));
                }
            }
        }

        return blocked;
    }

    private static int roomProximityPenalty(
            GridCell cell,
            Map<String, Draft> drafts,
            String sourceRoomId,
            String targetRoomId
    ) {
        int penalty = 0;

        for (Draft draft : drafts.values()) {
            if (draft.id().equals(sourceRoomId)
                    || draft.id().equals(targetRoomId)) {
                continue;
            }

            int distance = distanceToBox(cell, draft.cellBox());
            if (distance == 1) {
                penalty += LOOP_ROOM_PROXIMITY_COST;
            } else if (distance == 2) {
                penalty += LOOP_ROOM_PROXIMITY_COST / 2;
            }
        }

        return penalty;
    }

    private static int distanceToBox(
            GridCell cell,
            DungeonCellBox box
    ) {
        int dx = cell.x() < box.minX()
                ? box.minX() - cell.x()
                : cell.x() >= box.maxXExclusive()
                ? cell.x() - box.maxXExclusive() + 1
                : 0;
        int dz = cell.z() < box.minZ()
                ? box.minZ() - cell.z()
                : cell.z() >= box.maxZExclusive()
                ? cell.z() - box.maxZExclusive() + 1
                : 0;
        return dx + dz;
    }

    private static List<GridCell> reconstruct(
            Map<RouteState, RouteState> previous,
            RouteState current
    ) {
        List<GridCell> path = new ArrayList<>();
        path.add(current.cell());

        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.add(current.cell());
        }

        java.util.Collections.reverse(path);
        return List.copyOf(path);
    }

    private static List<Step> orderedSteps(
            GridCell current,
            GridCell goal
    ) {
        List<Step> steps = new ArrayList<>(List.of(
                new Step(
                        new GridCell(current.x(), current.z() - 1),
                        DungeonConnectorSide.NORTH
                ),
                new Step(
                        new GridCell(current.x() + 1, current.z()),
                        DungeonConnectorSide.EAST
                ),
                new Step(
                        new GridCell(current.x(), current.z() + 1),
                        DungeonConnectorSide.SOUTH
                ),
                new Step(
                        new GridCell(current.x() - 1, current.z()),
                        DungeonConnectorSide.WEST
                )
        ));
        steps.sort(Comparator
                .comparingInt((Step step) -> manhattan(step.cell(), goal))
                .thenComparingInt(step -> step.direction().ordinal()));
        return steps;
    }

    private static List<GridCell> exteriorPorts(
            Draft room,
            DungeonConnectorSide side
    ) {
        DungeonCellBox box = room.cellBox();
        List<GridCell> ports = new ArrayList<>();

        switch (side) {
            case NORTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    ports.add(new GridCell(x, box.minZ() - 1));
                }
            }
            case SOUTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    ports.add(new GridCell(x, box.maxZExclusive()));
                }
            }
            case WEST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    ports.add(new GridCell(box.minX() - 1, z));
                }
            }
            case EAST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    ports.add(new GridCell(box.maxXExclusive(), z));
                }
            }
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical dungeon embedding is not supported: " + side
            );
        }

        return ports;
    }

    private static SearchBounds searchBounds(
            Iterable<Draft> drafts
    ) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Draft draft : drafts) {
            DungeonCellBox box = draft.cellBox();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxXExclusive());
            maxZ = Math.max(maxZ, box.maxZExclusive());
        }

        return new SearchBounds(
                minX - LOOP_SEARCH_PADDING_CELLS,
                minZ - LOOP_SEARCH_PADDING_CELLS,
                maxX + LOOP_SEARCH_PADDING_CELLS,
                maxZ + LOOP_SEARCH_PADDING_CELLS
        );
    }

    private static List<Integer> lateralOffsets() {
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);

        for (int magnitude = 1;
             magnitude <= MAX_LATERAL_OFFSET_CELLS;
             magnitude++) {
            offsets.add(-magnitude);
            offsets.add(magnitude);
        }

        return offsets;
    }

    private static boolean overlapsAny(
            DungeonCellBox candidate,
            Map<String, Draft> drafts
    ) {
        return drafts.values()
                .stream()
                .map(Draft::cellBox)
                .anyMatch(candidate::intersects);
    }

    private static boolean insideAnyRoom(
            GridCell cell,
            Map<String, Draft> drafts,
            String ignoredRoomId
    ) {
        for (Draft draft : drafts.values()) {
            if (!draft.id().equals(ignoredRoomId)
                    && insideBox(cell, draft.cellBox())) {
                return true;
            }
        }

        return false;
    }

    private static boolean insideBox(
            GridCell cell,
            DungeonCellBox box
    ) {
        return cell.x() >= box.minX()
                && cell.x() < box.maxXExclusive()
                && cell.z() >= box.minZ()
                && cell.z() < box.maxZExclusive();
    }

    private static int boundingAreaGrowth(
            DungeonCellBox candidate,
            Map<String, Draft> drafts
    ) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Draft draft : drafts.values()) {
            DungeonCellBox box = draft.cellBox();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxXExclusive());
            maxZ = Math.max(maxZ, box.maxZExclusive());
        }

        int oldArea = (maxX - minX) * (maxZ - minZ);
        int newMinX = Math.min(minX, candidate.minX());
        int newMinZ = Math.min(minZ, candidate.minZ());
        int newMaxX = Math.max(maxX, candidate.maxXExclusive());
        int newMaxZ = Math.max(maxZ, candidate.maxZExclusive());
        int newArea = (newMaxX - newMinX) * (newMaxZ - newMinZ);
        return newArea - oldArea;
    }

    private static int nearbyRoomCount(
            DungeonCellBox candidate,
            String parentId,
            Map<String, Draft> drafts
    ) {
        DungeonCellBox nearby = candidate.expanded(2);
        int count = 0;

        for (Draft draft : drafts.values()) {
            if (!draft.id().equals(parentId)
                    && nearby.intersects(draft.cellBox())) {
                count++;
            }
        }

        return count;
    }

    private static DungeonCellPos centeredOrigin(
            DungeonRoomFootprint footprint
    ) {
        return new DungeonCellPos(
                -footprint.widthCells() / 2,
                0,
                -footprint.depthCells() / 2
        );
    }

    private static void addEmbeddedEdge(
            List<DungeonLayoutEdge> edges,
            Map<String, EnumSet<DungeonConnectorSide>> connectors,
            Map<String, EdgePlan> edgePlans,
            DungeonGraphEdge graphEdge
    ) {
        EdgePlan plan = edgePlans.get(graphEdge.id());
        if (plan == null) {
            throw new IllegalArgumentException(
                    "Missing embedded edge plan: " + graphEdge.id()
            );
        }

        connectors.get(graphEdge.sourceNodeId()).add(plan.fromSide());
        connectors.get(graphEdge.targetNodeId()).add(plan.toSide());

        edges.add(new DungeonLayoutEdge(
                graphEdge.id().replaceFirst(
                        "^(tree|loop|secret)_",
                        "corridor_$1_"
                ),
                graphEdge.sourceNodeId(),
                graphEdge.targetNodeId(),
                plan.fromSide(),
                plan.toSide(),
                1,
                graphEdge.kind(),
                plan.path().stream()
                        .map(cell -> new DungeonCellPos(
                                cell.x(),
                                0,
                                cell.z()
                        ))
                        .toList()
        ));
    }

    private static DungeonConnectorSide connectorSide(
            Draft source,
            Draft target
    ) {
        int sourceCenterX = source.cellOrigin().x()
                + source.footprint().widthCells() / 2;
        int sourceCenterZ = source.cellOrigin().z()
                + source.footprint().depthCells() / 2;
        int targetCenterX = target.cellOrigin().x()
                + target.footprint().widthCells() / 2;
        int targetCenterZ = target.cellOrigin().z()
                + target.footprint().depthCells() / 2;
        int dx = targetCenterX - sourceCenterX;
        int dz = targetCenterZ - sourceCenterZ;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0
                    ? DungeonConnectorSide.EAST
                    : DungeonConnectorSide.WEST;
        }

        return dz >= 0
                ? DungeonConnectorSide.SOUTH
                : DungeonConnectorSide.NORTH;
    }

    private static Draft requireDraft(
            Map<String, Draft> drafts,
            String nodeId
    ) {
        Draft draft = drafts.get(nodeId);
        if (draft == null) {
            throw new IllegalArgumentException(
                    "Cannot embed graph edge before both endpoints are placed: "
                            + nodeId
            );
        }
        return draft;
    }

    private static DungeonRoomFootprint footprintFor(DungeonRoomType type) {
        Identifier definitionId = switch (type) {
            case START -> BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION;
            case COMBAT -> BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION;
            case TREASURE -> BuiltinDungeonRooms.GREAT_SWAMP_TREASURE_OBELISK_SANCTUM;
            case BOSS -> BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR;
        };
        DungeonRoomDefinition definition = BuiltinDungeonRoomDefinitions.all()
                .get(definitionId);

        if (definition == null) {
            throw new IllegalStateException("Missing built-in room definition: " + definitionId);
        }

        DungeonRoomFootprint footprint = definition.footprint();
        int width = planningWidth(type, footprint);
        int depth = planningDepth(type, footprint);

        return DungeonRoomFootprint.rectangular(width, 1, depth);
    }

    private static int planningWidth(
            DungeonRoomType type,
            DungeonRoomFootprint footprint
    ) {
        return switch (type) {
            case START -> Math.max(footprint.widthCells(), 2);
            case COMBAT -> Math.max(footprint.widthCells(), 3);
            case TREASURE -> footprint.widthCells();
            case BOSS -> footprint.widthCells();
        };
    }

    private static int planningDepth(
            DungeonRoomType type,
            DungeonRoomFootprint footprint
    ) {
        return switch (type) {
            case START -> Math.max(footprint.depthCells(), 2);
            case COMBAT -> Math.max(footprint.depthCells(), 3);
            case TREASURE -> footprint.depthCells();
            case BOSS -> footprint.depthCells();
        };
    }

    private static Comparator<PlannedRoute> plannedRouteOrder() {
        return Comparator
                .comparingInt(PlannedRoute::score)
                .thenComparingInt(route -> route.fromSide().ordinal())
                .thenComparingInt(route -> route.toSide().ordinal())
                .thenComparingInt(route -> route.path().get(0).x())
                .thenComparingInt(route -> route.path().get(0).z());
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
            GridCell current,
            GridCell goal
    ) {
        return manhattan(current, goal) * LOOP_MOVE_COST;
    }

    private static int turnCost(
            DungeonConnectorSide previous,
            DungeonConnectorSide next
    ) {
        return previous == null || previous == next ? 0 : LOOP_TURN_COST;
    }

    private static int countTurns(List<GridCell> path) {
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
            GridCell first,
            GridCell second
    ) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.z() - second.z());
    }

    private static final class Draft {
        private final String id;
        private final DungeonRoomType type;
        private final DungeonCellPos cellOrigin;
        private final DungeonRoomFootprint footprint;
        private final int treeDepth;
        private final DungeonConnectorSide incomingSide;
        private final EnumMap<DungeonConnectorSide, Integer> sideUseCounts =
                new EnumMap<>(DungeonConnectorSide.class);

        private Draft(
                String id,
                DungeonRoomType type,
                DungeonCellPos cellOrigin,
                DungeonRoomFootprint footprint,
                int treeDepth,
                DungeonConnectorSide incomingSide
        ) {
            this.id = id;
            this.type = type;
            this.cellOrigin = cellOrigin;
            this.footprint = footprint;
            this.treeDepth = treeDepth;
            this.incomingSide = incomingSide;
        }

        private String id() {
            return this.id;
        }

        private DungeonRoomType type() {
            return this.type;
        }

        private DungeonCellPos cellOrigin() {
            return this.cellOrigin;
        }

        private DungeonRoomFootprint footprint() {
            return this.footprint;
        }

        private DungeonCellBox cellBox() {
            return this.footprint.toCellBox(this.cellOrigin);
        }

        private int treeDepth() {
            return this.treeDepth;
        }

        private DungeonConnectorSide incomingSide() {
            return this.incomingSide;
        }

        private void reserve(DungeonConnectorSide side) {
            this.sideUseCounts.merge(side, 1, Integer::sum);
        }

        private int sideUseCount(DungeonConnectorSide side) {
            return this.sideUseCounts.getOrDefault(side, 0);
        }
    }

    private record GridCell(
            int x,
            int z
    ) {
    }

    private record EdgePlan(
            DungeonConnectorSide fromSide,
            DungeonConnectorSide toSide,
            List<GridCell> path
    ) {
        private EdgePlan {
            path = List.copyOf(path);
        }
    }

    private record PlacementCandidate(
            DungeonCellPos origin,
            DungeonConnectorSide parentSide,
            int gap,
            int lateralOffset,
            List<GridCell> corridorCells,
            int score
    ) {
        private PlacementCandidate {
            corridorCells = List.copyOf(corridorCells);
        }

        private PlacementCandidate withScore(int newScore) {
            return new PlacementCandidate(
                    this.origin,
                    this.parentSide,
                    this.gap,
                    this.lateralOffset,
                    this.corridorCells,
                    newScore
            );
        }

        private PlacementCandidate withCorridorCells(
                List<GridCell> newCorridorCells
        ) {
            return new PlacementCandidate(
                    this.origin,
                    this.parentSide,
                    this.gap,
                    this.lateralOffset,
                    newCorridorCells,
                    this.score
            );
        }
    }

    private record PlannedRoute(
            DungeonConnectorSide fromSide,
            DungeonConnectorSide toSide,
            List<GridCell> path,
            int score
    ) {
        private PlannedRoute {
            path = List.copyOf(path);
        }
    }

    private record Step(
            GridCell cell,
            DungeonConnectorSide direction
    ) {
    }

    private record RouteState(
            GridCell cell,
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

    private record SearchBounds(
            int minX,
            int minZ,
            int maxXExclusive,
            int maxZExclusive
    ) {
        private boolean contains(GridCell cell) {
            return cell.x() >= this.minX
                    && cell.x() < this.maxXExclusive
                    && cell.z() >= this.minZ
                    && cell.z() < this.maxZExclusive;
        }
    }
}
