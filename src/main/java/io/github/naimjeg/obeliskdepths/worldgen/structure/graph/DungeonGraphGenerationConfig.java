package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

record DungeonGraphGenerationConfig(
        int minSectorCount,
        int maxSectorCount,
        int minEntryCount,
        int maxEntryCount,
        int minArmDepth,
        int maxArmDepth,
        int guaranteedRingDepth,
        int maxLoopEdges,
        int optionalOuterLoopEdges,
        int minSideBranches,
        int maxSideBranches,
        int minSideBranchLength,
        int maxSideBranchLength,
        int maxNodeCount,
        int maxOrdinaryDegree,
        int minEntrySectorSeparation
) {
    static final DungeonGraphGenerationConfig DEFAULT = new DungeonGraphGenerationConfig(
            3,
            3,
            2,
            2,
            3,
            4,
            2,
            1,
            0,
            1,
            1,
            2,
            2,
            24,
            3,
            1
    );
}
