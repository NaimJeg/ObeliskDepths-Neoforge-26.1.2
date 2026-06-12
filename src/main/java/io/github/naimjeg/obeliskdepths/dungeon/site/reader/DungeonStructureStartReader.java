package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.registry.ModStructures;
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
        ChunkPos startChunk = key.toChunkPos();

        ChunkAccess chunk = level.getChunk(
                startChunk.x(),
                startChunk.z(),
                ChunkStatus.STRUCTURE_STARTS,
                false
        );

        if (chunk == null) {
            return Optional.empty();
        }

        Registry<Structure> structureRegistry =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        Structure structure = structureRegistry.getValue(
                ModStructures.DEPTHS_SITE.identifier()
        );

        if (structure == null) {
            ObeliskDepths.LOGGER.error(
                    "Missing registered dungeon structure {} in level {}",
                    ModStructures.DEPTHS_SITE.identifier(),
                    level.dimension().identifier()
            );
            return Optional.empty();
        }

        StructureStart start = level.structureManager()
                .getStartForStructure(
                        SectionPos.bottomOf(chunk),
                        structure,
                        chunk
                );

        if (start == null) {
            return Optional.empty();
        }

        if (!start.isValid()) {
            ObeliskDepths.LOGGER.debug(
                    "[OD locator] invalid structure start structure={} chunk={} generated={}",
                    ModStructures.DEPTHS_SITE.identifier(),
                    startChunk,
                    false
            );
            return Optional.empty();
        }

        return Optional.of(start);
    }
}
