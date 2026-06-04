package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.GreatSwampVineBlock;
import io.github.naimjeg.obeliskdepths.block.GreatSwampVinePlantBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskSmithingTableBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Function;

public final class ModBlocks {
    private ModBlocks() {
    }

    private static final float OBSIDIAN_HARDNESS = 50.0F;
    private static final float OBSIDIAN_RESISTANCE = 1200.0F;

    private static final float OBSIDIAN_PLUS_HARDNESS = 60.0F;
    private static final float OBSIDIAN_PLUS_RESISTANCE = 1500.0F;

    private static final float UNBREAKABLE_HARDNESS = -1.0F;
    private static final float UNBREAKABLE_RESISTANCE = 3600000.0F;

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ObeliskDepths.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ObeliskDepths.MOD_ID);

    // ---------------------------------------------------------------------
    // Functional blocks
    // ---------------------------------------------------------------------

    public static final DeferredBlock<ObeliskBlock> OBELISK = registerBlock(
            "obelisk",
            3.0F,
            6.0F,
            SoundType.STONE,
            ObeliskBlock::new
    );

    public static final DeferredItem<BlockItem> OBELISK_ITEM =
            registerBlockItem(OBELISK);

    public static final DeferredBlock<ObeliskSmithingTableBlock> OBELISK_SMITHING_TABLE = registerBlock(
            "obelisk_smithing_table",
            2.5F,
            6.0F,
            SoundType.WOOD,
            ObeliskSmithingTableBlock::new
    );

    public static final DeferredItem<BlockItem> OBELISK_SMITHING_TABLE_ITEM =
            registerBlockItem(OBELISK_SMITHING_TABLE);

    // ---------------------------------------------------------------------
    // Dungeon stone set
    // ---------------------------------------------------------------------

    public static final DeferredBlock<Block> DUNGEON_STONE =
            registerStoneBlock("dungeon_stone", OBSIDIAN_HARDNESS, OBSIDIAN_RESISTANCE);

    public static final DeferredItem<BlockItem> DUNGEON_STONE_ITEM =
            registerBlockItem(DUNGEON_STONE);

    public static final DeferredBlock<Block> REINFORCED_DUNGEON_STONE =
            registerStoneBlock("reinforced_dungeon_stone", UNBREAKABLE_HARDNESS, UNBREAKABLE_RESISTANCE);

    public static final DeferredItem<BlockItem> REINFORCED_DUNGEON_STONE_ITEM =
            registerBlockItem(REINFORCED_DUNGEON_STONE);

    public static final DeferredBlock<Block> DUNGEON_BRICKS =
            registerStoneBlock("dungeon_bricks", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);

    public static final DeferredItem<BlockItem> DUNGEON_BRICKS_ITEM =
            registerBlockItem(DUNGEON_BRICKS);

    public static final DeferredBlock<Block> DUNGEON_TILES =
            registerStoneBlock("dungeon_tiles", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);

    public static final DeferredItem<BlockItem> DUNGEON_TILES_ITEM =
            registerBlockItem(DUNGEON_TILES);

    public static final DeferredBlock<Block> DUNGEON_CRACKED_TILES =
            registerStoneBlock("dungeon_cracked_tiles", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);

    public static final DeferredItem<BlockItem> DUNGEON_CRACKED_TILES_ITEM =
            registerBlockItem(DUNGEON_CRACKED_TILES);

    public static final DeferredBlock<Block> DUNGEON_CRACKED_BRICKS =
            registerStoneBlock("dungeon_cracked_bricks", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);

    public static final DeferredItem<BlockItem> DUNGEON_CRACKED_BRICKS_ITEM =
            registerBlockItem(DUNGEON_CRACKED_BRICKS);

    // ---------------------------------------------------------------------
    // Theme Style Blocks / Biome-Based Natural Blocks
    // ---------------------------------------------------------------------

    public static final DeferredBlock<Block> GREAT_SWAMP_GRASS_BLOCK = registerBlock(
            "great_swamp_grass_block",
            0.5F,
            0.5F,
            SoundType.GRASS,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_GRASS_BLOCK_ITEM =
            registerBlockItem(GREAT_SWAMP_GRASS_BLOCK);

    public static final DeferredBlock<Block> GREAT_SWAMP_COARSE_DIRT = registerBlock(
            "great_swamp_coarse_dirt",
            0.5F,
            0.5F,
            SoundType.GRAVEL,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_COARSE_DIRT_ITEM =
            registerBlockItem(GREAT_SWAMP_COARSE_DIRT);

    public static final DeferredBlock<Block> GREAT_SWAMP_MUD = registerBlock(
            "great_swamp_mud",
            0.5F,
            0.5F,
            SoundType.MUD,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_MUD_ITEM =
            registerBlockItem(GREAT_SWAMP_MUD);

    public static final DeferredBlock<Block> GREAT_SWAMP_DIRT = registerBlock(
            "great_swamp_dirt",
            0.5F,
            0.5F,
            SoundType.GRAVEL,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_WAMP_DIRT_ITEM =
            registerBlockItem(GREAT_SWAMP_DIRT);

    public static final DeferredBlock<Block> GREAT_SWAMP_ROOTED_DIRT = registerBlock(
            "great_swamp_root_dirt",
            0.5F,
            0.5F,
            SoundType.ROOTED_DIRT,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_ROOTED_DIRT_ITEM =
            registerBlockItem(GREAT_SWAMP_ROOTED_DIRT);

    public static final DeferredBlock<GreatSwampVineBlock> GREAT_SWAMP_VINES = BLOCKS.register(
            "great_swamp_vines",
            registryName -> new GreatSwampVineBlock(vineHeadProperties(registryName).lightLevel(state -> 6))
    );

    public static final DeferredBlock<GreatSwampVinePlantBlock> GREAT_SWAMP_VINES_PLANT = BLOCKS.register(
            "great_swamp_vines_plant",
            registryName -> new GreatSwampVinePlantBlock(vineBodyProperties(registryName).lightLevel(state -> 6))
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_VINE_ITEM =
            registerBlockItem(GREAT_SWAMP_VINES);

    // ---------------------------------------------------------------------
    // Dungeon decoration
    // ---------------------------------------------------------------------

    private static BlockBehaviour.Properties vineHeadProperties(
            Identifier registryName
    ) {
        return BlockBehaviour.Properties.of()
                .setId(blockKey(registryName))
                .noCollision()
                .randomTicks()
                .instabreak()
                .sound(SoundType.VINE)
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties vineBodyProperties(
            Identifier registryName
    ) {
        return BlockBehaviour.Properties.of()
                .setId(blockKey(registryName))
                .noCollision()
                .instabreak()
                .sound(SoundType.VINE)
                .pushReaction(PushReaction.DESTROY);
    }

    public static final DeferredBlock<Block> DUNGEON_LAMP = BLOCKS.register(
            "dungeon_lamp",
            blockKey -> new Block(baseProperties(blockKey, 1.5F, 6.0F, SoundType.GLASS)
                    .lightLevel(state -> 12))
    );

    public static final DeferredItem<BlockItem> DUNGEON_LAMP_ITEM =
            registerBlockItem(DUNGEON_LAMP);

    // ---------------------------------------------------------------------
    // Shared lists
    // ---------------------------------------------------------------------

    /**
     * Blocks that can safely use simple cube_all model generation.
     *
     * Do not include DUNGEON_VINE here.
     */
    public static final List<DeferredBlock<? extends Block>> TRIVIAL_CUBE_BLOCKS =
            List.of(
                    OBELISK,
                    DUNGEON_STONE,
                    DUNGEON_BRICKS,
                    DUNGEON_TILES,
                    DUNGEON_CRACKED_TILES,
                    DUNGEON_CRACKED_BRICKS,
                    GREAT_SWAMP_COARSE_DIRT,
                    GREAT_SWAMP_MUD,
                    GREAT_SWAMP_DIRT,
                    GREAT_SWAMP_ROOTED_DIRT,
                    DUNGEON_LAMP
            );

    /**
     * Blocks that should drop themselves by default.
     *
     * DUNGEON_VINE is intentionally excluded for now because vine-like blocks
     * usually need special drop behavior.
     */
    public static final List<DeferredBlock<? extends Block>> SELF_DROPPING_BLOCKS =
            List.of(
                    //OBELISK,
                    OBELISK_SMITHING_TABLE,
                    DUNGEON_STONE,
                    REINFORCED_DUNGEON_STONE,
                    DUNGEON_BRICKS,
                    DUNGEON_TILES,
                    DUNGEON_CRACKED_TILES,
                    DUNGEON_CRACKED_BRICKS,
                    GREAT_SWAMP_GRASS_BLOCK,
                    GREAT_SWAMP_COARSE_DIRT,
                    GREAT_SWAMP_MUD,
                    GREAT_SWAMP_DIRT,
                    GREAT_SWAMP_ROOTED_DIRT,
                    DUNGEON_LAMP
            );

    public static final List<DeferredItem<? extends BlockItem>> BUILDING_BLOCK_ITEMS =
            List.of(
                    OBELISK_ITEM,
                    OBELISK_SMITHING_TABLE_ITEM,
                    DUNGEON_STONE_ITEM,
                    REINFORCED_DUNGEON_STONE_ITEM,
                    DUNGEON_BRICKS_ITEM,
                    DUNGEON_TILES_ITEM,
                    DUNGEON_CRACKED_TILES_ITEM,
                    DUNGEON_CRACKED_BRICKS_ITEM,
                    GREAT_SWAMP_COARSE_DIRT_ITEM,
                    GREAT_SWAMP_GRASS_BLOCK_ITEM,
                    GREAT_SWAMP_MUD_ITEM,
                    GREAT_WAMP_DIRT_ITEM,
                    GREAT_SWAMP_ROOTED_DIRT_ITEM,
                    GREAT_SWAMP_VINE_ITEM,
                    DUNGEON_LAMP_ITEM
            );

    private static DeferredBlock<Block> registerStoneBlock(
            String name,
            float destroyTime,
            float explosionResistance
    ) {
        return BLOCKS.register(
                name,
                blockKey -> new Block(stoneProperties(
                        blockKey,
                        destroyTime,
                        explosionResistance
                ))
        );
    }

    private static BlockBehaviour.Properties stoneProperties(
            Identifier registryName,
            float destroyTime,
            float explosionResistance
    ) {
        return baseProperties(
                registryName,
                destroyTime,
                explosionResistance,
                SoundType.STONE
        ).requiresCorrectToolForDrops();
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            float destroyTime,
            float explosionResistance,
            SoundType soundType,
            Function<BlockBehaviour.Properties, T> factory
    ) {
        return BLOCKS.register(
                name,
                blockKey -> factory.apply(baseProperties(
                        blockKey,
                        destroyTime,
                        explosionResistance,
                        soundType
                ))
        );
    }

    private static <T extends Block> DeferredItem<BlockItem> registerBlockItem(
            DeferredBlock<T> block
    ) {
        return ITEMS.registerSimpleBlockItem(block);
    }

    private static BlockBehaviour.Properties baseProperties(
            Identifier registryName,
            float destroyTime,
            float explosionResistance,
            SoundType soundType
    ) {
        return BlockBehaviour.Properties.of()
                .setId(blockKey(registryName))
                .destroyTime(destroyTime)
                .explosionResistance(explosionResistance)
                .sound(soundType);
    }

    private static ResourceKey<Block> blockKey(Identifier registryName) {
        return ResourceKey.create(Registries.BLOCK, registryName);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}