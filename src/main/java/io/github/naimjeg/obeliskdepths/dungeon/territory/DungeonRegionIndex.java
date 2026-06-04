package io.github.naimjeg.obeliskdepths.dungeon.territory;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.spatial.DungeonSpatialIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * @deprecated Use DungeonSpatialIndex instead.
 *
 * This class is kept temporarily to avoid broad call-site churn.
 * Do not add new logic here.
 */
@Deprecated(forRemoval = false)
public final class DungeonRegionIndex {
    private DungeonRegionIndex() {
    }

    public static Optional<DungeonInstanceId> findOwner(
            ServerLevel dungeonLevel,
            BlockPos pos
    ) {
        return DungeonSpatialIndex.findPhysicalOwnerAt(dungeonLevel, pos);
    }

    public static Optional<DungeonTerritory> findTerritory(
            ServerLevel dungeonLevel,
            BlockPos pos
    ) {
        return DungeonSpatialIndex.findTerritoryAt(dungeonLevel, pos);
    }
}