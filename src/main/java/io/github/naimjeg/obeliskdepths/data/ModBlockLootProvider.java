package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;
import java.util.stream.Stream;

public final class ModBlockLootProvider extends BlockLootSubProvider {
    public ModBlockLootProvider(HolderLookup.Provider lookupProvider) {
        super(Set.of(), FeatureFlags.DEFAULT_FLAGS, lookupProvider);
    }

    @Override
    protected void generate() {
        ModBlocks.SELF_DROPPING_BLOCKS.forEach(block ->
                this.dropSelf(block.get())
        );

        this.add(
                ModBlocks.GREAT_SWAMP_VINES.get(),
                block -> this.createShearsOnlyDrop(ModBlocks.GREAT_SWAMP_VINES.get())
        );

        this.add(
                ModBlocks.GREAT_SWAMP_VINES_PLANT.get(),
                block -> this.createShearsOnlyDrop(ModBlocks.GREAT_SWAMP_VINES.get())
        );
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return Stream.concat(
                ModBlocks.SELF_DROPPING_BLOCKS.stream().map(block -> (Block) block.get()),
                Stream.of(ModBlocks.GREAT_SWAMP_VINES.get())
        ).toList();
    }
}