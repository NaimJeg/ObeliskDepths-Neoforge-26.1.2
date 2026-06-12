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
            4,
            4,
            2,
            3,
            4,
            6,
            1,
            6,
            0,
            2,
            4,
            1,
            1,
            48,
            4,
            1
    );
}
