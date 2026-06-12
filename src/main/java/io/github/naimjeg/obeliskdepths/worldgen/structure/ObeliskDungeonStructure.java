package io.github.naimjeg.obeliskdepths.worldgen.structure;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationProfile;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.PreliminaryDungeonLayoutPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonSiteOverlapGuard;
import io.github.naimjeg.obeliskdepths.worldgen.structure.terrain.DungeonTerrainPieceEmitter;
import io.github.naimjeg.obeliskdepths.worldgen.structure.terrain.DungeonTerrainPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.terrain.DungeonTerrainPlanner;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/*
 * Vanilla worldgen entry point for Obelisk dungeon sites.
 *
 * Current behavior:
 * - lets vanilla StructurePlacement decide start chunks
 * - creates a valid StructureStart with serialized ObeliskDungeonPiece metadata
 * - delegates block placement and piece metadata ownership to ObeliskDungeonPiece
 *
 * Later:
 * - choose Y from dimension terrain/noise
 * - choose theme/layout from seed + start chunk
 * - generate authored room/corridor pieces
 * - expose projection metadata through GeneratedDungeonSiteReader
 */
public final class ObeliskDungeonStructure extends Structure {
    public static final MapCodec<ObeliskDungeonStructure> CODEC =
            Structure.simpleCodec(ObeliskDungeonStructure::new);

    public ObeliskDungeonStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(
            GenerationContext context
    ) {
        ChunkPos chunkPos = context.chunkPos();

        /*
         * Use the center of the structure start chunk.
         *
         * Later:
         * - read placement/config from JSON
         * - use layout generator
         * - avoid unsuitable terrain pockets if this dimension becomes noisy
         */
        BlockPos startAnchor = new BlockPos(
                chunkPos.getMiddleBlockX(),
                DungeonSitePlacement.PROTOTYPE_Y,
                chunkPos.getMiddleBlockZ()
        );

        Holder<Biome> actualBiome = context.chunkGenerator()
                .getBiomeSource()
                .getNoiseBiome(
                        QuartPos.fromBlock(startAnchor.getX()),
                        QuartPos.fromBlock(startAnchor.getY()),
                        QuartPos.fromBlock(startAnchor.getZ()),
                        context.randomState().sampler()
                );

        String actualBiomeId = actualBiome.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("<direct/unregistered>");

        boolean valid = context.validBiome().test(actualBiome);

        if (!valid) {
            ObeliskDepths.LOGGER.warn(
                    "[OD structure] invalid biome candidate chunk={} anchor={} actualBiome={}",
                    chunkPos,
                    startAnchor,
                    actualBiomeId
            );
        }

        DungeonLayoutPlan layout =
                PreliminaryDungeonLayoutPlanner.plan(startAnchor, DungeonLayoutGenerationProfile.SMALL_TEST);
        DungeonTerrainPlan terrainPlan = DungeonTerrainPlanner.build(startAnchor, layout);
        BoundingBox plannedBounds = terrainPlan.outerBounds();

        Optional<ObeliskDungeonSiteOverlapGuard.Rejection> rejection =
                ObeliskDungeonSiteOverlapGuard.findRejection(
                        context.seed(),
                        chunkPos,
                        plannedBounds
                );

        if (rejection.isPresent()) {
            return Optional.empty();
        }

        ObeliskDepths.LOGGER.debug(
                "[OD structure] accepted dungeon candidate chunk={} anchor={} bounds={} rooms={} corridors={} footprintBlocks={}x{} footprintCells={}x{}",
                chunkPos,
                startAnchor,
                plannedBounds,
                layout.nodes().size(),
                terrainPlan.corridors().size(),
                plannedBounds.getXSpan(),
                plannedBounds.getZSpan(),
                (plannedBounds.getXSpan() + DungeonLayoutConstants.CELL_SIZE_X - 1) / DungeonLayoutConstants.CELL_SIZE_X,
                (plannedBounds.getZSpan() + DungeonLayoutConstants.CELL_SIZE_Z - 1) / DungeonLayoutConstants.CELL_SIZE_Z
        );

        return Optional.of(new GenerationStub(
                startAnchor,
                builder -> DungeonTerrainPieceEmitter.emit(builder, terrainPlan)
        ));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.OBELISK_DUNGEON.get();
    }
}
