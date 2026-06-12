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

            if (separatedOnX(from, to)) {
                validateXAdjacency(corridor, from, to);
                continue;
            }

            if (separatedOnZ(from, to)) {
                validateZAdjacency(corridor, from, to);
                continue;
            }

            throw new IllegalStateException(
                    "Dungeon corridor endpoints are not separated by a clear X/Z gap: corridor="
                            + corridor.spec().id()
                            + " from="
                            + from.bounds()
                            + " to="
                            + to.bounds()
            );
        }
    }

    private static void validateXAdjacency(
            SolvedDungeonCorridor corridor,
            SolvedDungeonRoom from,
            SolvedDungeonRoom to
    ) {
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

        if (expectedCorridorMinX != corridor.bounds().minX()
                || expectedRightRoomMinX != right.bounds().minX()) {
            throw new IllegalStateException(
                    "Dungeon X corridor is not boundary-adjacent: corridor="
                            + corridor.spec().id()
                            + " left="
                            + left.bounds()
                            + " corridor="
                            + corridor.bounds()
                            + " right="
                            + right.bounds()
            );
        }
    }

    private static void validateZAdjacency(
            SolvedDungeonCorridor corridor,
            SolvedDungeonRoom from,
            SolvedDungeonRoom to
    ) {
        SolvedDungeonRoom north = from.bounds().minZ() <= to.bounds().minZ() ? from : to;
        SolvedDungeonRoom south = north == from ? to : from;
        int expectedCorridorMinZ = north.bounds().maxZ() + 1;
        int expectedSouthRoomMinZ = corridor.bounds().maxZ() + 1;

        ObeliskDepths.LOGGER.debug(
                "[OD layout] adjacency {}.maxZ + 1 == {}.minZ -> {} == {}",
                north.spec().id(),
                corridor.spec().id(),
                expectedCorridorMinZ,
                corridor.bounds().minZ()
        );
        ObeliskDepths.LOGGER.debug(
                "[OD layout] adjacency {}.maxZ + 1 == {}.minZ -> {} == {}",
                corridor.spec().id(),
                south.spec().id(),
                expectedSouthRoomMinZ,
                south.bounds().minZ()
        );

        if (expectedCorridorMinZ != corridor.bounds().minZ()
                || expectedSouthRoomMinZ != south.bounds().minZ()) {
            throw new IllegalStateException(
                    "Dungeon Z corridor is not boundary-adjacent: corridor="
                            + corridor.spec().id()
                            + " north="
                            + north.bounds()
                            + " corridor="
                            + corridor.bounds()
                            + " south="
                            + south.bounds()
            );
        }
    }

    private static boolean separatedOnX(
            SolvedDungeonRoom first,
            SolvedDungeonRoom second
    ) {
        return first.bounds().maxX() < second.bounds().minX()
                || second.bounds().maxX() < first.bounds().minX();
    }

    private static boolean separatedOnZ(
            SolvedDungeonRoom first,
            SolvedDungeonRoom second
    ) {
        return first.bounds().maxZ() < second.bounds().minZ()
                || second.bounds().maxZ() < first.bounds().minZ();
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
