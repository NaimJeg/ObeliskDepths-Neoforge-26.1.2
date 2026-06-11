package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/*
 * Runtime sessions are intentionally separate from worldgen DungeonSite metadata.
 * DungeonSite describes generated physical layout; DungeonSession describes one
 * active run. Cleanup removes runtime entities/state only and must not delete
 * worldgen-owned blocks.
 */
public final class DungeonSession {
    public static final Codec<DungeonSession> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonCodecs.UUID_CODEC.fieldOf("id")
                            .forGetter(DungeonSession::id),
                    DungeonInstanceId.CODEC.fieldOf("instance_id")
                            .forGetter(DungeonSession::instanceId),
                    DungeonCodecs.UUID_CODEC.fieldOf("starter_player_id")
                            .forGetter(DungeonSession::starterPlayerId),
                    DungeonSiteKey.CODEC.fieldOf("site_key")
                            .forGetter(DungeonSession::siteKey),
                    DungeonSessionState.CODEC
                            .optionalFieldOf("state", DungeonSessionState.ACTIVE)
                            .forGetter(DungeonSession::state),
                    DungeonAccessMode.CODEC
                            .optionalFieldOf("access_mode", DungeonAccessMode.OPEN)
                            .forGetter(DungeonSession::accessMode),
                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("participants", List.of())
                            .forGetter(session -> List.copyOf(session.participants)),
                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("physical_participants", List.of())
                            .forGetter(session -> List.copyOf(session.physicalParticipants)),
                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("spawned_entity_ids", List.of())
                            .forGetter(session -> List.copyOf(session.spawnedEntityIds)),
                    DungeonKillProgress.CODEC
                            .optionalFieldOf("progress", DungeonKillProgress.empty())
                            .forGetter(DungeonSession::progress),
                    DungeonRewardState.CODEC
                            .optionalFieldOf("reward_state", DungeonRewardState.empty())
                            .forGetter(DungeonSession::rewardState),
                    Codec.LONG.optionalFieldOf("created_at_game_time", 0L)
                            .forGetter(DungeonSession::createdAtGameTime),
                    Codec.LONG.optionalFieldOf("last_starter_inside_game_time", 0L)
                            .forGetter(DungeonSession::lastStarterInsideGameTime),
                    Codec.BOOL.optionalFieldOf("tribute_bonus_active", false)
                            .forGetter(DungeonSession::tributeBonusActive)
            ).apply(instance, DungeonSession::new));

    private final UUID id;
    private final DungeonInstanceId instanceId;
    private final UUID starterPlayerId;
    private final DungeonSiteKey siteKey;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> physicalParticipants = new HashSet<>();
    private final Set<UUID> spawnedEntityIds = new HashSet<>();
    private final long createdAtGameTime;

    private DungeonSessionState state;
    private DungeonAccessMode accessMode;
    private DungeonKillProgress progress;
    private DungeonRewardState rewardState;
    private long lastStarterInsideGameTime;
    private boolean tributeBonusActive;

    public DungeonSession(
            UUID id,
            DungeonInstanceId instanceId,
            UUID starterPlayerId,
            DungeonSiteKey siteKey,
            DungeonSessionState state,
            DungeonAccessMode accessMode,
            Collection<UUID> participants,
            Collection<UUID> physicalParticipants,
            Collection<UUID> spawnedEntityIds,
            DungeonKillProgress progress,
            DungeonRewardState rewardState,
            long createdAtGameTime,
            long lastStarterInsideGameTime,
            boolean tributeBonusActive
    ) {
        this.id = requireNonNull(id, "session id");
        this.instanceId = requireNonNull(instanceId, "instance id");
        this.starterPlayerId = requireNonNull(starterPlayerId, "starter player id");
        this.siteKey = requireNonNull(siteKey, "site key");
        this.state = state == null ? DungeonSessionState.ACTIVE : state;
        this.accessMode = accessMode == null ? DungeonAccessMode.OPEN : accessMode;
        this.progress = progress == null ? DungeonKillProgress.empty() : progress;
        this.rewardState = rewardState == null ? DungeonRewardState.empty() : rewardState;
        this.createdAtGameTime = createdAtGameTime;
        this.lastStarterInsideGameTime = lastStarterInsideGameTime;
        this.tributeBonusActive = tributeBonusActive;

        if (participants != null) {
            this.participants.addAll(participants);
        }

        if (physicalParticipants != null) {
            this.physicalParticipants.addAll(physicalParticipants);
        }

        if (spawnedEntityIds != null) {
            this.spawnedEntityIds.addAll(spawnedEntityIds);
        }

        this.participants.add(starterPlayerId);
    }

    public static DungeonSession create(
            DungeonInstance instance,
            UUID starterPlayerId,
            DungeonAccessMode accessMode,
            boolean tributeBonusActive,
            long gameTime
    ) {
        return new DungeonSession(
                UUID.randomUUID(),
                instance.id(),
                starterPlayerId,
                instance.siteKey(),
                DungeonSessionState.ACTIVE,
                accessMode,
                Set.of(starterPlayerId),
                Set.of(starterPlayerId),
                Set.of(),
                DungeonKillProgress.empty(),
                DungeonRewardState.empty(),
                gameTime,
                gameTime,
                tributeBonusActive
        );
    }

    public UUID id() {
        return this.id;
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public UUID starterPlayerId() {
        return this.starterPlayerId;
    }

    public DungeonSiteKey siteKey() {
        return this.siteKey;
    }

    public DungeonSessionState state() {
        return this.state;
    }

    public DungeonAccessMode accessMode() {
        return this.accessMode;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(this.participants);
    }

    public Set<UUID> physicalParticipants() {
        return Collections.unmodifiableSet(this.physicalParticipants);
    }

    public Set<UUID> spawnedEntityIds() {
        return Collections.unmodifiableSet(this.spawnedEntityIds);
    }

    public DungeonKillProgress progress() {
        return this.progress;
    }

    public DungeonRewardState rewardState() {
        return this.rewardState;
    }

    public long createdAtGameTime() {
        return this.createdAtGameTime;
    }

    public long lastStarterInsideGameTime() {
        return this.lastStarterInsideGameTime;
    }

    public boolean tributeBonusActive() {
        return this.tributeBonusActive;
    }

    public boolean registerParticipant(UUID playerId) {
        return this.participants.add(playerId);
    }

    public boolean registerPhysicalParticipant(UUID playerId) {
        return this.physicalParticipants.add(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return this.participants.contains(playerId);
    }

    public boolean isPhysicalParticipant(UUID playerId) {
        return this.physicalParticipants.contains(playerId);
    }

    public boolean setState(DungeonSessionState state) {
        if (this.state == state) {
            return false;
        }

        this.state = state;
        return true;
    }

    public boolean setAccessMode(DungeonAccessMode accessMode) {
        if (this.accessMode == accessMode) {
            return false;
        }

        this.accessMode = accessMode;
        return true;
    }

    public boolean markStarterInside(long gameTime) {
        boolean changed = this.lastStarterInsideGameTime != gameTime;
        this.lastStarterInsideGameTime = gameTime;

        if (this.state == DungeonSessionState.ABANDON_PENDING) {
            this.state = DungeonSessionState.ACTIVE;
            changed = true;
        }

        return changed;
    }

    public boolean markAbandonPending() {
        return setState(DungeonSessionState.ABANDON_PENDING);
    }

    public boolean markAbandoned() {
        boolean changed = setState(DungeonSessionState.ABANDONED);

        if (this.tributeBonusActive) {
            this.tributeBonusActive = false;
            changed = true;
        }

        return changed;
    }

    public boolean markCleaned() {
        boolean changed = setState(DungeonSessionState.CLEANED);

        if (this.tributeBonusActive) {
            this.tributeBonusActive = false;
            changed = true;
        }

        return changed;
    }

    public boolean markCompleted() {
        boolean changed = setState(DungeonSessionState.COMPLETED);

        if (this.tributeBonusActive) {
            this.tributeBonusActive = false;
            changed = true;
        }

        return changed;
    }

    public boolean registerSpawnedEntity(
            UUID entityId,
            int killScore
    ) {
        if (!this.spawnedEntityIds.add(entityId)) {
            return false;
        }

        this.progress = this.progress.withAdditionalRequiredKillScore(killScore);
        return true;
    }

    public boolean markSpawnedEntityKilled(
            UUID entityId,
            int killScore
    ) {
        if (!this.spawnedEntityIds.remove(entityId)) {
            return false;
        }

        this.progress = this.progress.withAddedKillScore(killScore);
        return true;
    }

    public int clearSpawnedEntityIds() {
        int count = this.spawnedEntityIds.size();
        this.spawnedEntityIds.clear();
        return count;
    }

    public boolean markBossKilled(Optional<BlockPos> chestPos) {
        DungeonRewardState next = this.rewardState.withBossKilled(chestPos);

        if (next.equals(this.rewardState)) {
            return false;
        }

        this.rewardState = next;
        return true;
    }

    public boolean markRewardChestOpened() {
        if (this.rewardState.chestState() != DungeonRewardChestState.SPAWNED) {
            return false;
        }

        this.rewardState = this.rewardState.withOpenedChest();
        return true;
    }

    private static <T> T requireNonNull(
            T value,
            String name
    ) {
        if (value == null) {
            throw new IllegalArgumentException("Dungeon session requires " + name);
        }

        return value;
    }
}
