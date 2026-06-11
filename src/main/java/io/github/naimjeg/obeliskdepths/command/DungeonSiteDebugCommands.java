package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.debug.DungeonDebugChunkWarmupService;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.WorldgenDungeonSiteLocator;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

public final class DungeonSiteDebugCommands {
    private static final int DEFAULT_WARMUP_RADIUS_CHUNKS = 6;

    private DungeonSiteDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> site() {
        return Commands.literal("site")
                .then(Commands.literal("key-here")
                        .executes(context -> keyHere(context.getSource())))
                .then(Commands.literal("nearest")
                        .executes(context -> nearest(context.getSource())))
                .then(Commands.literal("nearest-generated")
                        .executes(context -> nearest(context.getSource())))
                .then(Commands.literal("warmup-nearest")
                        .executes(context -> warmupNearest(
                                context.getSource(),
                                DEFAULT_WARMUP_RADIUS_CHUNKS
                        ))
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, DungeonDebugChunkWarmupService.MAX_RADIUS_CHUNKS))
                                .executes(context -> warmupNearest(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radiusChunks")
                                ))))
                .then(Commands.literal("enter-nearest")
                        .executes(context -> enterNearest(context.getSource())))
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
        BlockPos origin = originInDungeonLevel(source);

        Optional<ResolvedDungeonSite> resolved =
                findNearestGeneratedReservable(dungeonLevel, origin, data);

        if (resolved.isEmpty()) {
            sendNoGeneratedSiteDiagnostic(source, dungeonLevel, origin, false, 0);
            return 0;
        }

        sendSiteInfo(source, dungeonLevel, resolved.get());

        return Command.SINGLE_SUCCESS;
    }

    private static int warmupNearest(
            CommandSourceStack source,
            int radiusChunks
    ) {
        ServerLevel dungeonLevel = dungeonLevel(source);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal(
                    "ObeliskDepths dimension is not loaded. Check dimension JSON / level stem registration."
            ));
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        BlockPos origin = originInDungeonLevel(source);
        int warmed = DungeonDebugChunkWarmupService.warmupChunks(
                dungeonLevel,
                origin,
                radiusChunks
        );

        Optional<ResolvedDungeonSite> resolved =
                findNearestGeneratedReservable(dungeonLevel, origin, data);

        if (resolved.isEmpty()) {
            sendNoGeneratedSiteDiagnostic(source, dungeonLevel, origin, true, warmed);
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Warmed "
                                + warmed
                                + " chunks near "
                                + origin
                                + "; found authoritative generated site."
                ),
                false
        );
        sendSiteInfo(source, dungeonLevel, resolved.get());

        return Command.SINGLE_SUCCESS;
    }

    private static int enterNearest(CommandSourceStack source) {
        ServerLevel dungeonLevel = dungeonLevel(source);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal(
                    "ObeliskDepths dimension is not loaded. Check dimension JSON / level stem registration."
            ));
            return 0;
        }

        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        BlockPos origin = originInDungeonLevel(source);

        Optional<ResolvedDungeonSite> resolved =
                findNearestGeneratedReservable(dungeonLevel, origin, data);

        if (resolved.isEmpty()) {
            sendNoGeneratedSiteDiagnostic(source, dungeonLevel, origin, false, 0);
            return 0;
        }

        BlockPos target = safeEntryPos(resolved.get().site());

        if (ObeliskDepthsTeleporter.teleportToLevel(player, dungeonLevel, target).isEmpty()) {
            source.sendFailure(Component.literal("Failed to teleport to generated dungeon site."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Entered nearest authoritative generated site at "
                                + target
                                + ". This does not reserve or create a runtime instance."
                ),
                false
        );
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
                                + ": authoritative="
                                + resolved.authoritative()
                                + ", source="
                                + metadataSource
                                + ", rooms="
                                + site.rooms().size()
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

    static BlockPos originInDungeonLevel(CommandSourceStack source) {
        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        return new BlockPos(
                sourcePos.getX(),
                DungeonSitePlacement.PROTOTYPE_Y,
                sourcePos.getZ()
        );
    }

    static BlockPos safeEntryPos(DungeonSite site) {
        return site.startRoom()
                .map(DungeonGeneratedRoom::spawnPos)
                .orElse(site.startPos())
                .above();
    }

    static Optional<ResolvedDungeonSite> findNearestGeneratedReservable(
            ServerLevel dungeonLevel,
            BlockPos origin,
            DungeonManagerSavedData data
    ) {
        return WorldgenDungeonSiteLocator.findNearestReservableSite(
                dungeonLevel,
                origin,
                data::isSiteUnreached
        );
    }

    static void sendNoGeneratedSiteDiagnostic(
            CommandSourceStack source,
            ServerLevel dungeonLevel,
            BlockPos origin,
            boolean warmedChunks,
            int warmedChunkCount
    ) {
        Optional<ResolvedDungeonSite> prototype =
                WorldgenDungeonSiteLocator.findNearestPrototypeSiteForDebug(
                        dungeonLevel,
                        origin,
                        ignored -> true
                );

        source.sendFailure(Component.literal(
                "No generated authoritative dungeon site found near origin "
                        + origin
                        + " in "
                        + dungeonLevel.dimension().identifier()
                        + ". Move/search farther, generate chunks in the ObeliskDepths dimension, "
                        + "or verify structure placement/biome tags."
        ));

        source.sendFailure(Component.literal(
                "Diagnostics: warmedChunks="
                        + warmedChunks
                        + ", warmedChunkCount="
                        + warmedChunkCount
                        + ", prototypePreviewNearby="
                        + prototype.map(site -> site.site().key().toString()).orElse("<none>")
                        + ". Prototype preview metadata is debug-only and was not used."
        ));
    }

    private static ServerLevel dungeonLevel(CommandSourceStack source) {
        return source.getServer().getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
    }
}
