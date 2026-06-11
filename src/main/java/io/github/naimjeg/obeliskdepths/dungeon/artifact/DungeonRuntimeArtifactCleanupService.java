package io.github.naimjeg.obeliskdepths.dungeon.artifact;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import net.minecraft.server.level.ServerLevel;

public final class DungeonRuntimeArtifactCleanupService {
    private DungeonRuntimeArtifactCleanupService() {
    }

    public static void cleanupInstanceArtifacts(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        /*
         * Worldgen-owned blocks are persistent and are not cleanup targets:
         * terrain, room planes/templates, corridors, anchors, and decoration.
         *
         * Runtime artifacts are cleanup targets:
         * reward chests/block entities, dungeon-tagged reward item entities,
         * temporary barriers/magic walls, and future encounter controllers.
         *
         * TODO: Remove reward chest block entities once they carry instance id.
         * TODO: Remove dungeon-tagged reward item entities without deleting
         * normal player-dropped items.
         * TODO: Remove runtime barriers/magic walls when those systems exist.
         */
        ObeliskDepths.LOGGER.debug(
                "Dungeon runtime artifact cleanup placeholder: level={}, instance={}",
                dungeonLevel.dimension().identifier(),
                instanceId
        );
    }
}
