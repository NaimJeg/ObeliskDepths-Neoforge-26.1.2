package io.github.naimjeg.obeliskdepths.dungeon.template;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class BuiltinDungeonTemplates {
    public static final Identifier BASIC_ROOM_OPEN_PAVILION_01 =
            id("dungeon/basic/room/open_pavilion_01");
    public static final Identifier BASIC_ROOM_OBELISK_SANCTUM_01 =
            id("dungeon/basic/room/obelisk_sanctum_01");

    public static final Identifier BASIC_CORRIDOR_STRAIGHT_01 =
            corridor("straight_01");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_02 =
            corridor("straight_02");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_03 =
            corridor("straight_03");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_04 =
            corridor("straight_04");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_05 =
            corridor("straight_05");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_06 =
            corridor("straight_06");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_07 =
            corridor("straight_07");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_08 =
            corridor("straight_08");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_09 =
            corridor("straight_09");
    public static final Identifier BASIC_CORRIDOR_STRAIGHT_10 =
            corridor("straight_10");
    public static final Identifier BASIC_CORRIDOR_CORNER_01 =
            corridor("corner_01");
    public static final Identifier BASIC_CORRIDOR_CORNER_02 =
            corridor("corner_02");
    public static final Identifier BASIC_CORRIDOR_TEE_01 =
            corridor("tee_01");
    public static final Identifier BASIC_CORRIDOR_TEE_02 =
            corridor("tee_02");

    public static final List<Identifier> ALL_SUPPLIED_TEMPLATES = List.of(
            BASIC_ROOM_OPEN_PAVILION_01,
            BASIC_ROOM_OBELISK_SANCTUM_01,
            BASIC_CORRIDOR_STRAIGHT_01,
            BASIC_CORRIDOR_STRAIGHT_02,
            BASIC_CORRIDOR_STRAIGHT_03,
            BASIC_CORRIDOR_STRAIGHT_04,
            BASIC_CORRIDOR_STRAIGHT_05,
            BASIC_CORRIDOR_STRAIGHT_06,
            BASIC_CORRIDOR_STRAIGHT_07,
            BASIC_CORRIDOR_STRAIGHT_08,
            BASIC_CORRIDOR_STRAIGHT_09,
            BASIC_CORRIDOR_STRAIGHT_10,
            BASIC_CORRIDOR_CORNER_01,
            BASIC_CORRIDOR_CORNER_02,
            BASIC_CORRIDOR_TEE_01,
            BASIC_CORRIDOR_TEE_02
    );

    private BuiltinDungeonTemplates() {
    }

    private static Identifier corridor(String name) {
        return id("dungeon/basic/corridor/" + name);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
