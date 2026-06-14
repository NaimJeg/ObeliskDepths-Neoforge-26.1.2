package io.github.naimjeg.obeliskdepths.dungeon.theme;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DungeonThemeDefinitionRegistry {
    private static volatile Map<Identifier, DungeonThemeDefinition> THEMES =
            Map.of();

    private DungeonThemeDefinitionRegistry() {
    }

    public static Optional<DungeonThemeDefinition> get(Identifier id) {
        return Optional.ofNullable(THEMES.get(id));
    }

    public static Map<Identifier, DungeonThemeDefinition> snapshot() {
        return THEMES;
    }

    public static void replace(Map<Identifier, DungeonThemeDefinition> themes) {
        THEMES = immutableOrderedCopy(themes);
    }

    public static void clearForTests() {
        THEMES = Map.of();
    }

    private static Map<Identifier, DungeonThemeDefinition> immutableOrderedCopy(
            Map<Identifier, DungeonThemeDefinition> themes
    ) {
        if (themes == null || themes.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, DungeonThemeDefinition> copy = new LinkedHashMap<>();
        themes.entrySet()
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
