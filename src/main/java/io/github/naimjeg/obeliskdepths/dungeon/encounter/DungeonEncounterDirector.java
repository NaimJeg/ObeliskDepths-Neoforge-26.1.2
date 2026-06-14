package io.github.naimjeg.obeliskdepths.dungeon.encounter;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityData;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityTracker;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.lifecycle.DungeonEncounterFailureService;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidPlayers;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardService;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionProgressBarService;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/*
 * Instance-level dungeon encounter controller.
 *
 * Rooms are spatial metadata only. This director owns encounter phase,
 * controlled mob population, kill credit, boss transition, and encounter
 * cleanup for each active dungeon instance.
 */
public final class DungeonEncounterDirector {
    private static final int NORMAL_MOB_KILL_SCORE = 1;
    private static final long RECONCILE_INTERVAL_TICKS = 40L;
    private static final int MAX_SPAWN_ATTEMPTS_PER_TICK = 8;
    private static final long BASE_RETRY_BACKOFF_TICKS = 40L;
    private static final long MAX_RETRY_BACKOFF_TICKS = 20L * 15L;
    private static final int MAX_CONSECUTIVE_SPAWN_FAILURES = 10;
    public static final long MISSING_MOB_RECONCILE_TIMEOUT_TICKS = 20L * 60L;

    private DungeonEncounterDirector() {
    }

    public static void tick(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);

