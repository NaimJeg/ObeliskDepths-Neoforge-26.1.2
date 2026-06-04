package io.github.naimjeg.obeliskdepths.dungeon.site;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import net.minecraft.world.level.ChunkPos;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/*
 * Stable key for a generated dungeon site.
 *
 * This key identifies the owning/start chunk of the vanilla StructureStart.
 * It is not a runtime instance id.
 * It is not a territory allocation id.
 * It is not a room id.
 *
 * Runtime identity flow:
 *
 *   DungeonSiteKey
 *     -> derived from StructureStart.getChunkPos()
 *     -> converted to DungeonTerritoryId when a runtime instance reserves it
 *
 * Authoritative metadata flow:
 *
 *   DungeonSiteKey
 *     -> DungeonStructureStartReader
 *     -> StructureStart
 *     -> ObeliskDungeonPiece list
 *     -> DungeonSite projection
 *
 * Prototype/debug metadata may also use this key shape, but it must not be
 * treated as proof that terrain or pieces exist.
 */
public record DungeonSiteKey(
        int startChunkX,
        int startChunkZ
) {
    public static final Codec<DungeonSiteKey> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("start_chunk_x").forGetter(DungeonSiteKey::startChunkX),
                    Codec.INT.fieldOf("start_chunk_z").forGetter(DungeonSiteKey::startChunkZ)
            ).apply(instance, DungeonSiteKey::new));

    public static DungeonSiteKey fromStartChunk(ChunkPos chunkPos) {
        return new DungeonSiteKey(chunkPos.x(), chunkPos.z());
    }

    public ChunkPos toChunkPos() {
        return new ChunkPos(this.startChunkX, this.startChunkZ);
    }

    public DungeonTerritoryId toTerritoryId() {
        String stableKey = "obeliskdepths:structure_start/"
                + this.startChunkX
                + "/"
                + this.startChunkZ;

        UUID uuid = UUID.nameUUIDFromBytes(
                stableKey.getBytes(StandardCharsets.UTF_8)
        );

        return new DungeonTerritoryId(uuid);
    }

    @Override
    public String toString() {
        return this.startChunkX + "," + this.startChunkZ;
    }
}