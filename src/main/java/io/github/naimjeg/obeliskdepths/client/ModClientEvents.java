package io.github.naimjeg.obeliskdepths.client;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.client.screen.ObeliskPortalScreen;
import io.github.naimjeg.obeliskdepths.client.screen.ObeliskTemperingScreen;
import io.github.naimjeg.obeliskdepths.registry.ModEntityTypes;
import io.github.naimjeg.obeliskdepths.registry.ModMenuTypes;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(
        modid = ObeliskDepths.MOD_ID,
        value = Dist.CLIENT
)
public final class ModClientEvents {
    private ModClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(
                ModMenuTypes.OBELISK_TEMPERING.get(),
                ObeliskTemperingScreen::new
        );

        event.register(
                ModMenuTypes.OBELISK_PORTAL.get(),
                ObeliskPortalScreen::new
        );
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.DUNGEON_PORTAL.get(),
                NoopRenderer::new
        );
    }
}