        data.forEachActiveInstance(instance ->
                tickInstance(level, data, instance)
        );
    }

    public static boolean resolveControlledMob(
            ServerLevel level,
            DungeonInstanceId instanceId,
            UUID entityId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter =
                data.findActiveEncounter(instanceId);

        if (encounter.isEmpty()) {
            return false;
        }

        return resolveControlledMob(level, data, encounter.get(), entityId, resolution);
    }

    public static boolean resolveControlledMob(
            ServerLevel level,
            DungeonRaidId encounterId,
            UUID entityId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter = data.getRaid(encounterId);

        if (encounter.isEmpty()) {
            return false;
        }

        return resolveControlledMob(level, data, encounter.get(), entityId, resolution);
    }

    public static void cleanupInstance(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        data.findActiveEncounter(instanceId)
                .ifPresent(encounter -> {
                    removeTrackedMobs(level, data, encounter, resolution);
                    encounter.markEncounterExpired();
                    data.markEncounterDirty();
                    data.findSessionByInstance(instanceId)
                            .ifPresent(session ->
                                    DungeonSessionProgressBarService.removeSession(session.id())
                            );
                });
    }

    public static boolean failInstance(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter = data.findEncounter(instanceId);

        if (encounter.isEmpty() || encounter.get().isTerminal()) {
            return false;
        }

        failEncounter(level, data, encounter.get(), resolution);
        return true;
    }

    public static boolean expireInstance(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter = data.findEncounter(instanceId);

        if (encounter.isEmpty() || encounter.get().isTerminal()) {
            return false;
        }

        expireEncounter(level, data, encounter.get(), resolution);
        return true;
    }

    public static boolean reconcileInstanceNow(
            ServerLevel level,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonInstance> instance = data.getInstance(instanceId);

        if (instance.isEmpty() || instance.get().status() != DungeonStatus.ACTIVE) {
            return false;
        }

        Optional<DungeonSite> site = resolveSite(level, data, instance.get());
        Optional<DungeonRaidInstance> encounter = data.findActiveEncounter(instanceId);

        if (site.isEmpty() || encounter.isEmpty() || !encounter.get().encounterPhase().active()) {
            return false;
        }

        reconcilePopulation(level, data, instance.get(), site.get(), encounter.get());
        return true;
    }

    private static void tickInstance(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        if (instance.status() != DungeonStatus.ACTIVE) {
            cleanupInstance(level, instance.id(), DungeonMobResolution.CLEANED);
            return;
        }

        Optional<DungeonSite> site = resolveSite(level, data, instance);

        if (site.isEmpty()) {
            return;
        }

        Optional<DungeonSession> session =
                data.findSessionByInstance(instance.id());

        if (session.isEmpty() || !session.get().state().needsRuntimeTick()) {
            return;
        }

        Optional<DungeonRaidInstance> existingEncounter = data.findEncounter(instance.id());

        if (existingEncounter.isPresent() && existingEncounter.get().isTerminal()) {
            return;
        }

        DungeonEncounterSettings encounterSettings =
                DungeonEncounterSettingsResolver.resolve(instance.difficulty());

        DungeonRaidInstance encounter = data.getOrCreateEncounter(
                instance.id(),
                BuiltinDungeonRaids.COMBAT_ROOM,
                encounterSettings.normalKillQuota(),
                encounterSettings.desiredLivingMobCount(),
                level.getGameTime()
        );
        if (encounter.initializeEncounterSettings(
                encounterSettings.normalKillQuota(),
                encounterSettings.desiredLivingMobCount()
        )) {
            data.markEncounterDirty();
        }

        if (migrateLegacySessionProgress(level, data, instance, encounter)) {
            ObeliskDepths.LOGGER.debug(
                    "Migrated legacy dungeon session progress into encounter: instance={}, encounter={}, credited={}, quota={}",
                    instance.id(),
                    encounter.id(),
                    encounter.creditedNormalKills(),
                    encounter.normalKillQuota()
            );
        }

        if (encounter.encounterPhase() == DungeonEncounterPhase.COMBAT
                && encounter.normalKillQuotaComplete()) {
            transitionToBoss(level, data, instance, encounter);
            return;
        }

        if (!encounter.encounterPhase().active()) {
            return;
        }

        if (DungeonRaidPlayers.findActivePlayersInDungeon(level, instance).isEmpty()) {
            suspendForNoPlayers(level, data, encounter);
            return;
        }

        if (level.getGameTime() < encounter.nextSpawnGameTime()) {
            return;
        }

        reconcilePopulation(level, data, instance, site.get(), encounter);
    }

    private static boolean resolveControlledMob(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            UUID entityId,
            DungeonMobResolution resolution
    ) {
        if (!encounter.resolveMob(entityId)) {
            return false;
        }

        Entity entity = level.getEntity(entityId);
        Optional<DungeonEntityData> entityData =
                entity == null ? Optional.empty() : DungeonEntityTracker.get(entity);
        DungeonEncounterMobRole role = entityData
                .flatMap(DungeonEntityData::mobRole)
                .orElse(encounter.currentMobRole());

        if (entity != null) {
            DungeonEntityTracker.clear(entity);
        }

        if (resolution == DungeonMobResolution.KILLED) {
            if (role == DungeonEncounterMobRole.NORMAL
                    && encounter.encounterPhase() == DungeonEncounterPhase.COMBAT) {
                encounter.creditNormalKill();
                data.findSessionByInstance(encounter.dungeonInstanceId())
                        .ifPresent(session -> DungeonSessionProgressBarService.updateSession(level, session));
                Optional.of(encounter)
                        .filter(DungeonRaidInstance::normalKillQuotaComplete)
                        .flatMap(ignored -> data.getInstance(encounter.dungeonInstanceId()))
                        .ifPresent(instance ->
                                transitionToBoss(level, data, instance, encounter)
                        );
            } else if (role == DungeonEncounterMobRole.BOSS
                    && encounter.encounterPhase() == DungeonEncounterPhase.BOSS) {
                completeBoss(level, data, encounter);
            }
        }

        data.markEncounterDirty();
        return true;
    }

    private static void reconcilePopulation(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonSite site,
            DungeonRaidInstance encounter
    ) {
        int living = pruneInvalidTrackedMobs(level, data, encounter);

        if (!encounter.encounterPhase().active()) {
            return;
        }

        int desired = encounter.encounterPhase() == DungeonEncounterPhase.BOSS
                ? 1
                : encounter.desiredLivingMobCount();
        int deficit = Math.max(0, desired - living);

        if (deficit <= 0) {
            encounter.setNextSpawnGameTime(level.getGameTime() + RECONCILE_INTERVAL_TICKS);
            data.markEncounterDirty();
            return;
        }

        int spawned = spawnDeficit(level, instance, site, encounter, deficit);

        if (spawned > 0) {
            encounter.clearSpawnFailure();
            encounter.setNextSpawnGameTime(level.getGameTime() + RECONCILE_INTERVAL_TICKS);
        } else {
            long backoff = Math.min(
                    MAX_RETRY_BACKOFF_TICKS,
                    BASE_RETRY_BACKOFF_TICKS * (encounter.spawnFailureCount() + 1L)
            );
            encounter.recordSpawnFailure(level.getGameTime() + backoff);
            if (encounter.spawnFailureCount() >= MAX_CONSECUTIVE_SPAWN_FAILURES) {
                ObeliskDepths.LOGGER.warn(
                        "Dungeon encounter spawn failed terminally: instance={}, encounter={}, room={}, phase={}, failureCount={}, nextAction=failEncounterAndCleanup",
                        instance.id(),
                        encounter.id(),
                        encounter.roomId().map(Object::toString).orElse("<instance>"),
                        encounter.encounterPhase().getSerializedName(),
                        encounter.spawnFailureCount()
                );
                failEncounter(level, data, encounter, DungeonMobResolution.INVALIDATED);
                return;
            }

            ObeliskDepths.LOGGER.debug(
                    "Dungeon encounter spawn deferred: instance={}, encounter={}, phase={}, failureCount={}, nextRetry={}",
                    instance.id(),
                    encounter.id(),
                    encounter.encounterPhase().getSerializedName(),
                    encounter.spawnFailureCount(),
                    encounter.nextSpawnGameTime()
            );
        }

        data.markEncounterDirty();
    }

    private static int pruneInvalidTrackedMobs(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter
    ) {
        int living = 0;

        for (UUID entityId : List.copyOf(encounter.trackedMobIds())) {
            Entity entity = level.getEntity(entityId);

            if (entity == null) {
                Optional<Long> missingSince = encounter.missingSinceGameTime(entityId);

                if (missingSince.isEmpty()) {
                    encounter.markMobTemporarilyMissing(entityId, level.getGameTime());
                    living++;
                    continue;
                }

                if (level.getGameTime() - missingSince.get()
                        < MISSING_MOB_RECONCILE_TIMEOUT_TICKS) {
                    living++;
                    continue;
                }

                encounter.resolveMob(entityId);
                continue;
            }

            if (!(entity instanceof Mob) || !entity.isAlive()) {
                encounter.resolveMob(entityId);
                continue;
            }

            Optional<DungeonEntityData> entityData = DungeonEntityTracker.get(entity);

            if (entityData.isEmpty()
                    || entityData.get().instanceId().isEmpty()
                    || !entityData.get().instanceId().get().equals(encounter.dungeonInstanceId())
                    || entityData.get().raidId().isEmpty()
                    || !entityData.get().raidId().get().equals(encounter.id())) {
                DungeonEntityTracker.clear(entity);
                entity.discard();
                encounter.resolveMob(entityId);
                continue;
            }

            encounter.clearMissingMob(entityId);
            living++;
        }

        data.markEncounterDirty();
        return living;
    }

    private static int spawnDeficit(
            ServerLevel level,
            DungeonInstance instance,
            DungeonSite site,
            DungeonRaidInstance encounter,
            int deficit
    ) {
        List<DungeonGeneratedRoom> rooms = spawnRooms(site, encounter.encounterPhase());

        if (rooms.isEmpty()) {
            return 0;
        }

        int spawned = 0;
        int attempts = 0;

        while (spawned < deficit && attempts < MAX_SPAWN_ATTEMPTS_PER_TICK) {
            DungeonGeneratedRoom room = rooms.get(attempts % rooms.size());
            Optional<BlockPos> spawnPos =
                    DungeonSpawnPositionResolver.findSpawnPos(
                            room,
                            level,
                            attempts + encounter.trackedMobIds().size()
                    );

            attempts++;

            if (spawnPos.isEmpty()) {
                continue;
            }

            Mob mob = entityTypeFor(encounter.currentMobRole(), attempts)
                    .spawn(level, spawnPos.get(), EntitySpawnReason.TRIGGERED);

            if (mob == null) {
                continue;
            }

            mob.setPersistenceRequired();
            DungeonEntityTracker.bindControlledMob(
                    mob,
                    instance.id(),
                    encounter.id(),
                    encounter.currentMobRole()
            );
            encounter.trackMob(mob.getUUID());
            spawned++;
        }

        return spawned;
    }

    private static List<DungeonGeneratedRoom> spawnRooms(
            DungeonSite site,
            DungeonEncounterPhase phase
    ) {
        if (phase == DungeonEncounterPhase.BOSS) {
            return site.roomsOfType(DungeonRoomType.BOSS);
        }

        return site.rooms()
                .stream()
                .filter(room -> room.type() != DungeonRoomType.START)
                .filter(room -> room.type() != DungeonRoomType.BOSS)
                .sorted(Comparator.comparing(room -> room.id().value()))
                .toList();
    }

    private static EntityType<? extends Mob> entityTypeFor(
            DungeonEncounterMobRole role,
            int sequence
    ) {
        if (role == DungeonEncounterMobRole.BOSS) {
            return EntityType.ZOMBIE;
        }

        return sequence % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON;
    }

    private static void transitionToBoss(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonRaidInstance encounter
    ) {
        if (!encounter.setEncounterPhase(DungeonEncounterPhase.BOSS)) {
            return;
        }

        removeTrackedMobs(level, data, encounter, DungeonMobResolution.CLEANED);
        data.unlockBossRooms(instance.id());

        for (var roomState : data.roomStates(instance.id())) {
            if (roomState.type() == DungeonRoomType.COMBAT) {
                data.setRoomStatus(
                        instance.id(),
                        roomState.roomId(),
                        DungeonRoomStatus.CLEARED
                );
            }
        }

        data.findSessionByInstance(instance.id())
                .ifPresent(session ->
                        DungeonSessionProgressBarService.removeSession(session.id())
                );
        encounter.setNextSpawnGameTime(level.getGameTime());
        data.markEncounterDirty();

        ObeliskDepths.LOGGER.debug(
                "Dungeon encounter transitioned to boss: instance={}, encounter={}",
                instance.id(),
                encounter.id()
        );
    }

    private static void completeBoss(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter
    ) {
        if (!encounter.markBossCompleted()) {
            return;
        }

        encounter.markEncounterComplete();
        removeTrackedMobs(level, data, encounter, DungeonMobResolution.CLEANED);

        for (var roomState : data.roomStates(encounter.dungeonInstanceId())) {
            if (roomState.type() == DungeonRoomType.BOSS) {
                data.setRoomStatus(
                        encounter.dungeonInstanceId(),
                        roomState.roomId(),
                        DungeonRoomStatus.CLEARED
                );
            }
        }

        DungeonSessionManager.markBossKilled(
                level,
                encounter.dungeonInstanceId(),
                Optional.empty()
        );
        DungeonRewardService.onBossDefeated(level, encounter.dungeonInstanceId());
        DungeonInstanceService.setStatus(
                level,
                encounter.dungeonInstanceId(),
                DungeonStatus.REWARD_PHASE
        );
        DungeonSessionManager.completeSession(level, encounter.dungeonInstanceId());
        data.markEncounterDirty();

        ObeliskDepths.LOGGER.debug(
                "Dungeon encounter completed: instance={}, encounter={}",
                encounter.dungeonInstanceId(),
                encounter.id()
        );
    }

    private static void suspendForNoPlayers(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter
    ) {
        if (encounter.trackedMobIds().isEmpty()) {
            return;
        }

        removeTrackedMobs(level, data, encounter, DungeonMobResolution.CLEANED);
        encounter.setNextSpawnGameTime(level.getGameTime() + RECONCILE_INTERVAL_TICKS);
        data.markEncounterDirty();
    }

    private static void expireEncounter(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            DungeonMobResolution resolution
    ) {
        removeTrackedMobs(level, data, encounter, resolution);
        encounter.markEncounterExpired();
        data.findSessionByInstance(encounter.dungeonInstanceId())
                .ifPresent(session ->
                        DungeonSessionProgressBarService.removeSession(session.id())
                );
        data.markEncounterDirty();
    }

    private static void failEncounter(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            DungeonMobResolution resolution
    ) {
        removeTrackedMobs(level, data, encounter, resolution);
        DungeonEncounterFailureService.failInstanceForEncounter(
                level,
                data,
                encounter,
                "encounter_spawn_or_reconciliation_failure"
        );
    }

    private static void removeTrackedMobs(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            DungeonMobResolution resolution
    ) {
        int removed = 0;
        int resolved = 0;

        for (UUID entityId : List.copyOf(encounter.trackedMobIds())) {
            Entity entity = level.getEntity(entityId);

            if (entity != null) {
                DungeonEntityTracker.clear(entity);
                entity.discard();
                removed++;
            } else {
                encounter.markCleanupPending(entityId);
            }

            if (encounter.resolveMob(entityId)) {
                resolved++;
            }
        }

        if (removed > 0 || resolved > 0) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon encounter removed tracked mobs: instance={}, encounter={}, resolution={}, removed={}, resolved={}",
                    encounter.dungeonInstanceId(),
                    encounter.id(),
                    resolution,
                    removed,
                    resolved
            );
        }

        data.markEncounterDirty();
    }

    private static Optional<DungeonSite> resolveSite(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        Optional<DungeonSite> generated =
                DungeonSiteProjectionCache.read(level, instance.siteKey())
                        .map(ResolvedDungeonSite::site);

        if (generated.isPresent()) {
            return generated;
        }

        return data.getSiteSnapshot(instance.siteKey());
    }

    private static boolean migrateLegacySessionProgress(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonRaidInstance encounter
    ) {
        Optional<DungeonSession> session = data.findSessionByInstance(instance.id());

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = encounter.migrateCreditedNormalKills(
                session.get().progress().clampedCurrentKillScore()
        );

        if (changed) {
            data.markEncounterDirty();
            DungeonSessionProgressBarService.updateSession(level, session.get());
        }

        return changed;
    }

    public static int fixedNormalKillQuota(DungeonDifficulty difficulty) {
        return DungeonEncounterSettingsResolver.fixedNormalKillQuota(difficulty);
    }

    public static int desiredLivingMobCount(DungeonDifficulty difficulty) {
        return DungeonEncounterSettingsResolver.desiredLivingMobCount(difficulty);
    }
}
