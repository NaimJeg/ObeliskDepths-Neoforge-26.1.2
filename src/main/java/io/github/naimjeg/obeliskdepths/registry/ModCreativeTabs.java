package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ObeliskDepths.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDING_BLOCKS =
            CREATIVE_MODE_TABS.register(
                    "building_blocks",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup." + ObeliskDepths.MOD_ID + ".building_blocks"))
                            .icon(() -> new ItemStack(ModBlocks.OBELISK_ITEM.get()))
                            .displayItems((parameters, output) ->
                                    acceptBlockItems(output, ModBlocks.BUILDING_BLOCK_ITEMS)
                            )
                            .build()
            );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OBELISK_ITEMS =
            CREATIVE_MODE_TABS.register(
                    "obelisk_items",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup." + ObeliskDepths.MOD_ID + ".obelisk_items"))
                            .icon(() -> new ItemStack(ModItems.TEMPERING_SMITHING_TEMPLATE.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.TEMPERING_SMITHING_TEMPLATE.get());
                                output.accept(TemperingTemplateItems.createTemplate(1, 1.00f));
                                output.accept(TemperingTemplateItems.createTemplate(2, 1.00f));
                            })
                            .build()
            );

    private static void acceptBlockItems(
            CreativeModeTab.Output output,
            List<DeferredItem<? extends Item>> items
    ) {
        items.forEach(item -> output.accept(item.get()));
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
