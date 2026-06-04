package io.github.naimjeg.obeliskdepths.dungeon.territory;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import net.minecraft.core.BlockPos;

public final class DungeonRegionAllocator {
    private static final int SPACING = 2048;
    private static final int SIZE = 512;

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 127;
    private static final int START_Y = 10;

    /*
     * Leave a safety margin below the hard world coordinate limit.
     * This is intentionally lower than the absolute vanilla limit.
     */
    private static final int SAFE_WORLD_LIMIT = 29_000_000;

    private static final int MAX_GRID_COORDINATE =
            (SAFE_WORLD_LIMIT - SIZE) / SPACING;

    private DungeonRegionAllocator() {
    }

    public static DungeonTerritory allocateTerritory(int regionIndex) {
        if (regionIndex < 0) {
            throw new IllegalArgumentException("regionIndex must be non-negative: " + regionIndex);
        }

        GridPosition gridPosition = toSpiralGridPosition(regionIndex);

        validateGridPosition(regionIndex, gridPosition);

        int minX = Math.multiplyExact(gridPosition.x(), SPACING);
        int minZ = Math.multiplyExact(gridPosition.z(), SPACING);

        int maxX = Math.addExact(minX, SIZE);
        int maxZ = Math.addExact(minZ, SIZE);

        DungeonBounds bounds = new DungeonBounds(
                minX,
                MIN_Y,
                minZ,
                maxX,
                MAX_Y,
                maxZ
        );

        BlockPos startPos = new BlockPos(
                minX + 8,
                START_Y,
                minZ + 8
        );

        return new DungeonTerritory(
                DungeonTerritoryId.create(),
                null,
                bounds,
                startPos
        );
    }

    private static GridPosition toSpiralGridPosition(int regionIndex) {
        if (regionIndex == 0) {
            return new GridPosition(0, 0);
        }

        int ring = (int) Math.ceil((Math.sqrt(regionIndex + 1.0D) - 1.0D) / 2.0D);
        int sideLength = ring * 2;
        int maxIndexInRing = ((ring * 2 + 1) * (ring * 2 + 1)) - 1;
        int offsetFromMax = maxIndexInRing - regionIndex;

        if (offsetFromMax < sideLength) {
            return new GridPosition(
                    ring - offsetFromMax,
                    -ring
            );
        }

        if (offsetFromMax < sideLength * 2) {
            return new GridPosition(
                    -ring,
                    -ring + (offsetFromMax - sideLength)
            );
        }

        if (offsetFromMax < sideLength * 3) {
            return new GridPosition(
                    -ring + (offsetFromMax - sideLength * 2),
                    ring
            );
        }

        return new GridPosition(
                ring,
                ring - (offsetFromMax - sideLength * 3)
        );
    }

    private static void validateGridPosition(
            int regionIndex,
            GridPosition gridPosition
    ) {
        if (Math.abs(gridPosition.x()) > MAX_GRID_COORDINATE
                || Math.abs(gridPosition.z()) > MAX_GRID_COORDINATE) {
            throw new IllegalStateException(
                    "Dungeon region index " + regionIndex
                            + " maps outside the safe world allocation area: "
                            + gridPosition
            );
        }
    }

    private record GridPosition(int x, int z) {
    }
}