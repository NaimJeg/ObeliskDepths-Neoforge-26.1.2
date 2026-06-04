package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Optional;

public final class DungeonStructureStartReader {
    private DungeonStructureStartReader() {
    }

    public static Optional<StructureStart> read(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        return read(level, key, false);
    }

    public static Optional<StructureStart> readOrGenerate(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        return read(level, key, true);
    }

    private static Optional<StructureStart> read(
            ServerLevel level,
            DungeonSiteKey key,
            boolean generateStructureStarts
    ) {
        ChunkPos startChunk = key.toChunkPos();

        ChunkAccess chunk = level.getChunk(
                startChunk.x(),
                startChunk.z(),
                ChunkStatus.STRUCTURE_STARTS,
                generateStructureStarts
        );

        if (chunk == null) {
            return Optional.empty();
        }

        Registry<Structure> structureRegistry =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        Structure structure = structureRegistry.getValue(
                Identifier.fromNamespaceAndPath(
                        ObeliskDepths.MOD_ID,
                        "depths_site"
                )
        );

        if (structure == null) {
            return Optional.empty();
        }

        StructureStart start = level.structureManager()
                .getStartForStructure(
                        SectionPos.bottomOf(chunk),
                        structure,
                        chunk
                );

        if (start == null || !start.isValid()) {
            return Optional.empty();
        }

        return Optional.of(start);
    }
}