package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.WorldgenDungeonSiteLocator;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

public final class DungeonSiteDebugCommands {
    private DungeonSiteDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> site() {
        return Commands.literal("site")
                .then(Commands.literal("key-here")
                        .executes(context -> keyHere(context.getSource())))
                .then(Commands.literal("nearest")
                        .executes(context -> nearest(context.getSource())))
                .then(Commands.literal("check")
                        .then(Commands.argument("startChunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("startChunkZ", IntegerArgumentType.integer())
                                        .executes(context -> check(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "startChunkX"),
                                                IntegerArgumentType.getInteger(context, "startChunkZ")
                                        )))));
    }

    private static int keyHere(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ChunkPos chunkPos = player.chunkPosition();
        DungeonSiteKey key = DungeonSiteKey.fromStartChunk(chunkPos);

        source.sendSuccess(
                () -> Component.literal(
                        "Worldgen dungeon site key here: "
                                + key
                                + ", territory="
                                + key.toTerritoryId()
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int nearest(CommandSourceStack source) {
        ServerLevel dungeonLevel = dungeonLevel(source);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        BlockPos origin = BlockPos.containing(source.getPosition());

        Optional<ResolvedDungeonSite> resolved =
                WorldgenDungeonSiteLocator.findNearestReservableSite(
                        dungeonLevel,
                        origin,
                        data::isSiteUnreached
                );

        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No authoritative unreached dungeon site found near " + origin + "."
            ));
            return 0;
        }

        sendSiteInfo(source, dungeonLevel, resolved.get());

        return Command.SINGLE_SUCCESS;
    }

    private static int check(
            CommandSourceStack source,
            int startChunkX,
            int startChunkZ
    ) {
        ServerLevel dungeonLevel = dungeonLevel(source);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        DungeonSiteKey key = new DungeonSiteKey(startChunkX, startChunkZ);

        Optional<ResolvedDungeonSite> resolved =
                WorldgenDungeonSiteLocator.readKnownAuthoritativeSite(
                        dungeonLevel,
                        key
                );

        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No authoritative dungeon site projection found for key " + key + "."
            ));
            return 0;
        }

        sendSiteInfo(source, dungeonLevel, resolved.get());

        return Command.SINGLE_SUCCESS;
    }

    private static void sendSiteInfo(
            CommandSourceStack source,
            ServerLevel dungeonLevel,
            ResolvedDungeonSite resolved
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        DungeonSite site = resolved.site();
        DungeonSiteKey key = site.key();

        String metadataSource = resolved.source().name().toLowerCase();

        String usageState = data.siteRecord(key)
                .map(record -> record.status().getSerializedName()
                        + record.activeInstanceId()
                        .map(instanceId -> ", activeInstance=" + instanceId)
                        .orElse(""))
                .orElse("unreached");

        source.sendSuccess(
                () -> Component.literal(
                        "Worldgen dungeon site "
                                + key
                                + ": source="
                                + metadataSource
                                + ", usage="
                                + usageState
                                + ", reserved="
                                + data.isSiteReserved(key)
                                + ", unreached="
                                + data.isSiteUnreached(key)
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  start="
                                + site.startPos()
                                + ", bounds=["
                                + site.bounds().minX()
                                + ", "
                                + site.bounds().minY()
                                + ", "
                                + site.bounds().minZ()
                                + "] -> ["
                                + site.bounds().maxX()
                                + ", "
                                + site.bounds().maxY()
                                + ", "
                                + site.bounds().maxZ()
                                + "]"
                ),
                false
        );

        for (DungeonGeneratedRoom room : site.rooms()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "  room "
                                    + room.id()
                                    + " type="
                                    + room.type().getSerializedName()
                                    + " anchor="
                                    + room.anchorPos()
                                    + " bounds=["
                                    + room.bounds().minX()
                                    + ", "
                                    + room.bounds().minY()
                                    + ", "
                                    + room.bounds().minZ()
                                    + "] -> ["
                                    + room.bounds().maxX()
                                    + ", "
                                    + room.bounds().maxY()
                                    + ", "
                                    + room.bounds().maxZ()
                                    + "]"
                    ),
                    false
            );
        }
    }

    private static ServerLevel dungeonLevel(CommandSourceStack source) {
        return source.getServer().getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
    }
}
