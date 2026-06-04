package io.github.naimjeg.obeliskdepths.dungeon.site;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

/*
 * Runtime projection of an already-generated dungeon structure.
 *
 * This is read-only worldgen metadata exposed to runtime.
 * It does not create blocks.
 * It does not decide layout.
 * It does not own room progression.
 */
public record DungeonSite(
        DungeonSiteKey key,
        DungeonBounds bounds,
        BlockPos startPos,
        List<DungeonGeneratedRoom> rooms
) {
    public static final Codec<DungeonSite> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonSiteKey.CODEC.fieldOf("key")
                            .forGetter(DungeonSite::key),
                    DungeonBounds.CODEC.fieldOf("bounds")
                            .forGetter(DungeonSite::bounds),
                    BlockPos.CODEC.fieldOf("start_pos")
                            .forGetter(DungeonSite::startPos),
                    DungeonGeneratedRoom.CODEC.listOf()
                            .fieldOf("rooms")
                            .forGetter(DungeonSite::rooms)
            ).apply(instance, DungeonSite::new));

    public DungeonSite {
        rooms = List.copyOf(rooms);
    }

    public Optional<DungeonGeneratedRoom> room(DungeonRoomId id) {
        return this.rooms.stream()
                .filter(room -> room.id().equals(id))
                .findFirst();
    }

    public List<DungeonGeneratedRoom> roomsOfType(DungeonRoomType type) {
        return this.rooms.stream()
                .filter(room -> room.type() == type)
                .toList();
    }

    public Optional<DungeonGeneratedRoom> startRoom() {
        return this.roomsOfType(DungeonRoomType.START)
                .stream()
                .findFirst();
    }

    public Optional<DungeonGeneratedRoom> roomAt(BlockPos pos) {
        return this.rooms.stream()
                .filter(room -> room.contains(pos))
                .findFirst();
    }
}
