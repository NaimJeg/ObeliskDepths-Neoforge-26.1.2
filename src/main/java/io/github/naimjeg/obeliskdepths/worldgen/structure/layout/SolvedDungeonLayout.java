package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record SolvedDungeonLayout(
        List<SolvedDungeonRoom> rooms,
        List<SolvedDungeonCorridor> corridors
) {
    public SolvedDungeonLayout {
        rooms = List.copyOf(rooms);
        corridors = List.copyOf(corridors);

        validateUniqueRoomIds(rooms);
        validateUniqueCorridorIds(corridors);
    }

    public Optional<SolvedDungeonRoom> findRoom(String id) {
        return this.rooms.stream()
                .filter(room -> room.spec().id().equals(id))
                .findFirst();
    }

    public SolvedDungeonRoom room(String id) {
        return findRoom(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dungeon room id: " + id));
    }

    private static void validateUniqueRoomIds(List<SolvedDungeonRoom> rooms) {
        Set<String> seen = new HashSet<>();

        for (SolvedDungeonRoom room : rooms) {
            if (!seen.add(room.spec().id())) {
                throw new IllegalArgumentException("Duplicate dungeon room id: " + room.spec().id());
            }
        }
    }

    private static void validateUniqueCorridorIds(List<SolvedDungeonCorridor> corridors) {
        Set<String> seen = new HashSet<>();

        for (SolvedDungeonCorridor corridor : corridors) {
            if (!seen.add(corridor.spec().id())) {
                throw new IllegalArgumentException("Duplicate dungeon corridor id: " + corridor.spec().id());
            }
        }
    }
}
