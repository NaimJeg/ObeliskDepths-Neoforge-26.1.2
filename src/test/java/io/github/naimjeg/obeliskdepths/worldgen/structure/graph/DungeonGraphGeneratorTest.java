package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonCorridorRouter;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class DungeonGraphGeneratorTest {
    private DungeonGraphGeneratorTest() {
    }

    public static void main(String[] args) throws Exception {
        sameSeedProducesEqualGraph();
        differentSeedsVaryTopology();
        invalidGraphs();
        debugCommandHasFixedSyntax();
        bulkSeedsGenerateValidateAnalyzeEmbedAndCompile();
    }

    private static void sameSeedProducesEqualGraph() {
        DungeonGraph first = DungeonGraphGenerator.generate(0x5EED5EEDL);
        DungeonGraph second = DungeonGraphGenerator.generate(0x5EED5EEDL);

        assertEquals(first, second, "same seed should produce identical graph");
    }

    private static void differentSeedsVaryTopology() {
        Set<String> signatures = new LinkedHashSet<>();

        for (int seed = 0; seed < 24; seed++) {
            DungeonGraph graph = DungeonGraphGenerator.generate(seed);
            signatures.add(graph.nodes().size()
                    + "/"
                    + graph.treeEdges().size()
                    + "/"
                    + graph.loopEdges().size()
                    + "/"
                    + graph.entryNodeIds().size());
        }

        assertTrue(signatures.size() > 1, "different seeds should vary topology");
    }

    private static void bulkSeedsGenerateValidateAnalyzeEmbedAndCompile() {
        for (int index = 0; index < 1_000; index++) {
            long seed = index * 0x9E3779B97F4A7C15L;
            DungeonGraph graph = DungeonGraphGenerator.generate(seed);
            DungeonGraphValidator.validate(graph);
            DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);

            assertEquals(graph, DungeonGraphGenerator.generate(seed), "deterministic graph for seed " + seed);
            assertEquals(1L, countType(graph, DungeonRoomType.BOSS), "one boss");
            assertTrue(graph.bossNode().id().equals(graph.rootNodeId()), "boss is root");
            assertTrue(graph.entryNodeIds().size() >= 2, "multiple starts");
            assertEquals((long) graph.entryNodeIds().size(), countType(graph, DungeonRoomType.START), "entries are starts");
            assertTrue(graph.entryNodeIds().contains(graph.primaryEntryNodeId()), "primary entry is valid");
            assertTrue(graph.loopEdges().size() > 0, "at least one loop edge");
            assertTrue(graph.loopEdges().size() <= 2, "loop budget respected");
            assertTrue(graph.nodes().size() <= DungeonGraphGenerationConfig.DEFAULT.maxNodeCount(), "node budget respected");
            assertTrue(graph.edges().size() - graph.nodes().size() + 1 >= 1, "at least one cycle");
            assertTreeParents(graph, analysis);
            assertStartsReachBoss(graph);
            assertEntryDistribution(graph, analysis);
            assertTreasureTerminal(graph, analysis);

            BlockPos layoutOrigin = new BlockPos(index * 31, 32, index * -29);
            DungeonLayoutPlan layout = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);
            DungeonPiecePlan piecePlan;
            try {
                piecePlan = DungeonPiecePlanCompiler.compile(
                        layoutOrigin,
                        layout,
                        graph.primaryEntryNodeId()
                );
            } catch (RuntimeException exception) {
                throw new AssertionError(
                        "Failed to compile seed "
                                + seed
                                + " nodes="
                                + graph.nodes().size()
                                + " treeEdges="
                                + graph.treeEdges().size()
                                + " loopEdges="
                                + graph.loopEdges().size(),
                        exception
                );
            }
            assertEquals(graph.primaryEntryNodeId(), piecePlan.primaryEntryRoomId(), "primary entry survives piece plan");
            assertTrue(piecePlan.routedCorridors().stream()
                    .allMatch(route -> route.lengthCells() <= DungeonCorridorRouter.MAX_INDIVIDUAL_ROUTE_CELLS), "individual route budget respected");
            assertTrue(piecePlan.routedCorridors()
                    .stream()
                    .mapToInt(io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonRoutedCorridor::lengthCells)
                    .sum() <= DungeonCorridorRouter.MAX_TOTAL_ROUTE_CELLS, "total route budget respected");
            assertPieceBounds(piecePlan);
        }
    }

    private static void invalidGraphs() {
        assertInvalid(
                new DungeonGraph(
                        "boss",
                        Set.of("start_0", "start_1"),
                        "start_0",
                        List.of(
                                node("boss", DungeonRoomType.BOSS),
                                node("boss", DungeonRoomType.COMBAT),
                                node("start_0", DungeonRoomType.START),
                                node("start_1", DungeonRoomType.START)
                        ),
                        List.of()
                ),
                "Duplicate graph node id"
        );

        assertInvalid(
                validGraphWith(new DungeonGraphEdge("bad_missing", "boss", "missing", DungeonGraphEdgeKind.TREE)),
                "missing target node"
        );

        assertInvalid(
                new DungeonGraph(
                        "boss",
                        Set.of("start_0", "start_1"),
                        "start_0",
                        validNodes(),
                        List.of(
                                edge("tree_boss_a", "boss", "a", DungeonGraphEdgeKind.TREE),
                                edge("tree_a_b", "a", "b", DungeonGraphEdgeKind.TREE),
                                edge("tree_start_0_a", "start_0", "a", DungeonGraphEdgeKind.TREE),
                                edge("tree_b_start_0", "b", "start_0", DungeonGraphEdgeKind.TREE),
                                edge("tree_b_start_1", "b", "start_1", DungeonGraphEdgeKind.TREE),
                                edge("loop_a_b", "a", "b", DungeonGraphEdgeKind.LOOP)
                        )
                ),
                "TREE parent"
        );

        assertInvalid(
                new DungeonGraph(
                        "boss",
                        Set.of("start_0"),
                        "start_0",
                        validNodes(),
                        validEdges()
                ),
                "entryNodeIds"
        );

        assertInvalid(
                validGraphWith(edge("loop_boss_a", "boss", "a", DungeonGraphEdgeKind.LOOP)),
                "duplicates TREE"
        );

        assertInvalid(
                new DungeonGraph(
                        "boss",
                        Set.of("start_0", "start_1"),
                        "start_0",
                        withTreasureNode(),
                        withTreasureEdges()
                ),
                "TREASURE nodes must be terminal"
        );
    }

    private static void debugCommandHasFixedSyntax() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/command/DungeonDebugLayoutCommands.java"
        ));

        assertTrue(source.contains("Commands.literal(\"layout-test\")"), "layout-test command exists");
        String removedArgumentName = "pro" + "file";
        assertFalse(source.contains("Commands.argument(\"" + removedArgumentName + "\""), "layout-test should not accept a graph-size argument");
    }

    private static DungeonGraph validGraphWith(DungeonGraphEdge extraEdge) {
        List<DungeonGraphEdge> edges = new java.util.ArrayList<>(validEdges());
        edges.add(extraEdge);
        return new DungeonGraph(
                "boss",
                Set.of("start_0", "start_1"),
                "start_0",
                validNodes(),
                edges
        );
    }

    private static List<DungeonGraphNode> validNodes() {
        return List.of(
                node("boss", DungeonRoomType.BOSS),
                node("a", DungeonRoomType.COMBAT),
                node("b", DungeonRoomType.COMBAT),
                node("a2", DungeonRoomType.COMBAT),
                node("b2", DungeonRoomType.COMBAT),
                node("a3", DungeonRoomType.COMBAT),
                node("b3", DungeonRoomType.COMBAT),
                node("a4", DungeonRoomType.COMBAT),
                node("b4", DungeonRoomType.COMBAT),
                node("start_0", DungeonRoomType.START),
                node("start_1", DungeonRoomType.START)
        );
    }

    private static List<DungeonGraphEdge> validEdges() {
        return List.of(
                edge("tree_boss_a", "boss", "a", DungeonGraphEdgeKind.TREE),
                edge("tree_a_a2", "a", "a2", DungeonGraphEdgeKind.TREE),
                edge("tree_a2_a3", "a2", "a3", DungeonGraphEdgeKind.TREE),
                edge("tree_a3_a4", "a3", "a4", DungeonGraphEdgeKind.TREE),
                edge("tree_a4_start_0", "a4", "start_0", DungeonGraphEdgeKind.TREE),
                edge("tree_boss_b", "boss", "b", DungeonGraphEdgeKind.TREE),
                edge("tree_b_b2", "b", "b2", DungeonGraphEdgeKind.TREE),
                edge("tree_b2_b3", "b2", "b3", DungeonGraphEdgeKind.TREE),
                edge("tree_b3_b4", "b3", "b4", DungeonGraphEdgeKind.TREE),
                edge("tree_b4_start_1", "b4", "start_1", DungeonGraphEdgeKind.TREE),
                edge("loop_a_b", "a", "b", DungeonGraphEdgeKind.LOOP)
        );
    }

    private static List<DungeonGraphNode> withTreasureNode() {
        java.util.ArrayList<DungeonGraphNode> nodes = new java.util.ArrayList<>(validNodes());
        nodes.add(node("treasure", DungeonRoomType.TREASURE));
        return nodes;
    }

    private static List<DungeonGraphEdge> withTreasureEdges() {
        java.util.ArrayList<DungeonGraphEdge> edges = new java.util.ArrayList<>(validEdges());
        edges.add(edge("tree_a2_treasure", "a2", "treasure", DungeonGraphEdgeKind.TREE));
        edges.add(edge("loop_treasure_b2", "treasure", "b2", DungeonGraphEdgeKind.LOOP));
        return edges;
    }

    private static DungeonGraphNode node(
            String id,
            DungeonRoomType type
    ) {
        return new DungeonGraphNode(id, type);
    }

    private static DungeonGraphEdge edge(
            String id,
            String source,
            String target,
            DungeonGraphEdgeKind kind
    ) {
        return new DungeonGraphEdge(id, source, target, kind);
    }

    private static long countType(
            DungeonGraph graph,
            DungeonRoomType type
    ) {
        return graph.nodes().stream().filter(node -> node.type() == type).count();
    }

    private static void assertTreeParents(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis
    ) {
        assertTrue(!analysis.treeParentByNode().containsKey(graph.rootNodeId()), "boss has no tree parent");
        for (DungeonGraphNode node : graph.nodes()) {
            if (node.id().equals(graph.rootNodeId())) {
                continue;
            }
            assertTrue(analysis.treeParentByNode().containsKey(node.id()), "normal node has tree parent: " + node.id());
        }
    }

    private static void assertStartsReachBoss(DungeonGraph graph) {
        for (String start : graph.entryNodeIds()) {
            assertTrue(reaches(graph, start, graph.rootNodeId()), "start reaches boss: " + start);
        }
    }

    private static void assertEntryDistribution(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis
    ) {
        Set<Integer> sectors = new LinkedHashSet<>();
        for (String entry : graph.entryNodeIds()) {
            DungeonNodeAnalysis node = analysis.requireNode(entry);
            assertTrue(node.sectorIndex().isPresent(), "entry has sector");
            assertTrue(sectors.add(node.sectorIndex().getAsInt()), "entries use distinct sectors");
            assertTrue(node.treeDepth() >= DungeonGraphGenerationConfig.DEFAULT.minArmDepth(), "entry is in outer depth band");
            assertTrue(node.distanceToBoss() > 1, "entry is not adjacent to boss");
        }
    }

    private static void assertTreasureTerminal(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis
    ) {
        for (DungeonGraphNode node : graph.nodes()) {
            if (node.type() == DungeonRoomType.TREASURE) {
                assertTrue(analysis.requireNode(node.id()).totalDegree() <= 1, "treasure terminal: " + node.id());
            }
        }
    }

    private static void assertPieceBounds(DungeonPiecePlan piecePlan) {
        for (DungeonPieceMetadata piece : piecePlan.pieces()) {
            assertTrue(
                    contains(piecePlan.siteBounds(), piece.bounds()),
                    "site bounds contain piece " + piece.id()
            );
        }
    }

    private static boolean reaches(
            DungeonGraph graph,
            String source,
            String target
    ) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(target)) {
                return true;
            }
            queue.addAll(graph.neighbors(current));
        }

        return false;
    }

    private static boolean contains(
            net.minecraft.world.level.levelgen.structure.BoundingBox outer,
            net.minecraft.world.level.levelgen.structure.BoundingBox inner
    ) {
        return inner.minX() >= outer.minX()
                && inner.maxX() <= outer.maxX()
                && inner.minY() >= outer.minY()
                && inner.maxY() <= outer.maxY()
                && inner.minZ() >= outer.minZ()
                && inner.maxZ() <= outer.maxZ();
    }

    private static void assertInvalid(
            DungeonGraph graph,
            String expectedMessagePart
    ) {
        try {
            DungeonGraphValidator.validate(graph);
        } catch (DungeonGraphValidationException exception) {
            assertTrue(
                    exception.getMessage().contains(expectedMessagePart),
                    "expected validation message to contain '" + expectedMessagePart + "', got '" + exception.getMessage() + "'"
            );
            return;
        }

        throw new AssertionError("Expected invalid graph: " + expectedMessagePart);
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
