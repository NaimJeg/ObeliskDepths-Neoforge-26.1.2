package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ObeliskDungeonLayoutValidator {
    private ObeliskDungeonLayoutValidator() {
    }

    public static void validate(SolvedDungeonLayout layout) {
        validateUniqueRoomIds(layout.rooms());
        validateUniqueCorridorIds(layout.corridors());
        validateConnectionEndpoints(layout);
        validateRoomSeparation(layout.rooms());
        validateCorridorRoomSeparation(layout);
        validateCorridorAdjacency(layout);

        ObeliskDepths.LOGGER.debug(
                "[OD layout] validation passed: rooms={}, corridors={}",
                layout.rooms().size(),
                layout.corridors().size()
        );
    }

    private static void validateUniqueRoomIds(List<SolvedDungeonRoom> rooms) {
        Set<String> seen = new HashSet<>();

        for (SolvedDungeonRoom room : rooms) {
            if (!seen.add(room.spec().id())) {
                throw new IllegalStateException("Duplicate dungeon room id: " + room.spec().id());
            }
        }
    }

    private static void validateUniqueCorridorIds(List<SolvedDungeonCorridor> corridors) {
        Set<String> seen = new HashSet<>();

        for (SolvedDungeonCorridor corridor : corridors) {
            if (!seen.add(corridor.spec().id())) {
                throw new IllegalStateException("Duplicate dungeon corridor id: " + corridor.spec().id());
            }
        }
    }

    private static void validateConnectionEndpoints(SolvedDungeonLayout layout) {
        for (SolvedDungeonCorridor corridor : layout.corridors()) {
            if (layout.findRoom(corridor.spec().fromRoomId()).isEmpty()) {
                throw new IllegalStateException(
                        "Corridor "
                                + corridor.spec().id()
                                + " has missing from room "
                                + corridor.spec().fromRoomId()
                );
            }

            if (layout.findRoom(corridor.spec().toRoomId()).isEmpty()) {
                throw new IllegalStateException(
                        "Corridor "
                                + corridor.spec().id()
                                + " has missing to room "
                                + corridor.spec().toRoomId()
                );
            }
        }
    }

    private static void validateRoomSeparation(List<SolvedDungeonRoom> rooms) {
        for (int i = 0; i < rooms.size(); i++) {
            SolvedDungeonRoom first = rooms.get(i);

            for (int j = i + 1; j < rooms.size(); j++) {
                SolvedDungeonRoom second = rooms.get(j);

                if (intersects(first.bounds(), second.bounds())) {
                    throw new IllegalStateException(
                            "Dungeon rooms overlap: "
                                    + first.spec().id()
                                    + " bounds="
                                    + first.bounds()
                                    + ", "
                                    + second.spec().id()
                                    + " bounds="
                                    + second.bounds()
                    );
                }
            }
        }
    }

    private static void validateCorridorRoomSeparation(SolvedDungeonLayout layout) {
        for (SolvedDungeonCorridor corridor : layout.corridors()) {
            for (SolvedDungeonRoom room : layout.rooms()) {
                if (intersects(corridor.bounds(), room.bounds())) {
                    throw new IllegalStateException(
                            "Dungeon corridor overlaps room: corridor="
                                    + corridor.spec().id()
                                    + " bounds="
                                    + corridor.bounds()
                                    + ", room="
                                    + room.spec().id()
                                    + " bounds="
                                    + room.bounds()
                    );
                }
            }
        }
    }

    private static void validateCorridorAdjacency(SolvedDungeonLayout layout) {
        for (SolvedDungeonCorridor corridor : layout.corridors()) {
            SolvedDungeonRoom from = layout.room(corridor.spec().fromRoomId());
            SolvedDungeonRoom to = layout.room(corridor.spec().toRoomId());
            SolvedDungeonRoom left = from.bounds().minX() <= to.bounds().minX() ? from : to;
            SolvedDungeonRoom right = left == from ? to : from;

            int expectedCorridorMinX = left.bounds().maxX() + 1;
            int expectedRightRoomMinX = corridor.bounds().maxX() + 1;

            ObeliskDepths.LOGGER.debug(
                    "[OD layout] adjacency {}.maxX + 1 == {}.minX -> {} == {}",
                    left.spec().id(),
                    corridor.spec().id(),
                    expectedCorridorMinX,
                    corridor.bounds().minX()
            );
            ObeliskDepths.LOGGER.debug(
                    "[OD layout] adjacency {}.maxX + 1 == {}.minX -> {} == {}",
                    corridor.spec().id(),
                    right.spec().id(),
                    expectedRightRoomMinX,
                    right.bounds().minX()
            );

            if (expectedCorridorMinX != corridor.bounds().minX()) {
                throw new IllegalStateException(
                        "Dungeon corridor is not adjacent to left room: corridor="
                                + corridor.spec().id()
                                + " room="
                                + left.spec().id()
                                + " roomBounds="
                                + left.bounds()
                                + " corridorBounds="
                                + corridor.bounds()
                );
            }

            if (expectedRightRoomMinX != right.bounds().minX()) {
                throw new IllegalStateException(
                        "Dungeon corridor is not adjacent to right room: corridor="
                                + corridor.spec().id()
                                + " room="
                                + right.spec().id()
                                + " roomBounds="
                                + right.bounds()
                                + " corridorBounds="
                                + corridor.bounds()
                );
            }
        }
    }

    private static boolean intersects(BoundingBox first, BoundingBox second) {
        return first.minX() <= second.maxX()
                && first.maxX() >= second.minX()
                && first.minY() <= second.maxY()
                && first.maxY() >= second.minY()
                && first.minZ() <= second.maxZ()
                && first.maxZ() >= second.minZ();
    }
}
