package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;

public final class GeneratedDungeonSiteProjector {
    private GeneratedDungeonSiteProjector() {
    }

    public static DungeonSite project(
            DungeonSiteKey key,
            StructureStart start
    ) {
        BoundingBox box = start.getBoundingBox();

        DungeonBounds bounds = new DungeonBounds(
                box.minX(),
                box.minY(),
                box.minZ(),
                box.maxX(),
                box.maxY(),
                box.maxZ()
        );

        List<DungeonGeneratedRoom> rooms =
                GeneratedDungeonRoomProjector.projectRooms(start);

        BlockPos fallbackStartPos = new BlockPos(
                box.getCenter().getX(),
                box.minY(),
                box.getCenter().getZ()
        );

        BlockPos startPos = rooms.stream()
                .filter(room -> room.type() == DungeonRoomType.START)
                .findFirst()
                .map(DungeonGeneratedRoom::spawnPos)
                .orElse(fallbackStartPos);

        if (rooms.stream().noneMatch(room -> room.contains(startPos))) {
            ObeliskDepths.LOGGER.warn(
                    "Projected dungeon site {} has spawn outside all rooms: spawn={}, bounds={}, rooms={}",
                    key,
                    startPos,
                    bounds,
                    rooms.size()
            );
        }

        return new DungeonSite(
                key,
                bounds,
                startPos,
                rooms
        );
    }
}