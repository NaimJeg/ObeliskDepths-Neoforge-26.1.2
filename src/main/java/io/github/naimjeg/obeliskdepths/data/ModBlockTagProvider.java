package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

public final class ModBlockTagProvider extends IntrinsicHolderTagsProvider<Block> {
    public ModBlockTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(
                output,
                Registries.BLOCK,
                lookupProvider,
                block -> block.builtInRegistryHolder().key()
        );
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        addDungeonStoneToolTags();
        addVineTags();
    }

    private void addDungeonStoneToolTags() {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(
                        ModBlocks.DUNGEON_STONE.get(),
                        ModBlocks.REINFORCED_DUNGEON_STONE.get(),
                        ModBlocks.DUNGEON_BRICKS.get(),
                        ModBlocks.DUNGEON_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_BRICKS.get()
                );

        this.tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(
                        ModBlocks.DUNGEON_STONE.get(),
                        ModBlocks.REINFORCED_DUNGEON_STONE.get(),
                        ModBlocks.DUNGEON_BRICKS.get(),
                        ModBlocks.DUNGEON_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_BRICKS.get()
                );
    }

    private void addVineTags() {
        this.tag(BlockTags.CLIMBABLE)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );

        this.tag(BlockTags.FALL_DAMAGE_RESETTING)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );

        this.tag(BlockTags.CAN_GLIDE_THROUGH)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );

        this.tag(BlockTags.SWORD_EFFICIENT)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );
    }
}