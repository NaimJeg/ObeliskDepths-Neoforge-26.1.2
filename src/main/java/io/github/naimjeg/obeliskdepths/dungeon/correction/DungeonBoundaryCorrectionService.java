package io.github.naimjeg.obeliskdepths.dungeon.correction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidPlayers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonBoundaryCorrectionService {
    private DungeonBoundaryCorrectionService() {
    }

    public static void correctDesyncedPlayers(
            ServerLevel dungeonLevel,
            DungeonInstance instance
    ) {
        for (ServerPlayer player : DungeonRaidPlayers.findPhysicallyDesyncedPlayers(
                dungeonLevel,
                instance
        )) {
            ObeliskDepths.LOGGER.warn(
                    "Player {} is bound to dungeon instance {} but is physically outside its territory at {}",
                    player.getGameProfile().name(),
                    instance.id(),
                    player.blockPosition()
            );

            /*
             * Temporary behavior:
             * For now, only log. Do not forcibly teleport yet.
             *
             * Future options:
             * 1. Teleport player back to instance.startPos().
             * 2. Start an out-of-bounds countdown.
             * 3. Return player to saved returnDimension/returnPos.
             * 4. Mark dungeon as failed if no valid participants remain.
             */
        }
    }
}