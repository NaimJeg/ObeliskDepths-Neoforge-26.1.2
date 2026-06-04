package io.github.naimjeg.obeliskdepths.event;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityData;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityTracker;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidService;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonRaidMobEvents {
    private DungeonRaidMobEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return;
        }

        Optional<DungeonEntityData> data =
                DungeonEntityTracker.get(event.getEntity());

        if (data.isEmpty()) {
            return;
        }

        DungeonEntityData value = data.get();

        value.raidId().ifPresent(raidId ->
                DungeonRaidService.markRaidMobKilled(level, raidId)
        );

        value.instanceId().ifPresent(instanceId ->
                DungeonSessionManager.onRegisteredDungeonMobKilled(
                        level,
                        instanceId,
                        event.getEntity().getUUID()
                )
        );
    }
}
