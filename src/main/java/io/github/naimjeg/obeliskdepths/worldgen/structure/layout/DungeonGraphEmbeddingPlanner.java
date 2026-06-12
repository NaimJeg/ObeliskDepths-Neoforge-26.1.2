package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonNodeAnalysis;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Temporary radial debug embedding for boss-rooted dungeon graphs. It assigns
 * cell positions and connector sides only; the graph remains authoritative.
 */
public final class DungeonGraphEmbeddingPlanner {
    private static final int RADIAL_SPACING_CELLS = 5;
    private static final int BASE_RADIUS_CELLS = 4;
    private static final int EXIT_OFFSET_CELLS = -6;
    private static final int COLLISION_STEP_CELLS = 3;
    private static final int MAX_COLLISION_ATTEMPTS = 48;

    private DungeonGraphEmbeddingPlanner() {
    }

    public static DungeonLayoutPlan embed(
            DungeonGraph graph,
            BlockPos layoutOrigin
    ) {
        DungeonGraphValidator.validate(graph);
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);

        Map<String, Draft> drafts = new LinkedHashMap<>();
        Map<String, EnumSet<DungeonConnectorSide>> connectors = new LinkedHashMap<>();
        List<DungeonLayoutEdge> edges = new ArrayList<>();
        int sectorCount = Math.max(1, analysis.sectors().size());

        for (var node : graph.nodes()) {
            DungeonRoomFootprint footprint = footprintFor(node.type());
            DungeonCellPos cellOrigin = cellOriginFor(graph, analysis, node.id(), footprint, sectorCount, drafts);
            Draft draft = new Draft(node.id(), node.type(), cellOrigin, footprint);
            drafts.put(node.id(), draft);
            connectors.put(node.id(), EnumSet.noneOf(DungeonConnectorSide.class));
        }

        for (DungeonGraphEdge graphEdge : graph.edges()) {
            addEmbeddedEdge(edges, connectors, drafts, graphEdge);
        }

