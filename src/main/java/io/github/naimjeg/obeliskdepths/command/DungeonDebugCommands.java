package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.completion.DungeonCompletionResult;
import io.github.naimjeg.obeliskdepths.dungeon.completion.DungeonCompletionService;
import io.github.naimjeg.obeliskdepths.dungeon.debug.DungeonDebugChunkWarmupService;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.lifecycle.DungeonCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardService;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonCellBox;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DungeonDebugCommands {
    private static final int DEFAULT_DEV_WARMUP_RADIUS_CHUNKS = 6;

    private DungeonDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> dungeon() {
        return Commands.literal("dungeon")
                .then(Commands.literal("where")
                        .executes(context -> where(context.getSource())))
                .then(Commands.literal("return")
                        .executes(context -> returnPlayer(context.getSource())))
                .then(Commands.literal("stats")
                        .executes(context -> stats(context.getSource())))
                .then(Commands.literal("enter-depths")
                        .executes(context -> enterDepths(context.getSource(), Optional.empty()))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> enterDepths(
                                                context.getSource(),
                                                Optional.of(new DebugXZ(
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z")
                                                ))
                                        )))))
                .then(Commands.literal("enter-depths-here")
                        .executes(context -> enterDepths(context.getSource(), Optional.empty())))
                .then(Commands.literal("dev-start-nearest")
                        .executes(context -> devStartNearest(
                                context.getSource(),
                                DEFAULT_DEV_WARMUP_RADIUS_CHUNKS
                        ))
                        .then(Commands.argument("warmupRadiusChunks", IntegerArgumentType.integer(0, DungeonDebugChunkWarmupService.MAX_RADIUS_CHUNKS))
                                .executes(context -> devStartNearest(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "warmupRadiusChunks")
                                ))))
                .then(Commands.literal("layout-test")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                                .executes(context -> layoutTest(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")
                                ))))
                .then(Commands.literal("close-empty")
                        .executes(context -> closeEmpty(context.getSource())))
                .then(Commands.literal("purge-expired-sessions")
                        .executes(context -> purgeExpiredSessions(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("cleanup-closed")
                        .executes(context -> cleanupClosed(context.getSource())))
                .then(DungeonSiteDebugCommands.site())
                .then(Commands.literal("rooms")
                        .executes(context -> rooms(context.getSource())))
                .then(Commands.literal("room-here-debug")
                        .executes(context -> roomHereDebug(context.getSource())))
                .then(Commands.literal("clear-room-here")
                        .executes(context -> clearRoomHere(context.getSource())))
                .then(Commands.literal("claim-room-reward-debug")
                        .executes(context -> claimRoomRewardDebug(context.getSource())))
                .then(Commands.literal("complete-debug")
                        .executes(context -> completeDebug(context.getSource())))

                ;
    }

    private static int where(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        Optional<PlayerDungeonData> data = PlayerDungeonTracker.get(player);

        if (data.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("You are not bound to a dungeon instance."),
                    false
            );
            return Command.SINGLE_SUCCESS;
        }

        PlayerDungeonData playerData = data.get();

        source.sendSuccess(
                () -> Component.literal(
                        "Dungeon binding: instance="
                                + playerData.currentInstanceId().map(Object::toString).orElse("<missing>")
                                + ", returnDimension="
                                + playerData.returnDimension().map(Object::toString).orElse("<missing>")
                                + ", returnPos="
                                + playerData.returnPos().map(Object::toString).orElse("<missing>")
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int returnPlayer(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        PlayerDungeonReturnResult result =
                PlayerDungeonReturnService.returnPlayer(player);

        if (result != PlayerDungeonReturnResult.SUCCESS) {
            source.sendFailure(Component.literal("Failed to return from dungeon: " + result));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Returned from dungeon."),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int stats(CommandSourceStack source) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        source.sendSuccess(
                () -> Component.literal(
                        "Dungeon stats: instances="
                                + data.instanceCount()
                                + ", territories="
                                + data.territoryCount()
                                + ", portalSessions="
                                + data.activePortalSessionCount()
                                + ", siteRecords="
                                + data.siteRecordCount()
                                + ", reservedSites="
                                + data.reservedSiteCount()
                                + ", retiredSites="
                                + data.retiredSiteCount()
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int enterDepths(
            CommandSourceStack source,
            Optional<DebugXZ> requestedXZ
    ) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal(
                    "ObeliskDepths dimension is not loaded. Check dimension JSON / level stem registration."
            ));
            return 0;
        }

        BlockPos origin = requestedXZ
                .map(xz -> debugPos(xz.x(), xz.z()))
                .orElseGet(() -> debugPos(
                        player.blockPosition().getX(),
                        player.blockPosition().getZ()
                ));

        BlockPos target = origin;

        if (requestedXZ.isEmpty()) {
            DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
            target = DungeonSiteDebugCommands.findNearestGeneratedReservable(
                            dungeonLevel,
                            origin,
                            data
                    )
                    .map(resolved -> DungeonSiteDebugCommands.safeEntryPos(resolved.site()))
                    .orElse(origin);
        }

        if (ObeliskDepthsTeleporter.teleportToLevel(player, dungeonLevel, target).isEmpty()) {
            source.sendFailure(Component.literal("Failed to enter ObeliskDepths dimension."));
            return 0;
        }

        BlockPos finalTarget = target;

        source.sendSuccess(
                () -> Component.literal(
                        "Entered ObeliskDepths dimension at "
                                + finalTarget
                                + ". Debug entry only: no portal session, no reservation, no runtime instance was created."
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int devStartNearest(
            CommandSourceStack source,
            int warmupRadiusChunks
    ) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal(
                    "ObeliskDepths dimension is not loaded. Check dimension JSON / level stem registration."
            ));
            return 0;
        }

        BlockPos origin = DungeonSiteDebugCommands.originInDungeonLevel(source);
        int warmedChunks = DungeonDebugChunkWarmupService.warmupChunks(
                dungeonLevel,
                origin,
                warmupRadiusChunks
        );

        DungeonDifficulty debugDifficulty = new DungeonDifficulty(
                1,
                0.0F,
                1.0F,
                1
        );

        Optional<DungeonInstance> created =
                DungeonInstanceService.reserveNearestUnreachedWorldgenSite(
                        dungeonLevel,
                        origin,
                        debugDifficulty
                );

        if (created.isEmpty()) {
            DungeonSiteDebugCommands.sendNoGeneratedSiteDiagnostic(
                    source,
                    dungeonLevel,
                    origin,
                    true,
                    warmedChunks
            );
            source.sendFailure(Component.literal(
                    "dev-start-nearest did not create an instance. Check generated-site locator and projection logs."
            ));
            return 0;
        }

        DungeonInstance instance = created.get();
        var returnDimension = player.level().dimension();
        BlockPos returnPos = player.blockPosition();

        Optional<ServerPlayer> teleported =
                ObeliskDepthsTeleporter.teleportToInstanceStart(player, instance);

        if (teleported.isEmpty()) {
            DungeonInstanceService.releaseFailedReservation(
                    dungeonLevel,
                    instance.id()
            );
            source.sendFailure(Component.literal("Failed to teleport to reserved dungeon instance start."));
            return 0;
        }

        ServerPlayer enteredPlayer = teleported.get();

        DungeonInstanceService.addParticipant(
                dungeonLevel,
                instance.id(),
                enteredPlayer.getUUID()
        );
        PlayerDungeonTracker.bindPlayerToInstance(
                enteredPlayer,
                instance.id(),
                returnDimension,
                returnPos
        );
        DungeonSessionManager.getOrCreateDebugSession(
                dungeonLevel,
                instance,
                enteredPlayer.getUUID()
        );
        DungeonSessionManager.registerParticipant(
                dungeonLevel,
                instance.id(),
                enteredPlayer.getUUID()
        );
        DungeonSessionManager.registerPhysicalParticipant(
                dungeonLevel,
                instance.id(),
                enteredPlayer.getUUID()
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Dev-started dungeon instance "
                                + instance.id()
                                + " from authoritative generated site "
                                + instance.siteKey()
                                + " difficulty=tier "
                                + debugDifficulty.tier()
                                + " start="
                                + instance.startPos()
                                + ", warmedChunks="
                                + warmedChunks
                                + ". This is a debug runtime start, not portal gameplay."
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int closeEmpty(CommandSourceStack source) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        int closed = DungeonInstanceService.closeEmptyActiveInstances(dungeonLevel);
        int removedSessions = PortalSessionManager.removeSessionsForInactiveInstances(dungeonLevel);

        source.sendSuccess(
                () -> Component.literal(
                        "Closed empty active dungeon instances: "
                                + closed
                                + ", removed stale portal sessions: "
                                + removedSessions
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int purgeExpiredSessions(CommandSourceStack source) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        int purged = PortalSessionManager.purgeExpired(
                dungeonLevel,
                dungeonLevel.getGameTime()
        );

        source.sendSuccess(
                () -> Component.literal("Purged expired portal sessions: " + purged),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandSourceStack source) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        if (data.instances().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("No dungeon instances."),
                    false
            );
            return Command.SINGLE_SUCCESS;
        }

        for (var instance : data.instances()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "Instance "
                                    + instance.id()
                                    + " status="
                                    + instance.status().getSerializedName()
                                    + " participants="
                                    + instance.participants().size()
                                    + " start="
                                    + instance.startPos()
                                    + " territory="
                                    + instance.territoryId()
                                    + " site="
                                    + instance.siteKey()
                    ),
                    false
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int rooms(CommandSourceStack source) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        Optional<PlayerDungeonData> playerData = PlayerDungeonTracker.get(player);

        if (playerData.isEmpty() || playerData.get().currentInstanceId().isEmpty()) {
            source.sendFailure(Component.literal("You are not bound to a dungeon instance."));
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        var instanceId = playerData.get().currentInstanceId().get();

        var states = data.roomStates(instanceId);

        if (states.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("This instance has no generated room states."),
                    false
            );
            return Command.SINGLE_SUCCESS;
        }

        for (var room : states) {
            source.sendSuccess(
                    () -> Component.literal(
                            "Room "
                                    + room.roomId()
                                    + " type="
                                    + room.type().getSerializedName()
                                    + " status="
                                    + room.status().getSerializedName()
                                    + " rewardClaimed="
                                    + room.rewardClaimed()
                    ),
                    false
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int roomHereDebug(CommandSourceStack source) {
        Optional<ResolvedPlayerSite> resolved = resolvePlayerSite(source);

        if (resolved.isEmpty()) {
            return 0;
        }

        ResolvedPlayerSite context = resolved.get();
        var playerPos = context.player().blockPosition();

        source.sendSuccess(
                () -> Component.literal(
                        "room-here-debug: playerPos="
                                + playerPos
                                + ", instance="
                                + context.instance().id()
                                + ", instanceStart="
                                + context.instance().startPos()
                                + ", siteKey="
                                + context.instance().siteKey()
                                + ", siteStart="
                                + context.site().startPos()
                                + ", siteBounds="
                                + context.site().bounds()
                ),
                false
        );

        Optional<DungeonGeneratedRoom> currentRoom =
                context.site().roomAt(playerPos);

        source.sendSuccess(
                () -> Component.literal(
                        currentRoom
                                .map(room -> "currentRoom id="
                                        + room.id()
                                        + " type="
                                        + room.type().getSerializedName()
                                        + " bounds="
                                        + room.bounds())
                                .orElse("currentRoom=<none>")
                ),
                false
        );

        for (DungeonGeneratedRoom room : context.site().rooms()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "siteRoom id="
                                    + room.id()
                                    + " type="
                                    + room.type().getSerializedName()
                                    + " bounds="
                                    + room.bounds()
                                    + " anchor="
                                    + room.anchorPos()
                                    + " containsPlayer="
                                    + room.contains(playerPos)
                    ),
                    false
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int cleanupClosed(CommandSourceStack source) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        int cleaned = DungeonCleanupService.cleanupPortalClosedInstances(dungeonLevel);

        source.sendSuccess(
                () -> Component.literal("Cleaned portal-closed dungeon instances: " + cleaned),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int clearRoomHere(CommandSourceStack source) {
        Optional<ResolvedPlayerRoom> resolved = resolvePlayerRoom(source);

        if (resolved.isEmpty()) {
            return 0;
        }

        ResolvedPlayerRoom context = resolved.get();

        boolean changed = context.data().setRoomStatus(
                context.instanceId(),
                context.room().id(),
                DungeonRoomStatus.CLEARED
        );

        source.sendSuccess(
                () -> Component.literal(
                        changed
                                ? "Cleared room " + context.room().id()
                                : "Room was already in that state: " + context.room().id()
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int claimRoomRewardDebug(CommandSourceStack source) {
        Optional<ResolvedPlayerRoom> resolved = resolvePlayerRoom(source);

        if (resolved.isEmpty()) {
            return 0;
        }

        ResolvedPlayerRoom context = resolved.get();

        Optional<DungeonRoomState> state = context.data().getRoomState(
                context.instanceId(),
                context.room().id()
        );

        if (state.isEmpty()) {
            source.sendFailure(Component.literal("Current room state is missing."));
            return 0;
        }

        if (state.get().status() != DungeonRoomStatus.CLEARED) {
            source.sendFailure(Component.literal(
                    "Room is not cleared: "
                            + state.get().status().getSerializedName()
            ));
            return 0;
        }

        if (state.get().rewardClaimed()) {
            source.sendFailure(Component.literal("Room reward chest is already opened."));
            return 0;
        }

        boolean changed = DungeonRewardService.markRewardClaimed(
                context.dungeonLevel(),
                context.instanceId(),
                context.room().id()
        );

        if (!changed) {
            source.sendFailure(Component.literal("Room is not reward-chest eligible."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Opened room reward chest: " + context.room().id()),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int completeDebug(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        DungeonCompletionResult result =
                DungeonCompletionService.enterRewardPhase(player);

        if (result != DungeonCompletionResult.SUCCESS) {
            source.sendFailure(Component.literal("Dungeon completion failed: " + result));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Dungeon completed; reward phase started."),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int layoutTest(
            CommandSourceStack source,
            int count
    ) {
        LayoutTestStats stats = new LayoutTestStats();

        for (int index = 0; index < count; index++) {
            long seed = index * 0x9E3779B97F4A7C15L;
            BlockPos layoutOrigin = new BlockPos(
                    (int) (index * 9973L),
                    DungeonSitePlacement.PROTOTYPE_Y - 1,
                    (int) (index * -7919L)
            );

            try {
                DungeonGraph graph = DungeonGraphGenerator.generate(seed);
                DungeonGraphValidator.validate(graph);
                DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);

                DungeonGraph second = DungeonGraphGenerator.generate(seed);
                if (!graph.equals(second)) {
                    stats.recordDeterministicMismatch(seed);
                }

                DungeonLayoutPlan plan = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);
                DungeonPiecePlan piecePlan = DungeonPiecePlanCompiler.compile(
                        layoutOrigin,
                        plan,
                        graph.primaryEntryNodeId()
                );
                stats.recordSuccess(graph, analysis, plan, piecePlan);
            } catch (RuntimeException exception) {
                stats.recordFailure(seed, exception);
            }
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Layout test attempted="
                                + count
                                + " successful="
                                + stats.successful
                                + " failed="
                                + stats.failed
                                + " deterministicMismatches="
                                + stats.deterministicMismatches
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  graph nodes="
                                + stats.minNodes()
                                + ".."
                                + stats.maxNodes
                                + " treeEdges="
                                + stats.minTreeEdges()
                                + ".."
                                + stats.maxTreeEdges
                                + " loopEdges="
                                + stats.minLoopEdges()
                                + ".."
                                + stats.maxLoopEdges
                                + " starts="
                                + stats.minStarts()
                                + ".."
                                + stats.maxStarts
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  analysis sectors="
                                + stats.minSectors()
                                + ".."
                                + stats.maxSectors
                                + " maxBossDistance="
                                + stats.minMaxBossDistance()
                                + ".."
                                + stats.maxMaxBossDistance
                                + " depthBands="
                                + stats.minDepthBands()
                                + ".."
                                + stats.maxDepthBands
                                + " cyclomatic="
                                + stats.minCyclomatic()
                                + ".."
                                + stats.maxCyclomatic
                                + " maxDegree="
                                + stats.minMaxDegree()
                                + ".."
                                + stats.maxMaxDegree
                                + " deadEnds="
                                + stats.minDeadEnds()
                                + ".."
                                + stats.maxDeadEnds
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  embedding avgBoundsCells="
                                + stats.averageBoundsCells()
                                + " maxBoundsCells="
                                + stats.maxBoundsCells
                                + " avgBoundsBlocks="
                                + stats.averageBoundsBlocks()
                                + " maxBoundsBlocks="
                                + stats.maxBoundsBlocks
                                + " avgSiteOuterBlocks="
                                + stats.averageSiteOuterBlocks()
                                + " maxSiteOuterBlocks="
                                + stats.maxSiteOuterBlocks
                                + " avgRouteCount="
                                + stats.averageRouteCount()
                                + " maxRouteCount="
                                + stats.maxRouteCount
                                + " avgRouteCells="
                                + stats.averageRouteCells()
                                + " maxRouteCells="
                                + stats.maxRouteCells
                                + " avgCorridorSegments="
                                + stats.averageCorridorSegments()
                                + " maxCorridorSegments="
                                + stats.maxCorridorSegments
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  failures: embeddingOverlap="
                                + stats.embeddingOverlapFailures
                                + ", disconnected="
                                + stats.disconnectedFailures
                                + ", treeParentage="
                                + stats.treeParentageFailures
                                + ", loop="
                                + stats.loopFailures
                                + ", other="
                                + stats.otherFailures
                ),
                false
        );

        for (String failure : stats.failureSummaries) {
            source.sendFailure(Component.literal("  " + failure));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static DungeonCellBox layoutBounds(DungeonLayoutPlan plan) {
        DungeonCellBox result = null;

        for (DungeonLayoutNode node : plan.nodes()) {
            DungeonCellBox box = node.cellBox();

            if (result == null) {
                result = box;
            } else {
                int minX = Math.min(result.minX(), box.minX());
                int minY = Math.min(result.minY(), box.minY());
                int minZ = Math.min(result.minZ(), box.minZ());
                int maxX = Math.max(result.maxXExclusive(), box.maxXExclusive());
                int maxY = Math.max(result.maxYExclusive(), box.maxYExclusive());
                int maxZ = Math.max(result.maxZExclusive(), box.maxZExclusive());
                result = new DungeonCellBox(
                        minX,
                        minY,
                        minZ,
                        maxX - minX,
                        maxY - minY,
                        maxZ - minZ
                );
            }
        }

        if (result == null) {
            throw new IllegalArgumentException("Cannot compute layout bounds for empty plan");
        }

        return result;
    }

    private static Optional<ResolvedPlayerSite> resolvePlayerSite(
            CommandSourceStack source
    ) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return Optional.empty();
        }

        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return Optional.empty();
        }

        Optional<PlayerDungeonData> playerData = PlayerDungeonTracker.get(player);

        if (playerData.isEmpty() || playerData.get().currentInstanceId().isEmpty()) {
            source.sendFailure(Component.literal("You are not bound to a dungeon instance."));
            return Optional.empty();
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        var instanceId = playerData.get().currentInstanceId().get();
        var instance = data.getInstance(instanceId);

        if (instance.isEmpty()) {
            source.sendFailure(Component.literal("Current dungeon instance no longer exists."));
            return Optional.empty();
        }

        Optional<ResolvedDungeonSite> resolvedSite =
                DungeonSiteProjectionCache.read(
                        dungeonLevel,
                        instance.get().siteKey()
                );

        if (resolvedSite.isEmpty()) {
            source.sendFailure(Component.literal("Dungeon site projection could not be resolved."));
            return Optional.empty();
        }

        return Optional.of(new ResolvedPlayerSite(
                dungeonLevel,
                data,
                player,
                instance.get(),
                resolvedSite.get().site()
        ));
    }

    private static Optional<ResolvedPlayerRoom> resolvePlayerRoom(
            CommandSourceStack source
    ) {
        ServerLevel dungeonLevel = source.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return Optional.empty();
        }

        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return Optional.empty();
        }

        Optional<PlayerDungeonData> playerData = PlayerDungeonTracker.get(player);

        if (playerData.isEmpty() || playerData.get().currentInstanceId().isEmpty()) {
            source.sendFailure(Component.literal("You are not bound to a dungeon instance."));
            return Optional.empty();
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        var instanceId = playerData.get().currentInstanceId().get();
        var instance = data.getInstance(instanceId);

        if (instance.isEmpty()) {
            source.sendFailure(Component.literal("Current dungeon instance no longer exists."));
            return Optional.empty();
        }

        Optional<ResolvedDungeonSite> resolvedSite =
                DungeonSiteProjectionCache.read(
                        dungeonLevel,
                        instance.get().siteKey()
                );

        if (resolvedSite.isEmpty()) {
            source.sendFailure(Component.literal("Dungeon site projection could not be resolved."));
            return Optional.empty();
        }

        Optional<DungeonGeneratedRoom> room = resolvedSite.get().site()
                .rooms()
                .stream()
                .filter(candidate -> candidate.contains(player.blockPosition()))
                .findFirst();

        if (room.isEmpty()) {
            source.sendFailure(Component.literal("You are not inside a generated room."));
            return Optional.empty();
        }

        return Optional.of(new ResolvedPlayerRoom(
                dungeonLevel,
                data,
                instanceId,
                room.get()
        ));
    }

    private static BlockPos debugPos(
            int x,
            int z
    ) {
        /*
         * Debug entry uses a conservative Y above the current flat prototype
         * floor. It does not place blocks and does not create/reserve site
         * metadata.
         */
        return new BlockPos(
                x,
                DungeonSitePlacement.PROTOTYPE_Y + 2,
                z
        );
    }

    private static final class LayoutTestStats {
        private static final int MAX_FAILURE_SUMMARIES = 8;

        private int successful;
        private int failed;
        private int deterministicMismatches;
        private int minNodes = Integer.MAX_VALUE;
        private int maxNodes;
        private int minTreeEdges = Integer.MAX_VALUE;
        private int maxTreeEdges;
        private int minLoopEdges = Integer.MAX_VALUE;
        private int maxLoopEdges;
        private int minStarts = Integer.MAX_VALUE;
        private int maxStarts;
        private int minSectors = Integer.MAX_VALUE;
        private int maxSectors;
        private int minMaxBossDistance = Integer.MAX_VALUE;
        private int maxMaxBossDistance;
        private int minDepthBands = Integer.MAX_VALUE;
        private int maxDepthBands;
        private int minCyclomatic = Integer.MAX_VALUE;
        private int maxCyclomatic;
        private int minMaxDegree = Integer.MAX_VALUE;
        private int maxMaxDegree;
        private int minDeadEnds = Integer.MAX_VALUE;
        private int maxDeadEnds;
        private int totalBoundsCells;
        private int maxBoundsCells;
        private int totalBoundsBlocks;
        private int maxBoundsBlocks;
        private int totalSiteOuterBlocks;
        private int maxSiteOuterBlocks;
        private int totalRouteCount;
        private int maxRouteCount;
        private int totalRouteCells;
        private int maxRouteCells;
        private int totalCorridorSegments;
        private int maxCorridorSegments;
        private int embeddingOverlapFailures;
        private int disconnectedFailures;
        private int treeParentageFailures;
        private int loopFailures;
        private int otherFailures;
        private final List<String> failureSummaries = new ArrayList<>();

        private void recordSuccess(
                DungeonGraph graph,
                DungeonGraphAnalysis analysis,
                DungeonLayoutPlan plan,
                DungeonPiecePlan piecePlan
        ) {
            DungeonCellBox bounds = layoutBounds(plan);
            int boundsCells = bounds.sizeX() * bounds.sizeY() * bounds.sizeZ();
            int boundsBlocks =
                    bounds.sizeX()
                            * DungeonLayoutConstants.CELL_SIZE_X
                            * bounds.sizeY()
                            * DungeonLayoutConstants.CELL_SIZE_Y
                            * bounds.sizeZ()
                            * DungeonLayoutConstants.CELL_SIZE_Z;

            this.successful++;
            this.minNodes = Math.min(this.minNodes, graph.nodes().size());
            this.maxNodes = Math.max(this.maxNodes, graph.nodes().size());
            this.minTreeEdges = Math.min(this.minTreeEdges, graph.treeEdges().size());
            this.maxTreeEdges = Math.max(this.maxTreeEdges, graph.treeEdges().size());
            this.minLoopEdges = Math.min(this.minLoopEdges, graph.loopEdges().size());
            this.maxLoopEdges = Math.max(this.maxLoopEdges, graph.loopEdges().size());
            this.minStarts = Math.min(this.minStarts, graph.entryNodeIds().size());
            this.maxStarts = Math.max(this.maxStarts, graph.entryNodeIds().size());
            this.minSectors = Math.min(this.minSectors, analysis.sectors().size());
            this.maxSectors = Math.max(this.maxSectors, analysis.sectors().size());
            this.minMaxBossDistance = Math.min(this.minMaxBossDistance, analysis.maxDistanceToBoss());
            this.maxMaxBossDistance = Math.max(this.maxMaxBossDistance, analysis.maxDistanceToBoss());
            this.minDepthBands = Math.min(this.minDepthBands, analysis.depthBands().size());
            this.maxDepthBands = Math.max(this.maxDepthBands, analysis.depthBands().size());
            int cyclomatic = graph.edges().size() - graph.nodes().size() + 1;
            this.minCyclomatic = Math.min(this.minCyclomatic, cyclomatic);
            this.maxCyclomatic = Math.max(this.maxCyclomatic, cyclomatic);
            this.minMaxDegree = Math.min(this.minMaxDegree, analysis.maxDegree());
            this.maxMaxDegree = Math.max(this.maxMaxDegree, analysis.maxDegree());
            int deadEnds = (int) analysis.deadEndCount();
            this.minDeadEnds = Math.min(this.minDeadEnds, deadEnds);
            this.maxDeadEnds = Math.max(this.maxDeadEnds, deadEnds);
            this.totalBoundsCells += boundsCells;
            this.maxBoundsCells = Math.max(this.maxBoundsCells, boundsCells);
            this.totalBoundsBlocks += boundsBlocks;
            this.maxBoundsBlocks = Math.max(this.maxBoundsBlocks, boundsBlocks);

            int siteOuterBlocks = piecePlan.siteBounds().getXSpan()
                    * piecePlan.siteBounds().getYSpan()
                    * piecePlan.siteBounds().getZSpan();
            this.totalSiteOuterBlocks += siteOuterBlocks;
            this.maxSiteOuterBlocks = Math.max(this.maxSiteOuterBlocks, siteOuterBlocks);
            int routeCount = piecePlan.routedCorridors().size();
            int routeCells = piecePlan.routedCorridors()
                    .stream()
                    .mapToInt(io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonRoutedCorridor::lengthCells)
                    .sum();
            int corridorSegments = (int) piecePlan.corridorCount();
            this.totalRouteCount += routeCount;
            this.maxRouteCount = Math.max(this.maxRouteCount, routeCount);
            this.totalRouteCells += routeCells;
            this.maxRouteCells = Math.max(this.maxRouteCells, routeCells);
            this.totalCorridorSegments += corridorSegments;
            this.maxCorridorSegments = Math.max(this.maxCorridorSegments, corridorSegments);
        }

        private void recordDeterministicMismatch(long seed) {
            this.deterministicMismatches++;
            if (this.failureSummaries.size() < MAX_FAILURE_SUMMARIES) {
                this.failureSummaries.add("seed=" + seed + " deterministic mismatch");
            }
        }

        private void recordFailure(
                long seed,
                RuntimeException exception
        ) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            String lower = message.toLowerCase(Locale.ROOT);

            this.failed++;

            if (lower.contains("overlap")) {
                this.embeddingOverlapFailures++;
            } else if (lower.contains("connected")) {
                this.disconnectedFailures++;
            } else if (lower.contains("tree") || lower.contains("parent")) {
                this.treeParentageFailures++;
            } else if (lower.contains("loop") || lower.contains("cycle")) {
                this.loopFailures++;
            } else {
                this.otherFailures++;
            }

            if (this.failureSummaries.size() < MAX_FAILURE_SUMMARIES) {
                this.failureSummaries.add(
                        "seed="
                                + seed
                                + " reason="
                                + message
                );
            }
        }

        private int averageBoundsCells() {
            return this.successful == 0 ? 0 : this.totalBoundsCells / this.successful;
        }

        private int minNodes() {
            return this.successful == 0 ? 0 : this.minNodes;
        }

        private int minTreeEdges() {
            return this.successful == 0 ? 0 : this.minTreeEdges;
        }

        private int minLoopEdges() {
            return this.successful == 0 ? 0 : this.minLoopEdges;
        }

        private int minStarts() {
            return this.successful == 0 ? 0 : this.minStarts;
        }

        private int minSectors() {
            return this.successful == 0 ? 0 : this.minSectors;
        }

        private int minMaxBossDistance() {
            return this.successful == 0 ? 0 : this.minMaxBossDistance;
        }

        private int minDepthBands() {
            return this.successful == 0 ? 0 : this.minDepthBands;
        }

        private int minCyclomatic() {
            return this.successful == 0 ? 0 : this.minCyclomatic;
        }

        private int minMaxDegree() {
            return this.successful == 0 ? 0 : this.minMaxDegree;
        }

        private int minDeadEnds() {
            return this.successful == 0 ? 0 : this.minDeadEnds;
        }

        private int averageBoundsBlocks() {
            return this.successful == 0 ? 0 : this.totalBoundsBlocks / this.successful;
        }

        private int averageSiteOuterBlocks() {
            return this.successful == 0 ? 0 : this.totalSiteOuterBlocks / this.successful;
        }

        private int averageRouteCount() {
            return this.successful == 0 ? 0 : this.totalRouteCount / this.successful;
        }

        private int averageRouteCells() {
            return this.successful == 0 ? 0 : this.totalRouteCells / this.successful;
        }

        private int averageCorridorSegments() {
            return this.successful == 0 ? 0 : this.totalCorridorSegments / this.successful;
        }
    }

    private record DebugXZ(
            int x,
            int z
    ) {
    }

    private record ResolvedPlayerRoom(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonInstanceId instanceId,
            DungeonGeneratedRoom room
    ) {
    }

    private record ResolvedPlayerSite(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            ServerPlayer player,
            io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance instance,
            DungeonSite site
    ) {
    }
}
