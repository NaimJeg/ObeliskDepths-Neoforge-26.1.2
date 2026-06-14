package io.github.naimjeg.obeliskdepths.dungeon.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridorDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridors;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRooms;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.dungeon.template.DungeonTemplateResourceValidator;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemeDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemes;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinitionRegistry;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonCorridor;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonRoom;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DungeonContentDefinitionTest {
    private static final Identifier TEST_ROOM =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test/room");
    private static final Identifier TEST_THEME =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test/theme");
    private static final Identifier TEST_TEMPLATE =
            Identifier.fromNamespaceAndPath(
                    "obeliskdepths",
                    "dungeon/great_swamp/room/combat/open_pavilion_01"
            );
    private static final Identifier STANDARD_CONNECTOR =
            BuiltinDungeonRoomDefinitions.BASIC_FLOOR_PASSAGE_CONNECTOR;

    private DungeonContentDefinitionTest() {
    }

    public static void main(String[] args) throws Exception {
        testCodecsAndFootprints();
        testPortValidation();
        testBuiltInsAndTheme();
        testTemplateAssets();
        testRegistryAndAtomicInstall();

        DungeonRoomDefinitionRegistry.clearForTests();
        DungeonCorridorDefinitionRegistry.clearForTests();
        DungeonThemeDefinitionRegistry.clearForTests();
    }

    private static void testCodecsAndFootprints() {
        RoomConnectorDefinition connector = port(
                "north",
                new DungeonCellPos(0, 0, 0),
                new BlockPos(2, 1, 0),
                DungeonConnectorSide.NORTH
        );
        DungeonRoomDefinition room = room(
                DungeonRoomType.COMBAT,
                DungeonRoomFootprint.fromLayers(List.of(List.of("#"))),
                List.of(connector)
        );
        DungeonThemeDefinition theme = disabledTheme(
                Map.of(DungeonRoomType.COMBAT,
                        List.of(new WeightedDungeonRoom(TEST_ROOM, 1))),
                Map.of()
        );

        assertEquals(connector, roundTrip(RoomConnectorDefinition.CODEC, connector),
                "connector codec round trip");
        assertEquals(room, roundTrip(DungeonRoomDefinition.CODEC, room),
                "room codec round trip");
        assertEquals(theme, roundTrip(DungeonThemeDefinition.CODEC, theme),
                "theme codec round trip");

        DungeonRoomFootprint legacy = DungeonRoomFootprint.CODEC
                .parse(JsonOps.INSTANCE, legacyFootprintJson(3, 1, 2))
                .getOrThrow();
        assertEquals(6, legacy.occupiedCells().size(),
                "legacy rectangular decode");

        DungeonRoomFootprint mask = DungeonRoomFootprint.fromLayers(List.of(
                List.of("###", "##.", "#..")
        ));
        assertEquals(6, mask.occupiedCells().size(),
                "irregular mask should reserve exactly six cells");
        assertEquals(mask, roundTrip(DungeonRoomFootprint.CODEC, mask),
                "layers footprint round trip");

        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(
                        List.of("##", "#")
                )),
                "unequal row widths should be rejected"
        );
        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(
                        List.of("##"),
                        List.of("##", "##")
                )),
                "unequal layer dimensions should be rejected"
        );
        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(List.of("#x"))),
                "invalid mask characters should be rejected"
        );
        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(List.of(".."))),
                "all-empty masks should be rejected"
        );

        DungeonRoomFootprint rotated =
                mask.rotated(DungeonRoomRotation.CLOCKWISE_90);
        assertEquals(mask.depthCells(), rotated.widthCells(),
                "90-degree rotation swaps width");
        assertEquals(mask.widthCells(), rotated.depthCells(),
                "90-degree rotation swaps depth");
        assertEquals(mask.occupiedCells().size(), rotated.occupiedCells().size(),
                "rotation preserves occupied cell count");
        DungeonRoomFootprint fourTurns = mask
                .rotated(DungeonRoomRotation.CLOCKWISE_90)
                .rotated(DungeonRoomRotation.CLOCKWISE_90)
                .rotated(DungeonRoomRotation.CLOCKWISE_90)
                .rotated(DungeonRoomRotation.CLOCKWISE_90);
        assertEquals(mask, fourTurns, "four clockwise rotations restore shape");

        JsonElement encoded = DungeonRoomFootprint.CODEC
                .encodeStart(JsonOps.INSTANCE, mask)
                .getOrThrow();
        assertTrue(encoded.getAsJsonObject().has("layers"),
                "new footprint encoding should use layers");
        assertTrue(!encoded.getAsJsonObject().has("width_cells"),
                "new footprint encoding should not use legacy rectangle fields");
    }

    private static void testPortValidation() {
        DungeonRoomFootprint footprint = DungeonRoomFootprint.fromLayers(List.of(
                List.of("##", "#.")
        ));

        assertContains(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT, footprint, List.of(port(
                                "outside",
                                new DungeonCellPos(1, 0, 1),
                                new BlockPos(7, 1, 2),
                                DungeonConnectorSide.EAST
                        )))
                ),
                "boundary cell is not occupied",
                "port boundary cell must be occupied"
        );

        assertContains(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT, footprint, List.of(port(
                                "into_cell",
                                new DungeonCellPos(0, 0, 0),
                                new BlockPos(7, 1, 2),
                                DungeonConnectorSide.EAST
                        )))
                ),
                "faces occupied neighbor",
                "port must face unoccupied neighbor"
        );

        assertTrue(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT, footprint, List.of(port(
                                "concave",
                                new DungeonCellPos(0, 0, 1),
                                new BlockPos(15, 1, 8),
                                DungeonConnectorSide.EAST
                        )))
                ).isEmpty(),
                "concave exposed boundary should be accepted"
        );

        assertTrue(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        BuiltinDungeonRoomDefinitions
                                .greatSwampTreasureObeliskSanctum()
                ).isEmpty(),
                "sanctum east opening should validate along Z"
        );

        assertContains(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT,
                                DungeonRoomFootprint.fromLayers(List.of(
                                        List.of("#")
                                )),
                                List.of(port(
                                        "up",
                                        new DungeonCellPos(0, 0, 0),
                                        BlockPos.ZERO,
                                        DungeonConnectorSide.UP
                                )))
                ),
                "unsupported vertical facing",
                "vertical ports should report unsupported generation"
        );
    }

    private static void testBuiltInsAndTheme() {
        Map<Identifier, DungeonRoomDefinition> rooms =
                BuiltinDungeonRoomDefinitions.all();
        Map<Identifier, DungeonCorridorDefinition> corridors =
                BuiltinDungeonCorridorDefinitions.all();
        DungeonThemeDefinition greatSwamp =
                BuiltinDungeonThemeDefinitions.greatSwamp();

        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION),
                "start open pavilion semantic ID should exist");
        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION),
                "combat open pavilion semantic ID should exist");
        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_TREASURE_OBELISK_SANCTUM),
                "treasure obelisk sanctum semantic ID should exist");
        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR),
                "boss altar semantic ID should exist");
        assertEquals(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01,
                rooms.get(BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION)
                        .template(),
                "combat room definition template ID should be physical"
        );
        assertEquals(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_BOSS_ALTAR_01,
                rooms.get(BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR)
                        .template(),
                "boss room definition template ID should be physical"
        );
        assertTrue(greatSwamp.roomsFor(DungeonRoomType.COMBAT)
                        .stream()
                        .anyMatch(room -> room.room().equals(
                                BuiltinDungeonRooms
                                        .GREAT_SWAMP_COMBAT_OPEN_PAVILION)),
                "theme should reference semantic room IDs");
        assertEquals(14, corridors.size(),
                "all supplied corridors should have definitions");
        assertEquals(
                10,
                greatSwamp.corridorsFor(DungeonConnectorShapeType.STRAIGHT)
                        .size(),
                "great swamp should reference all straight corridors"
        );
        assertEquals(
                2,
                greatSwamp.corridorsFor(DungeonConnectorShapeType.CORNER)
                        .size(),
                "great swamp should reference all corner corridors"
        );
        assertEquals(
                2,
                greatSwamp.corridorsFor(DungeonConnectorShapeType.T)
                        .size(),
                "great swamp should reference all tee corridors"
        );

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry
                : rooms.entrySet()) {
            assertTrue(
                    DungeonRoomDefinitionValidator
                            .validate(entry.getKey(), entry.getValue())
                            .isEmpty(),
                    "built-in room should validate: " + entry.getKey()
            );
        }
        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry
                : corridors.entrySet()) {
            assertTrue(
                    DungeonCorridorDefinitionValidator
                            .validate(entry.getKey(), entry.getValue())
                            .isEmpty(),
                    "built-in corridor should validate: " + entry.getKey()
            );
        }
        assertTrue(
                DungeonThemeDefinitionValidator
                        .validate(
                                BuiltinDungeonThemes.GREAT_SWAMP,
                                greatSwamp,
                                rooms,
                                corridors
                        )
                        .isEmpty(),
                "enabled great swamp theme should validate"
        );

        DungeonThemeDefinition enabledIncomplete =
                new DungeonThemeDefinition(
                        Map.of(DungeonRoomType.COMBAT,
                                greatSwamp.roomsFor(DungeonRoomType.COMBAT)),
                        greatSwamp.corridorPools(),
                        Optional.empty(),
                        true
                );
        assertContains(
                DungeonThemeDefinitionValidator.validate(
                        TEST_THEME,
                        enabledIncomplete,
                        rooms,
                        corridors
                ),
                "enabled theme requires at least one start room",
                "enabled incomplete theme should fail validation"
        );

        assertContains(
                DungeonThemeDefinitionValidator.validate(
                        TEST_THEME,
                        disabledTheme(
                                Map.of(DungeonRoomType.START,
                                        List.of(new WeightedDungeonRoom(
                                                BuiltinDungeonRooms
                                                        .GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                                1
                                        ))),
                                Map.of()
                        ),
                        rooms,
                        corridors
                ),
                "but is referenced from start pool",
                "room type mismatch should be reported"
        );

        DungeonRoomDefinitionRegistry.replace(rooms);
        DungeonCorridorDefinitionRegistry.replace(corridors);
        DungeonThemeDefinitionRegistry.replace(BuiltinDungeonThemeDefinitions.all());
        assertTrue(
                DungeonContentResolver
                        .selectRoom(
                                BuiltinDungeonThemes.GREAT_SWAMP,
                                DungeonRoomType.COMBAT,
                                RandomSource.create(42L)
                        )
                        .isPresent(),
                "resolver should select rooms from enabled themes"
        );
        assertTrue(
                DungeonContentResolver
                        .selectCorridor(
                                BuiltinDungeonThemes.GREAT_SWAMP,
                                DungeonConnectorShapeType.STRAIGHT,
                                RandomSource.create(42L)
                        )
                        .isPresent(),
                "resolver should select corridors from enabled themes"
        );

        Identifier sharedTemplate = rooms
                .get(BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION)
                .template();
        DungeonRoomDefinition alternateSemanticRoom = new DungeonRoomDefinition(
                sharedTemplate,
                DungeonRoomType.START,
                rooms.get(BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION)
                        .footprint(),
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of(port(
                        "north",
                        new DungeonCellPos(0, 0, 0),
                        new BlockPos(2, 1, 0),
                        DungeonConnectorSide.NORTH
                )),
                List.of(DungeonRoomRotation.NONE),
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                false,
                false
        );
        assertEquals(sharedTemplate, alternateSemanticRoom.template(),
                "multiple semantic definitions may share one physical template");
    }

    private static void testTemplateAssets() throws Exception {
        Path resources = Path.of("src/main/resources");
        Map<Identifier, DungeonRoomDefinition> rooms =
                BuiltinDungeonRoomDefinitions.all();
        Map<Identifier, DungeonCorridorDefinition> corridors =
                BuiltinDungeonCorridorDefinitions.all();
        Map<Identifier, DungeonTemplateResourceValidator.Size> sizes =
                new LinkedHashMap<>();

        for (Identifier template : BuiltinDungeonTemplates.ALL_SUPPLIED_TEMPLATES) {
            Path path = DungeonTemplateResourceValidator.templatePath(
                    resources,
                    template
            );
            assertTrue(Files.exists(path), "template should exist: " + path);
            sizes.put(template,
                    DungeonTemplateResourceValidator.readTemplateSize(path));
        }

        assertEquals(
                new DungeonTemplateResourceValidator.Size(8, 16, 8),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_START_OPEN_PAVILION_01),
                "start open pavilion NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(8, 16, 8),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01),
                "combat open pavilion NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(32, 40, 32),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_TREASURE_OBELISK_SANCTUM_01),
                "obelisk sanctum NBT size"
        );
        for (Identifier corridor : BuiltinDungeonTemplates.ALL_SUPPLIED_TEMPLATES
                .stream()
                .filter(id -> id.getPath().contains("/corridor/"))
                .toList()) {
            assertEquals(
                    new DungeonTemplateResourceValidator.Size(8, 8, 8),
                    sizes.get(corridor),
                    "corridor NBT size: " + corridor
            );
        }

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry
                : rooms.entrySet()) {
            assertTrue(
                    DungeonTemplateResourceValidator
                            .validateRoom(
                                    entry.getKey(),
                                    entry.getValue(),
                                    sizes.get(entry.getValue().template())
                            )
                            .isEmpty(),
                    "room template should fit definition: " + entry.getKey()
            );
        }
        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry
                : corridors.entrySet()) {
            assertTrue(
                    DungeonTemplateResourceValidator
                            .validateCorridor(
                                    entry.getKey(),
                                    entry.getValue(),
                                    sizes.get(entry.getValue().template())
                            )
                            .isEmpty(),
                    "corridor template should fit definition: " + entry.getKey()
            );
        }
        assertTrue(
                DungeonTemplateResourceValidator
                        .validateAllSuppliedTemplatesReferenced(rooms, corridors)
                        .isEmpty(),
                "all supplied templates should be referenced by content definitions"
        );
    }

    private static void testRegistryAndAtomicInstall() {
        Map<Identifier, DungeonRoomDefinition> rooms =
                BuiltinDungeonRoomDefinitions.all();
        Map<Identifier, DungeonCorridorDefinition> corridors =
                BuiltinDungeonCorridorDefinitions.all();
        Map<Identifier, DungeonThemeDefinition> themes =
                BuiltinDungeonThemeDefinitions.all();

        assertThrows(
                () -> DungeonRoomDefinitionRegistry.snapshot().clear(),
                "room snapshot should be immutable"
        );
        assertThrows(
                () -> DungeonCorridorDefinitionRegistry.snapshot().clear(),
                "corridor snapshot should be immutable"
        );
        assertThrows(
                () -> DungeonThemeDefinitionRegistry.snapshot().clear(),
                "theme snapshot should be immutable"
        );

        boolean installed = DungeonContentReloadListener.validateAndInstall(
                rooms,
                corridors,
                themes
        );
        assertTrue(installed, "valid content should install");

        Map<Identifier, DungeonRoomDefinition> beforeRooms =
                DungeonRoomDefinitionRegistry.snapshot();
        Map<Identifier, DungeonCorridorDefinition> beforeCorridors =
                DungeonCorridorDefinitionRegistry.snapshot();
        Map<Identifier, DungeonThemeDefinition> beforeThemes =
                DungeonThemeDefinitionRegistry.snapshot();
        boolean parseFailed = DungeonContentReloadListener.validateAndInstall(
                Map.of(),
                corridors,
                themes,
                List.of("failed to parse dungeon room obeliskdepths:broken")
        );
        assertTrue(!parseFailed, "parse failure should block installation");
        assertEquals(beforeRooms, DungeonRoomDefinitionRegistry.snapshot(),
                "parse failure should retain room snapshot");
        assertEquals(beforeCorridors, DungeonCorridorDefinitionRegistry.snapshot(),
                "parse failure should retain corridor snapshot");
        assertEquals(beforeThemes, DungeonThemeDefinitionRegistry.snapshot(),
                "parse failure should retain theme snapshot");

        boolean invalidTheme = DungeonContentReloadListener.validateAndInstall(
                rooms,
                Map.of(),
                themes
        );
        assertTrue(!invalidTheme,
                "invalid theme cross-reference should block installation");
        assertEquals(beforeRooms, DungeonRoomDefinitionRegistry.snapshot(),
                "invalid theme should retain room snapshot");
        assertEquals(beforeCorridors, DungeonCorridorDefinitionRegistry.snapshot(),
                "invalid theme should retain corridor snapshot");
        assertEquals(beforeThemes, DungeonThemeDefinitionRegistry.snapshot(),
                "invalid theme should retain theme snapshot");
    }

    private static RoomConnectorDefinition port(
            String id,
            DungeonCellPos boundaryCell,
            BlockPos openingMin,
            DungeonConnectorSide facing
    ) {
        return new RoomConnectorDefinition(
                id,
                boundaryCell,
                openingMin,
                facing,
                STANDARD_CONNECTOR,
                4,
                4,
                true
        );
    }

    private static DungeonRoomDefinition room(
            DungeonRoomType type,
            DungeonRoomFootprint footprint,
            List<RoomConnectorDefinition> ports
    ) {
        return new DungeonRoomDefinition(
                TEST_TEMPLATE,
                type,
                footprint,
                BlockPos.ZERO,
                BlockPos.ZERO,
                ports,
                List.of(DungeonRoomRotation.NONE),
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                true,
                false
        );
    }

    private static DungeonThemeDefinition disabledTheme(
            Map<DungeonRoomType, List<WeightedDungeonRoom>> roomPools,
            Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                    corridorPools
    ) {
        EnumMap<DungeonRoomType, List<WeightedDungeonRoom>> roomCopy =
                new EnumMap<>(DungeonRoomType.class);
        roomCopy.putAll(roomPools);
        EnumMap<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                corridorCopy = new EnumMap<>(DungeonConnectorShapeType.class);
        corridorCopy.putAll(corridorPools);
        return new DungeonThemeDefinition(
                roomCopy,
                corridorCopy,
                Optional.empty(),
                false
        );
    }

    private static JsonObject legacyFootprintJson(
            int width,
            int height,
            int depth
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("width_cells", width);
        json.addProperty("height_cells", height);
        json.addProperty("depth_cells", depth);
        return json;
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value)
                .getOrThrow();
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static void assertContains(
            List<String> errors,
            String expected,
            String message
    ) {
        for (String error : errors) {
            if (error.contains(expected)) {
                return;
            }
        }

        throw new AssertionError(
                message + " expected substring=" + expected + " errors=" + errors
        );
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException expected) {
            return;
        }

        throw new AssertionError(message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T> void assertEquals(
            T expected,
            T actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=" + expected + " actual=" + actual
            );
        }
    }
}
