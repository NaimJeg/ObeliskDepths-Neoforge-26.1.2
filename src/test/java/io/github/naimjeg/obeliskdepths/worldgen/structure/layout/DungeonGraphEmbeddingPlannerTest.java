package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import net.minecraft.core.BlockPos;

public final class DungeonGraphEmbeddingPlannerTest {
    private DungeonGraphEmbeddingPlannerTest() {
    }

    public static void main(String[] args) {
        deterministicEmbedding();
        radialEmbeddingPlacesBossNearOrigin();
        noRoomOverlap();
        loopEdgesAreEmitted();
        validConnectorDirections();
        primaryEntryAnchorInsidePrimaryEntryBounds();
    }

    private static void deterministicEmbedding() {
        DungeonGraph graph = DungeonGraphGenerator.generate(12345L);
        BlockPos layoutOrigin = new BlockPos(100, 40, -200);

        DungeonLayoutPlan first = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);
        DungeonLayoutPlan second = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);

        assertEquals(first, second, "embedding should be deterministic");
    }

    private static void radialEmbeddingPlacesBossNearOrigin() {
        DungeonLayoutPlan plan = representativePlan();
        DungeonLayoutNode boss = plan.requireNode("boss");

        assertTrue(Math.abs(boss.cellOrigin().x()) <= boss.footprint().widthCells(), "boss near origin x");
        assertTrue(Math.abs(boss.cellOrigin().z()) <= boss.footprint().depthCells(), "boss near origin z");
    }

    private static void noRoomOverlap() {
        DungeonLayoutPlan plan = representativePlan();

        for (int i = 0; i < plan.nodes().size(); i++) {
            for (int j = i + 1; j < plan.nodes().size(); j++) {
                assertFalse(
                        plan.nodes().get(i).intersects(plan.nodes().get(j)),
                        "rooms should not overlap: "
                                + plan.nodes().get(i).roomId()
                                + " and "
                                + plan.nodes().get(j).roomId()
                );
            }
        }
    }

    private static void loopEdgesAreEmitted() {
        DungeonLayoutPlan plan = representativePlan();
        long loops = plan.edges().stream()
                .filter(edge -> edge.kind() == DungeonGraphEdgeKind.LOOP)
                .count();

        assertTrue(loops > 0, "loop edges should be emitted to debug layout");
    }

    private static void validConnectorDirections() {
        DungeonSpatialLayoutValidator.validate(representativePlan());
    }

    private static void primaryEntryAnchorInsidePrimaryEntryBounds() {
        BlockPos layoutOrigin = new BlockPos(0, 32, 0);
        DungeonGraph graph = DungeonGraphGenerator.generate(42L);
        DungeonLayoutPlan plan = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);
        DungeonPiecePlan piecePlan = DungeonPiecePlanCompiler.compile(
                layoutOrigin,
                plan,
                graph.primaryEntryNodeId()
        );
        DungeonPieceMetadata primary = piecePlan.pieces()
                .stream()
                .filter(piece -> piece.id().equals(graph.primaryEntryNodeId()))
                .findFirst()
                .orElseThrow();

        assertTrue(primary.bounds().isInside(primary.anchor()), "primary entry anchor should be inside bounds");
        assertSame(DungeonRoomType.START, primary.role().roomType(), "primary entry is START");
    }

    private static DungeonLayoutPlan representativePlan() {
        DungeonGraph graph = null;

        for (int seed = 0; seed < 256; seed++) {
            DungeonGraph candidate = DungeonGraphGenerator.generate(0xCAFEF00DL + seed);

            if (DungeonGraphAnalyzer.analyze(candidate).sectors().size() >= 3) {
                graph = candidate;
                break;
            }
        }

        assertTrue(graph != null, "representative graph has radial sectors");
        return DungeonGraphEmbeddingPlanner.embed(
                graph,
                new BlockPos(-64, 20, 128)
        );
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(
            boolean value,
            String message
    ) {
        if (value) {
            throw new AssertionError(message);
        }
    }
}
