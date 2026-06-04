package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ObeliskDungeonLayoutAlgorithm {
    private static final int ROOM_SPACING = 24;
    private static final int CORRIDOR_WIDTH = 5;

    private ObeliskDungeonLayoutAlgorithm() {
    }

    public static SolvedDungeonLayout solveLinearPrototype(BlockPos startAnchor) {
        List<SolvedDungeonRoom> rooms = List.of(
                solveRoom(new DungeonRoomSpec(
                        "start",
                        ObeliskDungeonPieceRole.START_ROOM,
                        13,
                        7,
                        13
                ), startAnchor),
                solveRoom(new DungeonRoomSpec(
                        "combat_01",
                        ObeliskDungeonPieceRole.COMBAT_ROOM,
                        15,
                        7,
                        15
                ), startAnchor.offset(ROOM_SPACING, 0, 0)),
                solveRoom(new DungeonRoomSpec(
                        "treasure_01",
                        ObeliskDungeonPieceRole.TREASURE_ROOM,
                        11,
                        7,
                        11
                ), startAnchor.offset(ROOM_SPACING * 2, 0, 0)),
                solveRoom(new DungeonRoomSpec(
                        "exit",
                        ObeliskDungeonPieceRole.EXIT_ROOM,
                        11,
                        7,
                        11
                ), startAnchor.offset(ROOM_SPACING * 3, 0, 0))
        );

        SolvedDungeonLayout roomsOnly = new SolvedDungeonLayout(rooms, List.of());
        List<SolvedDungeonCorridor> corridors = new ArrayList<>();

        corridors.add(solveCorridorX(
                roomsOnly,
                new DungeonConnectionSpec(
                        "corridor_start_combat",
                        "start",
                        "combat_01",
                        CORRIDOR_WIDTH
                )
        ));
        corridors.add(solveCorridorX(
                roomsOnly,
                new DungeonConnectionSpec(
                        "corridor_combat_treasure",
                        "combat_01",
                        "treasure_01",
                        CORRIDOR_WIDTH
                )
        ));
        corridors.add(solveCorridorX(
                roomsOnly,
                new DungeonConnectionSpec(
                        "corridor_treasure_exit",
                        "treasure_01",
                        "exit",
                        CORRIDOR_WIDTH
                )
        ));

        return new SolvedDungeonLayout(rooms, corridors);
    }

    private static SolvedDungeonRoom solveRoom(
            DungeonRoomSpec spec,
            BlockPos anchor
    ) {
        int halfX = spec.width() / 2;
        int halfZ = spec.depth() / 2;

        BoundingBox bounds = new BoundingBox(
                anchor.getX() - halfX,
                anchor.getY() - 1,
                anchor.getZ() - halfZ,
                anchor.getX() + halfX,
                anchor.getY() + spec.height() - 2,
                anchor.getZ() + halfZ
        );

        SolvedDungeonRoom room = new SolvedDungeonRoom(spec, anchor, bounds);
        ObeliskDepths.LOGGER.debug(
                "[OD layout] room id={} role={} anchor={} bounds={}",
                spec.id(),
                spec.role(),
                anchor,
                bounds
        );
        return room;
    }

    private static SolvedDungeonCorridor solveCorridorX(
            SolvedDungeonLayout layout,
            DungeonConnectionSpec spec
    ) {
        SolvedDungeonRoom from = layout.room(spec.fromRoomId());
        SolvedDungeonRoom to = layout.room(spec.toRoomId());

        if (from.anchor().getY() != to.anchor().getY()
                || from.anchor().getZ() != to.anchor().getZ()) {
            throw new IllegalStateException(
                    "Only X-axis prototype corridors are supported for now: "
                            + spec.id()
                            + " from="
                            + from.anchor()
                            + " to="
                            + to.anchor()
            );
        }

        SolvedDungeonRoom left = from.bounds().minX() <= to.bounds().minX() ? from : to;
        SolvedDungeonRoom right = left == from ? to : from;

        int minX = left.bounds().maxX() + 1;
        int maxX = right.bounds().minX() - 1;

        if (maxX < minX) {
            throw new IllegalStateException(
                    "Invalid corridor gap for "
                            + spec.id()
                            + ": left="
                            + left.spec().id()
                            + " bounds="
                            + left.bounds()
                            + ", right="
                            + right.spec().id()
                            + " bounds="
                            + right.bounds()
            );
        }

        int centerZ = left.anchor().getZ();
        int halfWidth = spec.width() / 2;
        int minY = left.anchor().getY() - 1;
        int maxY = left.anchor().getY() + 4;

        BoundingBox bounds = new BoundingBox(
                minX,
                minY,
                centerZ - halfWidth,
                maxX,
                maxY,
                centerZ + halfWidth
        );

        BlockPos anchor = new BlockPos(
                (minX + maxX) / 2,
                left.anchor().getY(),
                centerZ
        );

        SolvedDungeonCorridor corridor = new SolvedDungeonCorridor(spec, anchor, bounds);
        ObeliskDepths.LOGGER.debug(
                "[OD layout] corridor id={} from={} to={} anchor={} bounds={}",
                spec.id(),
                spec.fromRoomId(),
                spec.toRoomId(),
                anchor,
                bounds
        );
        return corridor;
    }
}
