package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.completion.DungeonCompletionResult;
import io.github.naimjeg.obeliskdepths.dungeon.completion.DungeonCompletionService;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
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
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class DungeonDebugCommands {
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
            source.sendFailure(Component.literal("Room reward is already claimed."));
            return 0;
        }

        boolean changed = DungeonRewardService.markRewardClaimed(
                context.dungeonLevel(),
                context.instanceId(),
                context.room().id()
        );

        if (!changed) {
            source.sendFailure(Component.literal("Room is not reward-eligible."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Claimed room reward: " + context.room().id()),
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
                DungeonCompletionService.completeAndReturnPlayer(player);

        if (result != DungeonCompletionResult.SUCCESS) {
            source.sendFailure(Component.literal("Dungeon completion failed: " + result));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Dungeon completed and player returned."),
                false
        );

        return Command.SINGLE_SUCCESS;
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
