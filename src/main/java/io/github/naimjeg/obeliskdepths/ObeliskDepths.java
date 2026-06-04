package io.github.naimjeg.obeliskdepths;

import com.mojang.logging.LogUtils;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingPoolReloadListener;
import io.github.naimjeg.obeliskdepths.registry.*;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.resource.ListenerKey;
import org.slf4j.Logger;

@Mod(ObeliskDepths.MOD_ID)
public final class ObeliskDepths {

    public static final String MOD_ID = "obeliskdepths";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ListenerKey<ObeliskTemperingPoolReloadListener>
            TEMPERING_POOL_RELOAD_LISTENER =
            ListenerKey.create(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "obelisk_tempering_pools"
            ));

    public ObeliskDepths(
            IEventBus modEventBus,
            ModContainer modContainer
    ) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModRecipeTypes.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModWorldgen.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ObeliskDepthsCommonSetup::bootstrap);
    }

    private void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addRetainedListener(
                TEMPERING_POOL_RELOAD_LISTENER,
                new ObeliskTemperingPoolReloadListener()
        );
    }
}
