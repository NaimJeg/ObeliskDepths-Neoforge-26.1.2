package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;

import com.google.gson.JsonArray;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public final class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, ObeliskDepths.MOD_ID);
    }

    @Override
    protected void registerModels(
            BlockModelGenerators blockModels,
            ItemModelGenerators itemModels
    ) {
        ModBlocks.TRIVIAL_CUBE_BLOCKS.forEach(block ->
                blockModels.createTrivialCube(block.get())
        );

        createObeliskSmithingTable(blockModels, ModBlocks.OBELISK_SMITHING_TABLE.get());
        createReinforcedDungeonStoneBlock(blockModels, ModBlocks.REINFORCED_DUNGEON_STONE.get());
        createModGrassBlock(blockModels, ModBlocks.GREAT_SWAMP_GRASS_BLOCK.get());
        createGreatSwampVines(blockModels);

        itemModels.generateFlatItem(
                ModItems.TEMPERING_SMITHING_TEMPLATE.get(),
                ModelTemplates.FLAT_ITEM
        );
    }

    private static void createReinforcedDungeonStoneBlock(
            BlockModelGenerators blockModels,
            Block block
    ) {
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.END, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"));

        Identifier model = ModelTemplates.CUBE_COLUMN.create(
                block,
                mapping,
                blockModels.modelOutput
        );

        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, variant)
        );

        blockModels.registerSimpleItemModel(block, model);
    }

    private static void createModGrassBlock(
            BlockModelGenerators blockModels,
            Block block
    ) {
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(ModBlocks.GREAT_SWAMP_DIRT.get()))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"));

        Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(
                block,
                mapping,
                blockModels.modelOutput
        );

        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, variant)
        );

        blockModels.registerSimpleItemModel(block, model);
    }

    private static void createObeliskSmithingTable(
            BlockModelGenerators blockModels,
            Block block
    ) {
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.DOWN, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_bottom"))
                .put(TextureSlot.EAST, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_side"))
                .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_front"))
                .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_front"))
                .put(TextureSlot.SOUTH, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_front"))
                .put(TextureSlot.UP, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_top"))
                .put(TextureSlot.WEST, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_side"));

        Identifier model = ModelTemplates.CUBE.create(
                block,
                mapping,
                blockModels.modelOutput
        );

        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, variant)
        );

        blockModels.registerSimpleItemModel(block, model);
    }

    private static void createGreatSwampVines(BlockModelGenerators blockModels) {
        blockModels.createGrowingPlant(
                ModBlocks.GREAT_SWAMP_VINES.get(),
                ModBlocks.GREAT_SWAMP_VINES_PLANT.get(),
                BlockModelGenerators.PlantType.NOT_TINTED
        );

        blockModels.registerSimpleFlatItemModel(
                ModBlocks.GREAT_SWAMP_VINES.get(),
                "_plant"
        );
    }

    private static JsonArray jsonArray(Number... values) {
        JsonArray array = new JsonArray();

        for (Number value : values) {
            array.add(value);
        }

        return array;
    }
}