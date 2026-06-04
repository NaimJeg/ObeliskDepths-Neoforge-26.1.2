package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.item.DamageNexusItemApi;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModDataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ObeliskTemperingRoller {

    private static final String TEMPERING_SOURCE =
            ObeliskDepths.MOD_ID + "/tempering";

    private static final String TEMPERING_ENTRY_PREFIX =
            "tempering/";

    private ObeliskTemperingRoller() {
    }

    public static boolean canTemper(
            ItemStack stack,
            boolean replaceExisting
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.getMaxStackSize() != 1) {
            return false;
        }

        if (!stack.isDamageableItem()) {
            return false;
        }

        if (replaceExisting) {
            return true;
        }

        return DamageNexusItemApi.getEntries(stack)
                .stream()
                .noneMatch(ObeliskTemperingRoller::isObeliskTemperingEntry);
    }

    public static boolean hasPendingRoll(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.has(ModDataComponents.PENDING_TEMPER_ROLL.get());
    }

    public static boolean attachPendingRoll(
            ItemStack stack,
            Identifier pool,
            int rolls,
            boolean replaceExisting
    ) {
        Objects.requireNonNull(pool, "pool must not be null");

        if (!canTemper(stack, replaceExisting)) {
            return false;
        }

        stack.set(
                ModDataComponents.PENDING_TEMPER_ROLL.get(),
                new PendingObeliskTemperRoll(
                        pool,
                        Math.max(1, rolls),
                        replaceExisting
                )
        );

        return true;
    }

    public static boolean clearPendingRoll(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (!stack.has(ModDataComponents.PENDING_TEMPER_ROLL.get())) {
            return false;
        }

        stack.remove(ModDataComponents.PENDING_TEMPER_ROLL.get());
        return true;
    }

    public static int resolvePendingRoll(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        PendingObeliskTemperRoll pending =
                stack.get(ModDataComponents.PENDING_TEMPER_ROLL.get());

        if (pending == null) {
            return 0;
        }

        if (!canTemper(stack, pending.replaceExisting())) {
            stack.remove(ModDataComponents.PENDING_TEMPER_ROLL.get());
            return 0;
        }

        if (pending.replaceExisting()) {
            DamageNexusItemApi.removeEntries(
                    stack,
                    ObeliskTemperingRoller::isObeliskTemperingEntry
            );
        }

        int applied = 0;

        for (int i = 0; i < pending.rolls(); i++) {
            DamageEntryDefinition entry =
                    ObeliskTemperingPoolRegistry
                            .roll(pending.pool())
                            .orElse(null);

            if (entry == null) {
                continue;
            }

            boolean added = DamageNexusItemApi.addEntry(
                    stack,
                    entry,
                    TEMPERING_SOURCE
            );

            if (added) {
                applied++;
            }
        }

        stack.remove(ModDataComponents.PENDING_TEMPER_ROLL.get());
        return applied;
    }

    private static boolean isObeliskTemperingEntry(
            DamageEntryDefinition entry
    ) {
        if (entry == null || entry.id() == null) {
            return false;
        }

        Identifier id = entry.id();

        return ObeliskDepths.MOD_ID.equals(id.getNamespace())
                && id.getPath().startsWith(TEMPERING_ENTRY_PREFIX);
    }
}