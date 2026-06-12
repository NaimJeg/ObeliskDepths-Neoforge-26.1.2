package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    private ModTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> GREAT_SWAMP_TAXODIUM_LOGS =
                create("great_swamp_taxodium_logs");

        private Blocks() {
        }

        private static TagKey<Block> create(String path) {
            return TagKey.create(
                    Registries.BLOCK,
                    Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path)
            );
        }
    }

    public static final class Items {
        public static final TagKey<Item> GREAT_SWAMP_TAXODIUM_LOGS =
                create("great_swamp_taxodium_logs");

        private Items() {
        }

        private static TagKey<Item> create(String path) {
            return TagKey.create(
                    Registries.ITEM,
                    Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path)
            );
        }
    }
}
