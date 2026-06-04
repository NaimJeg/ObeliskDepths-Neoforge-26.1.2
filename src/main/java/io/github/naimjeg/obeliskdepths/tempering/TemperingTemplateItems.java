package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.registry.ModDataComponents;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.world.item.ItemStack;

public final class TemperingTemplateItems {
    private TemperingTemplateItems() {
    }

    public static ItemStack createRewardTemplate(
            int tier,
            float weight
    ) {
        ItemStack stack = new ItemStack(
                ModItems.TEMPERING_SMITHING_TEMPLATE.get()
        );
        stack.set(
                ModDataComponents.TEMPERING_TEMPLATE_DATA.get(),
                new TemperingTemplateData(tier, weight)
        );
        return stack;
    }

    public static boolean hasTemplateData(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.has(ModDataComponents.TEMPERING_TEMPLATE_DATA.get());
    }

    public static TemperingTemplateData getOrDefault(ItemStack stack) {
        TemperingTemplateData data = stack.get(
                ModDataComponents.TEMPERING_TEMPLATE_DATA.get()
        );

        return data != null ? data : new TemperingTemplateData(1, 0.0F);
    }

    public static ItemStack createTemplate(int tier, float weight) {
        ItemStack stack = new ItemStack(ModItems.TEMPERING_SMITHING_TEMPLATE.get());

        stack.set(
                ModDataComponents.TEMPERING_TEMPLATE_DATA.get(),
                new TemperingTemplateData(tier, weight)
        );

        return stack;
    }

}
