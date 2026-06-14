package io.github.naimjeg.obeliskdepths.dungeon.corridor;

import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datagen-only built-in corridor definitions. Runtime content loading reads the
 * generated JSON instead of depending on these factories.
 */
public final class BuiltinDungeonCorridorDefinitions {
    private static final DungeonRoomFootprint SINGLE_CELL =
            DungeonRoomFootprint.fromLayers(List.of(List.of("#")));
    private static final List<DungeonRoomRotation> ALL_ROTATIONS =
            List.of(
                    DungeonRoomRotation.NONE,
                    DungeonRoomRotation.CLOCKWISE_90,
                    DungeonRoomRotation.CLOCKWISE_180,
                    DungeonRoomRotation.COUNTERCLOCKWISE_90
            );

    private BuiltinDungeonCorridorDefinitions() {
    }

    public static Map<Identifier, DungeonCorridorDefinition> all() {
        Map<Identifier, DungeonCorridorDefinition> corridors =
                new LinkedHashMap<>();
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_01,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_01,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_02,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_02,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_03,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_03,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_04,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_04,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_05,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_05,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_06,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_06,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_07,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_07,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_08,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_08,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_09,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_09,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_10,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_10,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_CORNER_01,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_CORNER_01,
                corner());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_CORNER_02,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_CORNER_02,
                corner());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_TEE_01,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_TEE_01,
                tee());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_TEE_02,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_TEE_02,
                tee());
        return Map.copyOf(corridors);
    }

    private static void add(
            Map<Identifier, DungeonCorridorDefinition> definitions,
            Identifier id,
            Identifier template,
            CorridorShape shape
    ) {
        definitions.put(id, new DungeonCorridorDefinition(
                template,
                shape.type(),
                SINGLE_CELL,
                shape.ports(),
                ALL_ROTATIONS,
                false,
                Optional.empty()
        ));
    }

    private static CorridorShape straight() {
        return new CorridorShape(
                DungeonConnectorShapeType.STRAIGHT,
                List.of(
                        port("west", new BlockPos(0, 1, 2),
                                DungeonConnectorSide.WEST),
                        port("east", new BlockPos(7, 1, 2),
                                DungeonConnectorSide.EAST)
                )
        );
    }

    private static CorridorShape corner() {
        return new CorridorShape(
                DungeonConnectorShapeType.CORNER,
                List.of(
                        port("south", new BlockPos(2, 1, 7),
                                DungeonConnectorSide.SOUTH),
                        port("east", new BlockPos(7, 1, 2),
                                DungeonConnectorSide.EAST)
                )
        );
    }

    private static CorridorShape tee() {
        return new CorridorShape(
                DungeonConnectorShapeType.T,
                List.of(
                        port("north", new BlockPos(2, 1, 0),
                                DungeonConnectorSide.NORTH),
                        port("south", new BlockPos(2, 1, 7),
                                DungeonConnectorSide.SOUTH),
                        port("west", new BlockPos(0, 1, 2),
                                DungeonConnectorSide.WEST)
                )
        );
    }

    private static RoomConnectorDefinition port(
            String id,
            BlockPos openingMin,
            DungeonConnectorSide side
    ) {
        return new RoomConnectorDefinition(
                id,
                new DungeonCellPos(0, 0, 0),
                openingMin,
                side,
                BuiltinDungeonRoomDefinitions.BASIC_FLOOR_PASSAGE_CONNECTOR,
                4,
                4,
                true
        );
    }

    private record CorridorShape(
            DungeonConnectorShapeType type,
            List<RoomConnectorDefinition> ports
    ) {
    }
}
