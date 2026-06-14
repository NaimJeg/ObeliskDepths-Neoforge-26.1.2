package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonAccessMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalEntityService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.registry.ModStructures;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
        if (!(player.level() instanceof ServerLevel sourceLevel)) {
            return false;
        }

        long acceptedNanos = System.nanoTime();
        long gameTime = dungeonLevel.getGameTime();
        ObeliskDepths.LOGGER.debug(
                "[OD timing] portalRequestAccepted player={} mode={} obelisk={} sourceDimension={} gameTime={}",
                player.getGameProfile().name(),
                requestedMode,
                obeliskPos,
                sourceLevel.dimension().identifier(),
                gameTime
        );

        if (!dungeonLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            ObeliskDepths.LOGGER.warn(
                    "[OD locator] rejected lookup reason=wrong_target_dimension player={} targetDimension={} expected={}",
                    player.getGameProfile().name(),
                    dungeonLevel.dimension().identifier(),
                    ModDimensions.OBELISK_DEPTHS_LEVEL.identifier()
            );
            return false;
        }

        if (requestedMode == DungeonAccessMode.PARTY_OPEN) {
            long lookupStart = System.nanoTime();
            Optional<ActivationTarget> existingTarget =
                    findExistingPartyOpenTarget(
                            dungeonLevel,
                            sourceLevel,
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
                return ensureExistingPortal(
                        player,
                        sourceLevel,
                        dungeonLevel,
                        existingTarget.get()
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

        BlockPos portalAnchor = obeliskPos.immutable();

        DungeonInstance instance = null;
        PortalSession portalSession = null;
        UUIDSession createdDungeonSession = null;

        try {
            long reserveStart = System.nanoTime();
            ObeliskDepths.LOGGER.debug(
                    "[OD locator] lookup begin player={} mode={} obelisk={} sourceDimension={} targetDimension={} searchOrigin={} structureSet={} structure={} placementType=minecraft:random_spread spacing={} separation={} salt={} candidateLimit={} correctTargetDimension=true",
                    player.getGameProfile().name(),
                    requestedMode,
                    obeliskPos,
                    sourceLevel.dimension().identifier(),
                    dungeonLevel.dimension().identifier(),
                    obeliskPos,
                    ModStructures.OBELISK_DUNGEONS.identifier(),
                    ModStructures.DEPTHS_SITE.identifier(),
                    ObeliskDungeonPlacementSettings.SPACING,
                    ObeliskDungeonPlacementSettings.SEPARATION,
                    ObeliskDungeonPlacementSettings.SALT,
                    ObeliskDungeonPlacementSettings.MAX_LOOKUP_CANDIDATES
            );

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
                        Component.translatable("message.obeliskdepths.portal.no_site")
                );
                return false;
            }

            instance = createdInstance.get();
            long sessionStart = System.nanoTime();
            portalSession = PortalSessionManager.openSession(
                    dungeonLevel,
                    instance.id(),
                    player.getUUID(),
                    sourceLevel.dimension(),
                    obeliskPos,
                    portalAnchor,
                    requestedMode,
                    gameTime
            );

            boolean hadDungeonSession = DungeonSessionManager.findSessionByInstance(
                    dungeonLevel,
                    instance.id()
            ).isPresent();
            DungeonSession dungeonSession = DungeonSessionManager.getOrCreateForPortal(
                    dungeonLevel,
                    instance,
                    portalSession,
                    tribute.valid()
            );

            if (!hadDungeonSession) {
                createdDungeonSession = new UUIDSession(dungeonSession.id());
            }

            ObeliskDepths.LOGGER.debug(
                    "[OD timing] sessionCreation player={} instance={} portalSession={} elapsedMicros={}",
                    player.getGameProfile().name(),
                    instance.id(),
                    portalSession.id(),
                    (System.nanoTime() - sessionStart) / 1_000L
            );

            if (DungeonPortalEntityService.ensurePortal(sourceLevel, portalSession).isEmpty()) {
                player.sendOverlayMessage(
                        Component.translatable("message.obeliskdepths.portal.spawn_failed")
                );
                rollbackCreatedTarget(
                        sourceLevel,
                        dungeonLevel,
                        instance,
                        portalSession,
                        createdDungeonSession
                );
                return false;
            }

            consumeTributeIfNeeded(player, tributeStack, tribute);
            player.sendOverlayMessage(
                    Component.translatable("message.obeliskdepths.portal.opened")
            );
            ObeliskDepths.LOGGER.info(
                    "Opened dungeon portal instance={} session={} mode={} anchor={}",
                    instance.id(),
                    portalSession.id(),
                    portalSession.accessMode(),
                    portalSession.portalAnchorPos()
            );
            ObeliskDepths.LOGGER.debug(
                    "[OD timing] activationComplete player={} instance={} totalMicros={}",
                    player.getGameProfile().name(),
                    instance.id(),
                    (System.nanoTime() - acceptedNanos) / 1_000L
            );
            return true;
        } catch (Exception exception) {
            rollbackCreatedTarget(
                    sourceLevel,
                    dungeonLevel,
                    instance,
                    portalSession,
                    createdDungeonSession
            );
            ObeliskDepths.LOGGER.error(
                    "Failed to open obelisk dungeon portal at {} for player {}",
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
            ServerLevel sourceLevel,
            BlockPos obeliskPos,
            long gameTime
    ) {
        Optional<PortalSession> existingSession =
                PortalSessionManager.findActivePartyOpenSession(
                        dungeonLevel,
                        sourceLevel.dimension(),
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

        return Optional.of(new ActivationTarget(instance.get(), session));
    }

    private static boolean ensureExistingPortal(
            ServerPlayer player,
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            ActivationTarget target
    ) {
        DungeonSessionManager.getOrCreateForPortal(
                dungeonLevel,
                target.instance(),
                target.session(),
                false
        );

        if (DungeonPortalEntityService.ensurePortal(sourceLevel, target.session()).isEmpty()) {
            player.sendOverlayMessage(
                    Component.translatable("message.obeliskdepths.portal.spawn_failed")
            );
            return false;
        }

        player.sendOverlayMessage(
                Component.translatable("message.obeliskdepths.portal.opened")
        );
        return true;
    }

    private static void rollbackCreatedTarget(
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            PortalSession session,
            UUIDSession createdDungeonSession
    ) {
        if (session != null) {
            DungeonPortalEntityService.removePortalsForSession(
                    sourceLevel,
                    session.id(),
                    session.portalAnchorPos()
            );
            PortalSessionManager.removeSession(dungeonLevel, session.id());
        }

        if (createdDungeonSession != null) {
            DungeonSessionManager.removeSession(dungeonLevel, createdDungeonSession.id());
        }

        if (instance != null) {
            DungeonInstanceService.releaseFailedReservation(dungeonLevel, instance.id());
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
            PortalSession session
    ) {
    }

    private record UUIDSession(java.util.UUID id) {
    }
}