        List<DungeonLayoutNode> nodes = drafts.values()
                .stream()
                .map(draft -> new DungeonLayoutNode(
                        draft.id,
                        draft.type,
                        draft.cellOrigin,
                        draft.footprint,
                        connectors.get(draft.id)
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

    private static DungeonCellPos cellOriginFor(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            String nodeId,
            DungeonRoomFootprint footprint,
            int sectorCount,
            Map<String, Draft> drafts
    ) {
        if (nodeId.equals(graph.rootNodeId())) {
            return new DungeonCellPos(-footprint.widthCells() / 2, 0, -footprint.depthCells() / 2);
        }

        if (nodeId.equals(graph.exitNodeId())) {
            return resolveCollision(
                    new DungeonCellPos(-footprint.widthCells() / 2, 0, EXIT_OFFSET_CELLS),
                    footprint,
                    0.0D,
                    drafts
            );
        }

        DungeonNodeAnalysis node = analysis.requireNode(nodeId);
        int sector = node.sectorIndex().orElseThrow(() ->
                new IllegalArgumentException("Embedded node missing sector: " + nodeId));
        int distance = Math.max(1, node.distanceToBoss());
        double angle = Math.PI * 2.0D * sector / sectorCount;
        int radius = BASE_RADIUS_CELLS + RADIAL_SPACING_CELLS * distance;
        int centerX = (int) Math.round(Math.cos(angle) * radius);
        int centerZ = (int) Math.round(Math.sin(angle) * radius);
        DungeonCellPos base = new DungeonCellPos(
                centerX - footprint.widthCells() / 2,
                0,
                centerZ - footprint.depthCells() / 2
        );

        return resolveCollision(base, footprint, angle, drafts);
    }

    private static DungeonCellPos resolveCollision(
            DungeonCellPos base,
            DungeonRoomFootprint footprint,
            double angle,
            Map<String, Draft> drafts
    ) {
        for (int attempt = 0; attempt <= MAX_COLLISION_ATTEMPTS; attempt++) {
            DungeonCellPos candidate = offset(base, angle, attempt);
            DungeonCellBox candidateBox = footprint.toCellBox(candidate);
            boolean overlaps = drafts.values()
                    .stream()
                    .map(Draft::cellBox)
                    .anyMatch(candidateBox::intersects);

            if (!overlaps) {
                return candidate;
            }
        }

        throw new IllegalArgumentException(
                "Unable to resolve radial embedding collision near " + base
        );
    }

    private static DungeonCellPos offset(
            DungeonCellPos base,
            double angle,
            int attempt
    ) {
        if (attempt == 0) {
            return base;
        }

        int magnitude = (attempt + 1) / 2 * COLLISION_STEP_CELLS;
        int sign = attempt % 2 == 0 ? -1 : 1;
        int tangentX = (int) Math.round(-Math.sin(angle) * magnitude * sign);
        int tangentZ = (int) Math.round(Math.cos(angle) * magnitude * sign);
        int radialX = (attempt / 8) * (int) Math.round(Math.cos(angle) * COLLISION_STEP_CELLS);
        int radialZ = (attempt / 8) * (int) Math.round(Math.sin(angle) * COLLISION_STEP_CELLS);

        return new DungeonCellPos(
                base.x() + tangentX + radialX,
                base.y(),
                base.z() + tangentZ + radialZ
        );
    }

    private static void addEmbeddedEdge(
            List<DungeonLayoutEdge> edges,
            Map<String, EnumSet<DungeonConnectorSide>> connectors,
            Map<String, Draft> drafts,
            DungeonGraphEdge graphEdge
    ) {
        Draft source = drafts.get(graphEdge.sourceNodeId());
        Draft target = drafts.get(graphEdge.targetNodeId());

        if (source == null || target == null) {
            throw new IllegalArgumentException(
                    "Cannot embed graph edge before both endpoints are placed: " + graphEdge.id()
            );
        }

        DungeonConnectorSide sourceSide = connectorSide(source, target);
        DungeonConnectorSide targetSide = sourceSide.opposite();
        connectors.get(graphEdge.sourceNodeId()).add(sourceSide);
        connectors.get(graphEdge.targetNodeId()).add(targetSide);

        edges.add(new DungeonLayoutEdge(
                graphEdge.id().replaceFirst("^(tree|loop|secret)_", "corridor_$1_"),
                graphEdge.sourceNodeId(),
                graphEdge.targetNodeId(),
                sourceSide,
                targetSide,
                graphEdge.kind() == io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdgeKind.LOOP ? 1 : 1,
                graphEdge.kind()
        ));
    }

    private static DungeonConnectorSide connectorSide(
            Draft source,
            Draft target
    ) {
        int sourceCenterX = source.cellOrigin.x() + source.footprint.widthCells() / 2;
        int sourceCenterZ = source.cellOrigin.z() + source.footprint.depthCells() / 2;
        int targetCenterX = target.cellOrigin.x() + target.footprint.widthCells() / 2;
        int targetCenterZ = target.cellOrigin.z() + target.footprint.depthCells() / 2;
        int dx = targetCenterX - sourceCenterX;
        int dz = targetCenterZ - sourceCenterZ;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? DungeonConnectorSide.EAST : DungeonConnectorSide.WEST;
        }

        return dz >= 0 ? DungeonConnectorSide.SOUTH : DungeonConnectorSide.NORTH;
    }

    private static DungeonRoomFootprint footprintFor(DungeonRoomType type) {
        return switch (type) {
            case START, EXIT, TREASURE -> new DungeonRoomFootprint(2, 1, 2);
            case COMBAT -> new DungeonRoomFootprint(3, 1, 3);
            case BOSS -> new DungeonRoomFootprint(5, 1, 5);
        };
    }

    private record Draft(
            String id,
            DungeonRoomType type,
            DungeonCellPos cellOrigin,
            DungeonRoomFootprint footprint
    ) {
        private DungeonCellBox cellBox() {
            return this.footprint.toCellBox(this.cellOrigin);
        }
    }
}
