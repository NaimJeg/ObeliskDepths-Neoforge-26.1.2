package io.github.naimjeg.obeliskdepths.dungeon.room;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DungeonRoomDefinitionRegistry {
    private static volatile Map<Identifier, DungeonRoomDefinition> ROOMS =
            Map.of();

    private DungeonRoomDefinitionRegistry() {
    }

    public static Optional<DungeonRoomDefinition> get(Identifier id) {
        return Optional.ofNullable(ROOMS.get(id));
    }

    public static Map<Identifier, DungeonRoomDefinition> snapshot() {
        return ROOMS;
    }

    public static void replace(Map<Identifier, DungeonRoomDefinition> rooms) {
        ROOMS = immutableOrderedCopy(rooms);
    }

    public static void clearForTests() {
        ROOMS = Map.of();
    }

    private static Map<Identifier, DungeonRoomDefinition> immutableOrderedCopy(
            Map<Identifier, DungeonRoomDefinition> rooms
    ) {
        if (rooms == null || rooms.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, DungeonRoomDefinition> copy = new LinkedHashMap<>();
        rooms.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Identifier::toString)
                ))
                .forEach(entry -> copy.put(entry.getKey(), entry.getValue()));

        return Collections.unmodifiableMap(copy);
    }
}
