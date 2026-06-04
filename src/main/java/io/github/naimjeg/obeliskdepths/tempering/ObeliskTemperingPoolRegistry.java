package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ObeliskTemperingPoolRegistry {

    private static final Map<Identifier, List<WeightedEntry>> POOLS =
            new ConcurrentHashMap<>();

    private ObeliskTemperingPoolRegistry() {
    }

    public static void clear() {
        POOLS.clear();
    }

    public static void register(
            Identifier poolId,
            List<WeightedEntry> entries
    ) {
        if (poolId == null) {
            throw new IllegalArgumentException("poolId must not be null");
        }

        if (entries == null || entries.isEmpty()) {
            POOLS.remove(poolId);
            return;
        }

        List<WeightedEntry> normalized = new ArrayList<>();

        for (WeightedEntry entry : entries) {
            if (entry == null || entry.entry() == null) {
                continue;
            }

            if (entry.weight() <= 0) {
                continue;
            }

            normalized.add(entry);
        }

        if (normalized.isEmpty()) {
            POOLS.remove(poolId);
            return;
        }

        POOLS.put(poolId, List.copyOf(normalized));
    }

    public static Optional<DamageEntryDefinition> roll(Identifier poolId) {
        return roll(poolId, RandomSource.create());
    }

    public static List<WeightedEntry> entries(Identifier poolId) {
        if (poolId == null) {
            return List.of();
        }

        return POOLS.getOrDefault(poolId, List.of());
    }

    public static int previewHash(Identifier poolId) {
        if (poolId == null) {
            return 0;
        }

        int hash = poolId.toString().hashCode();
        return hash == 0 ? 1 : hash;
    }

    public static Optional<Identifier> findPoolByPreviewHash(int hash) {
        if (hash == 0) {
            return Optional.empty();
        }

        for (Identifier poolId : POOLS.keySet()) {
            if (previewHash(poolId) == hash) {
                return Optional.of(poolId);
            }
        }

        return Optional.empty();
    }

    public static Optional<DamageEntryDefinition> roll(
            Identifier poolId,
            RandomSource random
    ) {
        if (poolId == null || random == null) {
            return Optional.empty();
        }

        List<WeightedEntry> entries = POOLS.get(poolId);

        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = 0;

        for (WeightedEntry entry : entries) {
            totalWeight += entry.weight();
        }

        if (totalWeight <= 0) {
            return Optional.empty();
        }

        int selected = random.nextInt(totalWeight);

        for (WeightedEntry entry : entries) {
            selected -= entry.weight();

            if (selected < 0) {
                return Optional.of(entry.entry());
            }
        }

        return Optional.empty();
    }

    public record WeightedEntry(
            DamageEntryDefinition entry,
            int weight
    ) {
        public WeightedEntry {
            if (weight < 0) {
                weight = 0;
            }
        }
    }
}
