package io.github.naimjeg.obeliskdepths.dungeon.content;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonCorridor;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonRoom;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorShapeType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * Read-only adapter for future generator integration. It resolves authored
 * room and theme JSON snapshots without mutating registry state or falling back
 * to Java-only definitions.
 */
public final class DungeonContentResolver {
    private DungeonContentResolver() {
    }

    public static Optional<DungeonRoomDefinition> room(Identifier roomId) {
        return DungeonRoomDefinitionRegistry.get(roomId);
    }

    public static Optional<DungeonCorridorDefinition> corridor(
            Identifier corridorId
    ) {
        return DungeonCorridorDefinitionRegistry.get(corridorId);
    }

    public static Optional<DungeonThemeDefinition> theme(Identifier themeId) {
        return DungeonThemeDefinitionRegistry.get(themeId);
    }

    public static List<WeightedDungeonRoom> candidates(
            Identifier themeId,
            DungeonRoomType type
    ) {
        return theme(themeId)
                .filter(DungeonThemeDefinition::enabled)
                .map(theme -> theme.roomsFor(type))
                .orElse(List.of());
    }

    public static List<WeightedDungeonCorridor> corridorCandidates(
            Identifier themeId,
            DungeonConnectorShapeType shape
    ) {
        return theme(themeId)
                .filter(DungeonThemeDefinition::enabled)
                .map(theme -> theme.corridorsFor(shape))
                .orElse(List.of());
    }

    public static Optional<Identifier> selectRoom(
            Identifier themeId,
            DungeonRoomType type,
            RandomSource random
    ) {
        if (random == null) {
            return Optional.empty();
        }

        List<WeightedDungeonRoom> candidates = candidates(themeId, type);
        return selectWeightedRoom(candidates, random);
    }

    public static Optional<Identifier> selectCorridor(
            Identifier themeId,
            DungeonConnectorShapeType shape,
            RandomSource random
    ) {
        if (random == null) {
            return Optional.empty();
        }

        return selectWeightedCorridor(
                corridorCandidates(themeId, shape),
                random
        );
    }

    private static Optional<Identifier> selectWeightedRoom(
            List<WeightedDungeonRoom> candidates,
            RandomSource random
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = 0;

        for (WeightedDungeonRoom candidate : candidates) {
            totalWeight += candidate.weight();
        }

        if (totalWeight <= 0) {
            return Optional.empty();
        }

        int selected = random.nextInt(totalWeight);

        for (WeightedDungeonRoom candidate : candidates) {
            selected -= candidate.weight();

            if (selected < 0) {
                return Optional.of(candidate.room());
            }
        }

        return Optional.empty();
    }

    private static Optional<Identifier> selectWeightedCorridor(
            List<WeightedDungeonCorridor> candidates,
            RandomSource random
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = 0;

        for (WeightedDungeonCorridor candidate : candidates) {
            totalWeight += candidate.weight();
        }

        if (totalWeight <= 0) {
            return Optional.empty();
        }

        int selected = random.nextInt(totalWeight);

        for (WeightedDungeonCorridor candidate : candidates) {
            selected -= candidate.weight();

            if (selected < 0) {
                return Optional.of(candidate.corridor());
            }
        }

        return Optional.empty();
    }
}
