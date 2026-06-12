package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;

public final class ModItemTagProvider extends IntrinsicHolderTagsProvider<Item> {
    public ModItemTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(
                output,
                Registries.ITEM,
                lookupProvider,
                item -> item.builtInRegistryHolder().key()
        );
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        for (ModBlocks.WoodBlockSet set : ModBlocks.WOOD_BLOCK_SETS) {
            this.tag(ModTags.Items.GREAT_SWAMP_TAXODIUM_LOGS)
                    .add(
                            set.logItem().get(),
                            set.woodItem().get(),
                            set.strippedLogItem().get(),
                            set.strippedWoodItem().get()
                    );
            this.tag(ItemTags.LOGS)
                    .addTag(ModTags.Items.GREAT_SWAMP_TAXODIUM_LOGS);
            this.tag(ItemTags.LOGS_THAT_BURN)
                    .addTag(ModTags.Items.GREAT_SWAMP_TAXODIUM_LOGS);
            this.tag(ItemTags.PLANKS)
                    .add(set.planksItem().get());
            this.tag(ItemTags.WOODEN_STAIRS)
                    .add(set.stairsItem().get());
            this.tag(ItemTags.STAIRS)
                    .add(set.stairsItem().get());
            this.tag(ItemTags.WOODEN_SLABS)
                    .add(set.slabItem().get());
            this.tag(ItemTags.SLABS)
                    .add(set.slabItem().get());
            this.tag(ItemTags.WOODEN_FENCES)
                    .add(set.fenceItem().get());
            this.tag(ItemTags.FENCES)
                    .add(set.fenceItem().get());
            this.tag(ItemTags.FENCE_GATES)
                    .add(set.fenceGateItem().get());
            this.tag(ItemTags.WOODEN_DOORS)
                    .add(set.doorItem().get());
            this.tag(ItemTags.DOORS)
                    .add(set.doorItem().get());
            this.tag(ItemTags.WOODEN_TRAPDOORS)
                    .add(set.trapdoorItem().get());
            this.tag(ItemTags.TRAPDOORS)
                    .add(set.trapdoorItem().get());
//            this.tag(ItemTags.WOODEN_PRESSURE_PLATES)
//                    .add(set.pressurePlateItem().get());
//            this.tag(ItemTags.WOODEN_BUTTONS)
//                    .add(set.buttonItem().get());
//            this.tag(ItemTags.BUTTONS)
//                    .add(set.buttonItem().get());
            this.tag(ItemTags.LEAVES)
                    .add(set.leavesItem().get());
//            this.tag(ItemTags.SIGNS)
//                    .add(set.signItem().get());
//            this.tag(ItemTags.HANGING_SIGNS)
//                    .add(set.hangingSignItem().get());
        }
    }
}
