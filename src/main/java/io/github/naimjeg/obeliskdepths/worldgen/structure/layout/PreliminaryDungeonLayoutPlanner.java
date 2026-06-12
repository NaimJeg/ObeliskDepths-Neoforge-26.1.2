package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class PreliminaryDungeonLayoutPlanner {
    private PreliminaryDungeonLayoutPlanner() {
    }

    /*
     * Preliminary graph/layout generator.
     *
     * This is not the final Diablo-style generator. It produces a small,
     * deterministic, connected tree: START -> combat path -> BOSS -> EXIT, plus
     * profile-controlled optional treasure branch caps. Connector shape is logical
     * graph connectivity; physical shape is currently only rectangular cell
     * footprint. Future .nbt templates should provide authored connector
     * metadata and footprint information that this worldgen-side planner can use
     * to snap pieces together.
     */
    public static DungeonLayoutPlan plan(BlockPos layoutOrigin) {
        return plan(layoutOrigin, DungeonLayoutGenerationProfile.SMALL_TEST);
    }

    public static DungeonLayoutPlan plan(
            BlockPos layoutOrigin,
            DungeonLayoutGenerationProfile profile
    ) {
        for (int variant = 0; variant < 3; variant++) {
            try {
                return buildVariant(layoutOrigin, profile, variant);
            } catch (IllegalArgumentException ignored) {
                // Try the next deterministic variant before using the fixed fallback.
            }
        }

        return fixedFallback(profile);
    }

    public static DungeonLayoutPlan fixedFallback(DungeonLayoutGenerationProfile profile) {
        return build(profile, 0L);
    }

    private static DungeonLayoutPlan buildVariant(
            BlockPos layoutOrigin,
            DungeonLayoutGenerationProfile profile,
            int variant
    ) {
        long seed = layoutOrigin.asLong() ^ (long) variant * 0x9E3779B97F4A7C15L;

        return build(profile, seed);
    }

    private static DungeonLayoutPlan build(
            DungeonLayoutGenerationProfile profile,
            long seed
    ) {
        List<DungeonLayoutNode> nodes = new ArrayList<>();
        List<DungeonLayoutEdge> edges = new ArrayList<>();
        Map<String, EnumSet<DungeonConnectorSide>> connectors = new LinkedHashMap<>();
        Map<String, NodeDraft> drafts = new LinkedHashMap<>();

        int criticalPathLength = profile.criticalPathLength(seed);
        int combatCount = Math.max(1, criticalPathLength - 3);
        int branchCount = Math.min(profile.branches(seed), combatCount);
        int cursorX = 0;

        addDraft(drafts, connectors, new NodeDraft(
                "start",
                DungeonRoomType.START,
                new DungeonCellPos(cursorX, 0, 0),
                new DungeonRoomFootprint(2, 1, 2),
                true,
                false
        ));

        String previous = "start";

        for (int index = 1; index <= combatCount; index++) {
            cursorX += index == 1 ? 4 : 5;
            String combatId = String.format("combat_%02d", index);
            addDraft(drafts, connectors, new NodeDraft(
                    combatId,
                    DungeonRoomType.COMBAT,
                    new DungeonCellPos(cursorX, 0, 0),
                    new DungeonRoomFootprint(3, 1, 3),
                    true,
                    false
            ));
            addEdge(edges, connectors, previous, combatId, DungeonConnectorSide.EAST);
            previous = combatId;
        }

        cursorX += 5;
        addDraft(drafts, connectors, new NodeDraft(
                "boss",
                DungeonRoomType.BOSS,
                new DungeonCellPos(cursorX, 0, -1),
                new DungeonRoomFootprint(5, 1, 5),
                true,
                false
        ));
        addEdge(edges, connectors, previous, "boss", DungeonConnectorSide.EAST);

        cursorX += 7;
        addDraft(drafts, connectors, new NodeDraft(
                "exit",
                DungeonRoomType.EXIT,
                new DungeonCellPos(cursorX, 0, 0),
                new DungeonRoomFootprint(2, 1, 2),
                true,
                false
        ));
        addEdge(edges, connectors, "boss", "exit", DungeonConnectorSide.EAST);

        for (int branch = 1; branch <= branchCount; branch++) {
            int combatIndex = 1 + Math.floorMod(branch * 2 - 1, combatCount);
            String combatId = String.format("combat_%02d", combatIndex);
            NodeDraft combat = drafts.get(combatId);
            boolean south = branch % 2 == 1;
            DungeonConnectorSide side = south ? DungeonConnectorSide.SOUTH : DungeonConnectorSide.NORTH;
            int branchZ = south
                    ? combat.origin.z() + combat.footprint.depthCells() + 2
                    : combat.origin.z() - 4;
            String treasureId = String.format("treasure_%02d", branch);

            addDraft(drafts, connectors, new NodeDraft(
                    treasureId,
                    DungeonRoomType.TREASURE,
                    new DungeonCellPos(combat.origin.x(), 0, branchZ),
                    new DungeonRoomFootprint(2, 1, 2),
                    false,
                    true
            ));
            addEdge(edges, connectors, combatId, treasureId, side);
        }

        for (NodeDraft draft : drafts.values()) {
            nodes.add(new DungeonLayoutNode(
                    draft.id,
                    draft.type,
                    draft.origin,
                    draft.footprint,
                    connectors.get(draft.id),
                    draft.criticalPath,
                    draft.branchCap
            ));
        }

        return new DungeonLayoutPlan(
                DungeonLayoutConstants.CELL_SIZE,
                nodes,
                edges
        );
    }

    private static void addDraft(
            Map<String, NodeDraft> drafts,
            Map<String, EnumSet<DungeonConnectorSide>> connectors,
            NodeDraft draft
    ) {
        drafts.put(draft.id, draft);
        connectors.put(draft.id, EnumSet.noneOf(DungeonConnectorSide.class));
    }

    private static void addEdge(
            List<DungeonLayoutEdge> edges,
            Map<String, EnumSet<DungeonConnectorSide>> connectors,
            String fromRoomId,
            String toRoomId,
            DungeonConnectorSide fromSide
    ) {
        DungeonConnectorSide toSide = fromSide.opposite();
        connectors.get(fromRoomId).add(fromSide);
        connectors.get(toRoomId).add(toSide);

        edges.add(new DungeonLayoutEdge(
                "corridor_" + fromRoomId + "_" + toRoomId,
                fromRoomId,
                toRoomId,
                fromSide,
                toSide,
                1
        ));
    }

    private record NodeDraft(
            String id,
            DungeonRoomType type,
            DungeonCellPos origin,
            DungeonRoomFootprint footprint,
            boolean criticalPath,
            boolean branchCap
    ) {
    }
}
