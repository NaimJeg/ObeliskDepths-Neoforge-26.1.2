package io.github.naimjeg.obeliskdepths.dungeon.corridor;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DungeonCorridorDefinitionRegistry {
    private static volatile Map<Identifier, DungeonCorridorDefinition>
            CORRIDORS = Map.of();

    private DungeonCorridorDefinitionRegistry() {
    }

    public static Optional<DungeonCorridorDefinition> get(Identifier id) {
        return Optional.ofNullable(CORRIDORS.get(id));
    }

    public static Map<Identifier, DungeonCorridorDefinition> snapshot() {
        return CORRIDORS;
    }

    public static void replace(
            Map<Identifier, DungeonCorridorDefinition> corridors
    ) {
        CORRIDORS = immutableOrderedCopy(corridors);
    }

    public static void clearForTests() {
        CORRIDORS = Map.of();
    }

    private static Map<Identifier, DungeonCorridorDefinition> immutableOrderedCopy(
            Map<Identifier, DungeonCorridorDefinition> corridors
    ) {
        if (corridors == null || corridors.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, DungeonCorridorDefinition> copy =
                new LinkedHashMap<>();
        corridors.entrySet()
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
