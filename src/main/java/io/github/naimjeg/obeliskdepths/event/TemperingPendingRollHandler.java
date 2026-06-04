package io.github.naimjeg.obeliskdepths.event;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingRoller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class TemperingPendingRollHandler {

    private TemperingPendingRollHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        Inventory inventory = event.getEntity().getInventory();
        boolean changed = false;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);

            if (stack.isEmpty()) {
                continue;
            }

            if (!ObeliskTemperingRoller.hasPendingRoll(stack)) {
                continue;
            }

            int applied = ObeliskTemperingRoller.resolvePendingRoll(stack);

            /*
             * Even if applied == 0, resolvePendingRoll may have removed
             * an invalid pending component. So mark changed after detecting
             * that a pending roll existed.
             */
            changed = true;

            if (applied > 0) {
                ObeliskDepths.LOGGER.debug(
                        "Resolved {} Obelisk tempering roll(s) in inventory slot {}",
                        applied,
                        slot
                );
            }
        }

        if (changed) {
            inventory.setChanged();
        }
    }
}