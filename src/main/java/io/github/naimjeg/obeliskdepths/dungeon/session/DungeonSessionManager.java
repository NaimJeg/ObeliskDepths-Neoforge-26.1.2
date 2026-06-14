package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonMobResolution;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardClaimResult;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardService;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class DungeonSessionManager {
    public static final int ABANDON_GRACE_TICKS = 20 * 90;
    private static final int NORMAL_MOB_KILL_SCORE = 1;

    private DungeonSessionManager() {
    }

    public static DungeonSession getOrCreateForPortal(
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession portalSession,
            boolean tributeBonusActive
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        Optional<DungeonSession> existing =
                data.findSessionByInstance(instance.id());

        if (existing.isPresent()) {
            DungeonSession session = existing.get();
            boolean changed = session.setAccessMode(fromPortalAccessMode(
                    portalSession.accessMode()
            ));

            if (tributeBonusActive && !session.tributeBonusActive()) {
                /*
                 * This branch is intentionally not toggled yet. Tribute bonuses
                 * should be fixed at run creation; joining an existing run should
                 * not reactivate a consumed/expired bonus.
                 */
                ObeliskDepths.LOGGER.debug(
                        "Ignoring late tribute bonus activation for existing dungeon session {}",
                        session.id()
                );
            }

            if (changed) {
                data.markSessionsDirty();
            }

            return session;
        }

        DungeonSession created = DungeonSession.createForPortal(
                instance,
                portalSession.opener(),
                fromPortalAccessMode(portalSession.accessMode()),
                tributeBonusActive,
                dungeonLevel.getGameTime()
        );

        data.addSession(created);

        ObeliskDepths.LOGGER.debug(
                "Created dungeon session: session={}, instance={}, starter={}, site={}, access={}, tributeBonus={}",
                created.id(),
                created.instanceId(),
                created.starterPlayerId(),
                created.siteKey(),
                created.accessMode(),
                created.tributeBonusActive()
        );

        return created;
    }

    public static DungeonSession getOrCreateDebugSession(
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            UUID starterPlayerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> existing =
                data.findSessionByInstance(instance.id());

        if (existing.isPresent()) {
            return existing.get();
        }

        /*
         * Debug/dev entry is not a portal session and must not change portal
         * semantics. It creates the minimum runtime session needed by encounter
         * cleanup/progress systems after a real authoritative site is reserved.
         */
        DungeonSession created = DungeonSession.createActive(
                instance,
                starterPlayerId,
                DungeonAccessMode.OPEN,
                false,
                dungeonLevel.getGameTime()
        );

        data.addSession(created);
        return created;
    }

    public static Optional<DungeonSession> findSessionByPlayer(
            ServerLevel dungeonLevel,
            UUID playerId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .findSessionByPlayer(playerId);
    }

    public static Optional<DungeonSession> findSessionBySite(
            ServerLevel dungeonLevel,
            DungeonSiteKey siteKey
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .findSessionBySite(siteKey);
    }

    public static Optional<DungeonSession> findSessionByInstance(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .findSessionByInstance(instanceId);
    }

    public static boolean removeSession(
            ServerLevel dungeonLevel,
            UUID sessionId
    ) {
        DungeonSessionProgressBarService.removeSession(sessionId);
        return DungeonManagerSavedData.get(dungeonLevel).removeSession(sessionId);
    }

    public static boolean registerParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = session.get().registerParticipant(playerId);

        if (changed) {
            data.markSessionsDirty();
        }

        return changed;
    }

    public static boolean registerPhysicalParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = session.get().registerPhysicalParticipant(playerId);

        if (changed) {
            data.markSessionsDirty();
        }

        return changed;
    }

    public static void markPortalEntrySucceeded(
            ServerLevel dungeonLevel,
            DungeonSession session,
            UUID playerId,
            long gameTime
    ) {
        if (session.markPortalEntrySucceeded(playerId, gameTime)) {
            DungeonManagerSavedData.get(dungeonLevel).markSessionsDirty();
        }
    }

    public static boolean unregisterPhysicalParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed =
                session.get().unregisterPhysicalParticipant(playerId);

        if (changed) {
            data.markSessionsDirty();
        }

        return changed;
    }

    public static boolean onRegisteredDungeonMobKilled(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID entityId
    ) {
        return DungeonEncounterDirector.resolveControlledMob(
                dungeonLevel,
                instanceId,
                entityId,
                DungeonMobResolution.KILLED
        );
    }

    public static boolean initializeEncounterProgress(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            int fixedKillQuota
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = session.get().initializeFixedKillQuota(fixedKillQuota);

        if (changed) {
            data.markSessionsDirty();
            DungeonSessionProgressBarService.updateSession(dungeonLevel, session.get());
        }

        return changed;
    }

    public static boolean removeParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = session.get().removeParticipant(playerId);

        if (changed) {
            data.markSessionsDirty();
        }

        return changed;
    }

    public static boolean creditEncounterNormalKill(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = session.get().creditNormalCombatKill(
                NORMAL_MOB_KILL_SCORE
        );

        if (changed) {
            data.markSessionsDirty();
            DungeonSessionProgressBarService.updateSession(dungeonLevel, session.get());
        }

        return changed;
    }

    public static void tickSessions(ServerLevel dungeonLevel) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        long gameTime = dungeonLevel.getGameTime();

        for (DungeonSession session : data.sessions()) {
            if (session.state() == DungeonSessionState.WAITING_FOR_ENTRY) {
                tickWaitingForEntry(
                        dungeonLevel,
                        data,
                        session,
                        gameTime
                );
                continue;
            }

            if (!session.state().needsRuntimeTick()) {
                continue;
            }

            ServerPlayer starter =
                    dungeonLevel.getServer()
                            .getPlayerList()
                            .getPlayer(session.starterPlayerId());

            boolean starterInside = starter != null
                    && isInsideSessionArea(dungeonLevel, starter, session);

            if (starterInside) {
                if (session.markStarterInside(gameTime)) {
                    data.markSessionsDirty();
                }

                continue;
            }

            /*
             * Only an entered run reaches this branch. Waiting portal sessions are
             * handled above and never consume the abandonment grace period.
             */
            if (gameTime - session.lastStarterInsideGameTime()
                    < ABANDON_GRACE_TICKS) {
                if (session.markAbandonPending()) {
                    data.markSessionsDirty();
                }

                continue;
            }

            abandonAndCleanup(dungeonLevel, session);
        }
    }

    private static void tickWaitingForEntry(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonSession session,
            long gameTime
    ) {
        Optional<DungeonInstance> optionalInstance =
                data.getInstance(session.instanceId());

        if (optionalInstance.isEmpty()) {
            if (session.markFailed()) {
                data.markSessionsDirty();
            }

            DungeonSessionProgressBarService.removeSession(session.id());
            return;
        }

        DungeonInstance instance = optionalInstance.get();

        if (instance.status() != DungeonStatus.ACTIVE) {
            if (session.markCleaned()) {
                data.markSessionsDirty();
            }

            DungeonSessionProgressBarService.removeSession(session.id());
            return;
        }

        /*
         * Defensive self-healing: a successfully registered player proves that
         * entry happened even if another caller failed to finalize the session
         * state.
         */
        if (!instance.participants().isEmpty()) {
            if (session.setState(DungeonSessionState.ACTIVE)) {
                data.markSessionsDirty();
            }

            return;
        }

        if (data.hasValidPortalSessionForInstance(
                session.instanceId(),
                gameTime
        )) {
            return;
        }

        closeUnenteredSession(
                dungeonLevel,
                data,
                session,
                instance,
                gameTime
        );
    }

    private static void closeUnenteredSession(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonSession session,
            DungeonInstance instance,
            long gameTime
    ) {
        boolean changed = session.markAbandoned();

        if (instance.setStatus(DungeonStatus.PORTAL_CLOSED)) {
            changed = true;
        }

        if (instance.closedGameTime() < 0L) {
            instance.markClosedAt(gameTime);
            changed = true;
        }

        if (changed) {
            data.markSessionsDirty();
        }

        cleanupSession(dungeonLevel, session);
        DungeonRuntimeArtifactCleanupService.cleanupInstanceArtifacts(
                dungeonLevel,
                instance.id()
        );

        ObeliskDepths.LOGGER.debug(
                "Closed unentered dungeon session after final portal lease ended: session={}, instance={}",
                session.id(),
                instance.id()
        );
    }

    public static boolean isInsideSessionArea(
            ServerPlayer player,
            DungeonSession session
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        return isInsideSessionArea(level, player, session);
    }

    public static boolean isInsideSessionArea(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonSession session
    ) {
        if (!player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return false;
        }

        /*
         * TODO: Include corridor bounds, entry overlap bounds, boss arena bounds,
         * and any future transport-entity overlap area. For now, this uses the
         * best available site bounds from authoritative DungeonSite metadata.
         */
        return resolveSite(dungeonLevel, session.siteKey())
                .map(site -> site.bounds().contains(player.blockPosition()))
                .orElse(false);
    }

    public static boolean canAccessDungeon(
            ServerPlayer player,
            DungeonSession session
    ) {
        /*
         * Access is centralized even though the current policy is deliberately
         * open. Later, the obelisk/entry entity, reward chest, doors/controllers,
         * and dungeon blocks can all ask this one service instead of growing
         * separate rule copies.
         *
         * TODO: Integrate the future entry-entity overlap transport here.
         */
        UUID playerId = player.getUUID();

        if (session.starterPlayerId().equals(playerId)) {
            return true;
        }

        return switch (session.accessMode()) {
            case OPEN -> true;
            case STARTER_ONLY -> false;
            case ALLOWLIST -> session.isParticipant(playerId);
        };
    }

    public static boolean markBossKilled(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            Optional<BlockPos> chestPos
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = session.get().markBossKilled(chestPos);

        if (changed) {
            data.markSessionsDirty();
        }

        return changed;
    }

    public static boolean completeSession(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.findSessionByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = session.get().markCompleted();

        if (changed) {
            data.markSessionsDirty();
            DungeonSessionProgressBarService.removeSession(session.get().id());
        }

        return changed;
    }

    public static boolean openRewardChest(
            ServerPlayer player,
            UUID sessionId,
            BlockPos chestPos
    ) {
        if (!(player.level() instanceof ServerLevel dungeonLevel)) {
            return false;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session = data.getSession(sessionId);

        if (session.isEmpty() || !canAccessDungeon(player, session.get())) {
            return false;
        }

        DungeonSession value = session.get();

        return DungeonRewardService.tryOpenReward(
                player,
                value.instanceId(),
                chestPos
        ) == DungeonRewardClaimResult.SUCCESS;
    }

    public static int cleanupSessionsForInstance(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        int cleaned = 0;
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        for (DungeonSession session : data.sessions()) {
            if (!session.instanceId().equals(instanceId)) {
                continue;
            }

            cleanupSession(dungeonLevel, session);
            cleaned++;
        }

        return cleaned;
    }

    public static void abandonAndCleanup(
            ServerLevel dungeonLevel,
            DungeonSession session
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        if (!session.state().needsRuntimeTick()) {
            return;
        }

        if (session.markAbandoned()) {
            data.markSessionsDirty();
        }

        ObeliskDepths.LOGGER.debug(
                "Dungeon session abandoned: session={}, instance={}, starter={}, outsideTicks={}",
                session.id(),
                session.instanceId(),
                session.starterPlayerId(),
                dungeonLevel.getGameTime() - session.lastStarterInsideGameTime()
        );

        cleanupSession(dungeonLevel, session);
        DungeonRuntimeArtifactCleanupService.cleanupInstanceArtifacts(
                dungeonLevel,
                session.instanceId()
        );

        DungeonInstanceService.retireRuntimeInstance(
                dungeonLevel,
                session.instanceId(),
                DungeonSiteUsageStatus.ABANDONED
        );
    }

    public static void cleanupSession(
            ServerLevel dungeonLevel,
            DungeonSession session
    ) {
        DungeonEncounterDirector.cleanupInstance(
                dungeonLevel,
                session.instanceId(),
                DungeonMobResolution.CLEANED
        );
        int removedEntities = removeRegisteredEntities(dungeonLevel, session);
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        if (session.markCleaned()) {
            data.markSessionsDirty();
        }

        DungeonSessionProgressBarService.removeSession(session.id());

        /*
         * TODO: When dungeon transport is fully entity-overlap based, evict or
         * return remaining participants through that system during abandonment.
         */
        ObeliskDepths.LOGGER.debug(
                "Dungeon session cleaned: session={}, instance={}, removedEntities={}",
                session.id(),
                session.instanceId(),
                removedEntities
        );
    }

    private static int removeRegisteredEntities(
            ServerLevel dungeonLevel,
            DungeonSession session
    ) {
        /*
         * Compatibility-only cleanup for older saved sessions that recorded
         * spawnedEntityIds before DungeonEncounterDirector became the owner of
         * controlled mob lifetime. New encounter cleanup must use
         * DungeonEncounterDirector.cleanupInstance(...), called above.
         */
        int removed = 0;

        for (UUID entityId : session.spawnedEntityIds()) {
            Entity entity = dungeonLevel.getEntity(entityId);

            if (entity == null || !entity.isAlive()) {
                continue;
            }

            /*
             * Only entities explicitly spawned and registered by the dungeon
             * session are removed. This prevents cleanup from deleting unrelated
             * mobs that happen to be inside the structure.
             */
            entity.discard();
            removed++;
        }

        if (session.clearSpawnedEntityIds() > 0) {
            DungeonManagerSavedData.get(dungeonLevel).markSessionsDirty();
        }

        return removed;
    }

    private static Optional<DungeonSite> resolveSite(
            ServerLevel dungeonLevel,
            DungeonSiteKey siteKey
    ) {
        Optional<DungeonSite> generated =
                DungeonSiteProjectionCache.read(dungeonLevel, siteKey)
                        .map(ResolvedDungeonSite::site);

        if (generated.isPresent()) {
            return generated;
        }

        return DungeonManagerSavedData.get(dungeonLevel)
                .getSiteSnapshot(siteKey);
    }

    private static DungeonAccessMode fromPortalAccessMode(
            io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonAccessMode mode
    ) {
        return switch (mode) {
            case SOLO -> DungeonAccessMode.STARTER_ONLY;
            case PARTY_OPEN -> DungeonAccessMode.OPEN;
        };
    }
}
