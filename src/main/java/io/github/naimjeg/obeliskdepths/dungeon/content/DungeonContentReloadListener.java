package io.github.naimjeg.obeliskdepths.dungeon.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.template.DungeonTemplateResourceValidator;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinitionValidator;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads authored room, corridor, and theme definitions in one pass so
 * cross-reference validation is atomic. Broken datapacks leave the previous
 * valid snapshot in place.
 */
public final class DungeonContentReloadListener
        implements PreparableReloadListener {
    private static final FileToIdConverter ROOM_LISTER =
            FileToIdConverter.json("dungeon_room");
    private static final FileToIdConverter CORRIDOR_LISTER =
            FileToIdConverter.json("dungeon_corridor");
    private static final FileToIdConverter THEME_LISTER =
            FileToIdConverter.json("dungeon_theme");

    @Override
    public CompletableFuture<Void> reload(
            SharedState sharedState,
            Executor backgroundExecutor,
            PreparationBarrier barrier,
            Executor gameExecutor
    ) {
        return CompletableFuture
                .supplyAsync(
                        () -> load(sharedState.resourceManager()),
                        backgroundExecutor
                )
                .thenCompose(barrier::wait)
                .thenAcceptAsync(DungeonContentReloadListener::install,
                        gameExecutor);
    }

    public static boolean validateAndInstall(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes
    ) {
        return validateAndInstall(rooms, corridors, themes, List.of());
    }

    static boolean validateAndInstall(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes,
            List<String> candidateErrors
    ) {
        LoadedContent content = validate(
                rooms,
                corridors,
                themes,
                candidateErrors
        );

        if (!content.errors().isEmpty()) {
            return false;
        }

        install(content);
        return true;
    }

    private static LoadedContent load(ResourceManager manager) {
        ParseResult<DungeonRoomDefinition> rooms = parseDefinitions(
                manager,
                ROOM_LISTER,
                DungeonRoomDefinition.CODEC,
                "dungeon room"
        );
        ParseResult<DungeonCorridorDefinition> corridors = parseDefinitions(
                manager,
                CORRIDOR_LISTER,
                DungeonCorridorDefinition.CODEC,
                "dungeon corridor"
        );
        ParseResult<DungeonThemeDefinition> themes = parseDefinitions(
                manager,
                THEME_LISTER,
                DungeonThemeDefinition.CODEC,
                "dungeon theme"
        );

        List<String> errors = new ArrayList<>();
        errors.addAll(rooms.errors());
        errors.addAll(corridors.errors());
        errors.addAll(themes.errors());

        LoadedContent validated = validate(
                rooms.values(),
                corridors.values(),
                themes.values(),
                errors
        );

        if (!validated.errors().isEmpty()) {
            return validated;
        }

        List<String> templateErrors = new ArrayList<>();
        templateErrors.addAll(DungeonTemplateResourceValidator.validateRooms(
                validated.rooms(),
                manager
        ));
        templateErrors.addAll(DungeonTemplateResourceValidator.validateCorridors(
                validated.corridors(),
                manager
        ));
        templateErrors.addAll(
                DungeonTemplateResourceValidator
                        .validateAllSuppliedTemplatesReferenced(
                                validated.rooms(),
                                validated.corridors()
                        )
        );

        if (templateErrors.isEmpty()) {
            return validated;
        }

        errors = new ArrayList<>(validated.errors());
        errors.addAll(templateErrors);
        return new LoadedContent(
                validated.rooms(),
                validated.corridors(),
                validated.themes(),
                List.copyOf(errors)
        );
    }

    private static <T> ParseResult<T> parseDefinitions(
            ResourceManager manager,
            FileToIdConverter converter,
            Codec<T> codec,
            String typeName
    ) {
        Map<Identifier, T> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Identifier, Resource> entry
                : converter.listMatchingResources(manager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier definitionId = converter.fileToId(fileId);

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                List<String> parseErrors = new ArrayList<>();
                T parsed = codec.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(parseErrors::add)
                        .orElse(null);

                if (parsed == null) {
                    if (parseErrors.isEmpty()) {
                        parseErrors.add("unknown codec error");
                    }

                    for (String error : parseErrors) {
                        errors.add("failed to parse "
                                + typeName
                                + " "
                                + definitionId
                                + ": "
                                + error);
                    }
                    continue;
                }

                result.put(definitionId, parsed);
            } catch (Exception exception) {
                errors.add("failed to load "
                        + typeName
                        + " "
                        + definitionId
                        + ": "
                        + exception.getMessage());
            }
        }

        return new ParseResult<>(orderedCopy(result), List.copyOf(errors));
    }

    private static LoadedContent validate(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes,
            List<String> existingErrors
    ) {
        Map<Identifier, DungeonRoomDefinition> roomCopy = orderedCopy(rooms);
        Map<Identifier, DungeonCorridorDefinition> corridorCopy =
                orderedCopy(corridors);
        Map<Identifier, DungeonThemeDefinition> themeCopy = orderedCopy(themes);
        List<String> errors = new ArrayList<>(existingErrors);

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry
                : roomCopy.entrySet()) {
            for (String error : DungeonRoomDefinitionValidator.validate(
                    entry.getKey(),
                    entry.getValue()
            )) {
                errors.add("room " + entry.getKey() + ": " + error);
            }
        }

        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry
                : corridorCopy.entrySet()) {
            for (String error : DungeonCorridorDefinitionValidator.validate(
                    entry.getKey(),
                    entry.getValue()
            )) {
                errors.add("corridor " + entry.getKey() + ": " + error);
            }
        }

        for (Map.Entry<Identifier, DungeonThemeDefinition> entry
                : themeCopy.entrySet()) {
            for (String error : DungeonThemeDefinitionValidator.validate(
                    entry.getKey(),
                    entry.getValue(),
                    roomCopy,
                    corridorCopy
            )) {
                errors.add("theme " + entry.getKey() + ": " + error);
            }
        }

        return new LoadedContent(
                roomCopy,
                corridorCopy,
                themeCopy,
                List.copyOf(errors)
        );
    }

    private static void install(LoadedContent content) {
        if (!content.errors().isEmpty()) {
            for (String error : content.errors()) {
                ObeliskDepths.LOGGER.error(
                        "Skipping dungeon content reload: {}",
                        error
                );
            }
            return;
        }

        DungeonRoomDefinitionRegistry.replace(content.rooms());
        DungeonCorridorDefinitionRegistry.replace(content.corridors());
        DungeonThemeDefinitionRegistry.replace(content.themes());
        ObeliskDepths.LOGGER.info(
                "Loaded {} dungeon rooms, {} dungeon corridors, and {} dungeon themes",
                content.rooms().size(),
                content.corridors().size(),
                content.themes().size()
        );
    }

    private static <T> Map<Identifier, T> orderedCopy(Map<Identifier, T> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, T> result = new LinkedHashMap<>();
        input.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Identifier::toString)
                ))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    private record ParseResult<T>(
            Map<Identifier, T> values,
            List<String> errors
    ) {
    }

    private record LoadedContent(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes,
            List<String> errors
    ) {
    }
}
