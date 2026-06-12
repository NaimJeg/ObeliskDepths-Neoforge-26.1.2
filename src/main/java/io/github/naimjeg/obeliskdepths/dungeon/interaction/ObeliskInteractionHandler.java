package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessController;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessResult;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonAccessMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class ObeliskInteractionHandler {
    private ObeliskInteractionHandler() {
    }

    public static boolean activate(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            BlockPos obeliskPos
    ) {
        DungeonAccessMode requestedMode = player.isShiftKeyDown()
                ? DungeonAccessMode.SOLO
                : DungeonAccessMode.PARTY_OPEN;

        return activate(
                player,
                dungeonLevel,
                obeliskPos,
                requestedMode,
                player.getMainHandItem()
        );
    }

    public static boolean activate(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            BlockPos obeliskPos,
            DungeonAccessMode requestedMode,
            ItemStack tributeStack
    ) {
        long acceptedNanos = System.nanoTime();
        long gameTime = player.level().getGameTime();
        ObeliskDepths.LOGGER.debug(
                "[OD timing] portalRequestAccepted player={} mode={} obelisk={} gameTime={}",
                player.getGameProfile().name(),
                requestedMode,
                obeliskPos,
                gameTime
        );

        if (requestedMode == DungeonAccessMode.PARTY_OPEN) {
            long lookupStart = System.nanoTime();
            Optional<ActivationTarget> existingTarget =
                    findExistingPartyOpenTarget(
                            dungeonLevel,
                            obeliskPos,
                            gameTime
                    );
            ObeliskDepths.LOGGER.debug(
                    "[OD timing] existingSessionLookup player={} found={} elapsedMicros={}",
                    player.getGameProfile().name(),
                    existingTarget.isPresent(),
                    (System.nanoTime() - lookupStart) / 1_000L
            );

            if (existingTarget.isPresent()) {
                return enterTarget(
                        player,
                        dungeonLevel,
                        existingTarget.get(),
                        null,
                        tributeStack,
                        false,
                        obeliskPos,
                        gameTime
                );
            }
        }

        ResolvedTribute tribute = TributeResolver.resolve(tributeStack);

        if (!tribute.valid()) {
            player.sendOverlayMessage(
                    Component.translatable("message.obeliskdepths.obelisk.invalid_tribute")
            );
            return false;
        }

        DungeonInstance instance = null;
        PortalSession session = null;

        try {
            /*
             * This reserves an authoritative worldgen dungeon site in the ObeliskDepths
             * dimension. The caller must pass ModDimensions.OBELISK_DEPTHS_LEVEL here,
             * not the player's current overworld level.
             *
             * Do not replace this with prototype metadata. If no generated site is found,
             * the fix is dimension/structure/chunk-generation diagnostics, not fake runtime
             * reservation.
             */
            long reserveStart = System.nanoTime();
            Optional<DungeonInstance> createdInstance =
                    DungeonInstanceService.reserveNearestUnreachedWorldgenSite(
                            dungeonLevel,
                            obeliskPos,
                            tribute.toDifficulty()
                    );
            ObeliskDepths.LOGGER.debug(
                    "[OD timing] siteLookupAndReservation player={} found={} elapsedMicros={}",
                    player.getGameProfile().name(),
                    createdInstance.isPresent(),
                    (System.nanoTime() - reserveStart) / 1_000L
            );

            if (createdInstance.isEmpty()) {
                player.sendOverlayMessage(
                        Component.literal("No unreached dungeon site was found nearby.")
                );
                return false;
            }

            instance = createdInstance.get();

            long sessionStart = System.nanoTime();
            session = PortalSessionManager.openSession(
                    dungeonLevel,
                    instance.id(),
                    player.getUUID(),
                    obeliskPos,
                    requestedMode,
                    gameTime
            );
            ObeliskDepths.LOGGER.debug(
                    "[OD timing] sessionCreation player={} instance={} elapsedMicros={}",
                    player.getGameProfile().name(),
                    instance.id(),
                    (System.nanoTime() - sessionStart) / 1_000L
            );

            ActivationTarget newTarget = new ActivationTarget(
                    instance,
                    session,
                    true
            );

            return enterTarget(
                    player,
                    dungeonLevel,
                    newTarget,
                    tribute,
                    tributeStack,
                    true,
                    obeliskPos,
                    gameTime
            );
        } catch (Exception exception) {
            long rollbackStart = System.nanoTime();
            rollbackCreatedTarget(dungeonLevel, instance, session);
            ObeliskDepths.LOGGER.debug(
                    "[OD timing] activationRollback player={} elapsedMicros={} totalMicros={}",
                    player.getGameProfile().name(),
                    (System.nanoTime() - rollbackStart) / 1_000L,
                    (System.nanoTime() - acceptedNanos) / 1_000L
            );

            ObeliskDepths.LOGGER.error(
                    "Failed to claim obelisk dungeon site at {} for player {}",
                    obeliskPos,
                    player.getGameProfile().name(),
                    exception
            );

            player.sendOverlayMessage(
                    Component.translatable("message.obeliskdepths.obelisk.activation_failed")
            );

            return false;
        }
    }

    private static Optional<ActivationTarget> findExistingPartyOpenTarget(
            ServerLevel dungeonLevel,
            BlockPos obeliskPos,
            long gameTime
    ) {
        Optional<PortalSession> existingSession =
                PortalSessionManager.findActivePartyOpenSession(
                        dungeonLevel,
                        obeliskPos,
                        gameTime
                );

        if (existingSession.isEmpty()) {
            return Optional.empty();
        }

        PortalSession session = existingSession.get();

        Optional<DungeonInstance> instance =
                DungeonInstanceService.get(dungeonLevel, session.instanceId());

        if (instance.isEmpty()) {
            PortalSessionManager.removeSession(dungeonLevel, session.id());
            return Optional.empty();
        }

        return Optional.of(new ActivationTarget(
                instance.get(),
                session,
                false
        ));
    }

    private static boolean enterTarget(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            ActivationTarget target,
            ResolvedTribute tribute,
            ItemStack tributeStack,
            boolean consumeTributeOnSuccess,
            BlockPos obeliskPos,
            long gameTime
    ) {
        long enterStart = System.nanoTime();
        ResourceKey<Level> returnDimension = player.level().dimension();
        BlockPos returnPos = player.blockPosition();
        Optional<PlayerDungeonData> previousPlayerData =
                PlayerDungeonTracker.get(player);

        boolean instanceParticipantAdded = false;
        boolean sessionParticipantAdded = false;
        boolean playerBound = false;

        try {
            DungeonAccessResult accessResult = DungeonAccessController.canEnter(
                    player.getUUID(),
                    target.session(),
                    target.instance(),
                    gameTime
            );

            if (accessResult != DungeonAccessResult.ALLOW) {
                player.sendOverlayMessage(
                        Component.literal("Cannot enter dungeon: " + accessResult)
                );
                return false;
            }

            /*
             * New model:
             * Terrain/rooms/chunks must already exist from worldgen.
             * Do not generate or clear blocks here.
             */

            long teleportStart = System.nanoTime();
            Optional<ServerPlayer> teleportedPlayer =
                    ObeliskDepthsTeleporter.teleportToInstanceStart(
                            player,
                            target.instance()
                    );
            ObeliskDepths.LOGGER.debug(
                    "[OD timing] teleportationStage player={} success={} elapsedMicros={}",
                    player.getGameProfile().name(),
                    teleportedPlayer.isPresent(),
                    (System.nanoTime() - teleportStart) / 1_000L
            );

            if (teleportedPlayer.isEmpty()) {
                if (target.createdNow()) {
                    rollbackCreatedTarget(
                            dungeonLevel,
                            target.instance(),
                            target.session()
                    );
                }

                player.sendOverlayMessage(
                        Component.literal("Could not resolve a safe dungeon entry position.")
                );

                return false;
            }

            ServerPlayer enteredPlayer = teleportedPlayer.get();

            instanceParticipantAdded = DungeonInstanceService.addParticipant(
                    dungeonLevel,
                    target.instance().id(),
                    enteredPlayer.getUUID()
            );

            sessionParticipantAdded = PortalSessionManager.addParticipant(
                    dungeonLevel,
                    target.session().id(),
                    enteredPlayer.getUUID()
            );

            DungeonSessionManager.getOrCreateForPortal(
                    dungeonLevel,
                    target.instance(),
                    target.session(),
                    target.createdNow() && tribute != null && tribute.valid()
            );

            DungeonSessionManager.registerParticipant(
                    dungeonLevel,
                    target.instance().id(),
                    enteredPlayer.getUUID()
            );

            PlayerDungeonTracker.bindPlayerToInstance(
                    enteredPlayer,
                    target.instance().id(),
                    returnDimension,
                    returnPos
            );

            playerBound = true;

            if (consumeTributeOnSuccess && tribute != null && tributeStack != null) {
                consumeTributeIfNeeded(
                        enteredPlayer,
                        tributeStack,
                        tribute
                );
            }

            ObeliskDepths.LOGGER.info(
                    "{} dungeon site instance {} mode={} createdNow={} participants={}",
                    target.createdNow() ? "Claimed" : "Joined",
                    target.instance().id(),
                    target.session().accessMode(),
                    target.createdNow(),
                    target.instance().participants().size()
            );
            ObeliskDepths.LOGGER.debug(
                    "[OD timing] activationComplete player={} instance={} totalMicros={}",
                    enteredPlayer.getGameProfile().name(),
                    target.instance().id(),
                    (System.nanoTime() - enterStart) / 1_000L
            );

            return true;
        } catch (Exception exception) {
            if (playerBound) {
                PlayerDungeonTracker.restore(player, previousPlayerData);
            }

            if (sessionParticipantAdded) {
                PortalSessionManager.removeParticipant(
                        dungeonLevel,
                        target.session().id(),
                        player.getUUID()
                );
            }

            if (instanceParticipantAdded) {
                DungeonInstanceService.removeParticipant(
                        dungeonLevel,
                        target.instance().id(),
                        player.getUUID()
                );
            }

            if (target.createdNow()) {
                rollbackCreatedTarget(
                        dungeonLevel,
                        target.instance(),
                        target.session()
                );
            }

            ObeliskDepths.LOGGER.error(
                    "Failed to enter obelisk dungeon site at {} for player {}",
                    obeliskPos,
                    player.getGameProfile().name(),
                    exception
            );

            player.sendOverlayMessage(
                    Component.literal("Failed to enter dungeon.")
            );

            return false;
        }
    }

    private static void rollbackCreatedTarget(
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            PortalSession session
    ) {
        if (session != null) {
            PortalSessionManager.removeSession(dungeonLevel, session.id());
        }

        if (instance != null) {
            DungeonInstanceService.releaseFailedReservation(
                    dungeonLevel,
                    instance.id()
            );
        }
    }

    private static void consumeTributeIfNeeded(
            ServerPlayer player,
            ItemStack tributeStack,
            ResolvedTribute tribute
    ) {
        if (player.getAbilities().instabuild) {
            return;
        }

        tributeStack.shrink(tribute.amount());
    }

    private record ActivationTarget(
            DungeonInstance instance,
            PortalSession session,
            boolean createdNow
    ) {
    }
}
