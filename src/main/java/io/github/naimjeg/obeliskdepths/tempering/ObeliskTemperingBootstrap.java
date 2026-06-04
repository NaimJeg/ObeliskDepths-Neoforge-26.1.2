package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ObeliskTemperingBootstrap {

    private ObeliskTemperingBootstrap() {
    }

    public static void bootstrap() {
        ObeliskTemperingPoolRegistry.clear();

        registerBuiltInPools();

        ObeliskDepths.LOGGER.info(
                "Registered built-in Obelisk tempering pools"
        );
    }

    static void registerBuiltInPools() {
        ObeliskTemperingPoolRegistry.register(
                Identifier.fromNamespaceAndPath(
                        ObeliskDepths.MOD_ID,
                        "basic"
                ),
                List.of(
                        new ObeliskTemperingPoolRegistry.WeightedEntry(
                                ObeliskTemperingEntryFactory
                                        .createFireTemperingEntry(),
                                10
                        ),
                        new ObeliskTemperingPoolRegistry.WeightedEntry(
                                ObeliskTemperingEntryFactory
                                        .createCritTemperingEntry(),
                                5
                        )
                )
        );
    }
}
