package io.github.naimjeg.obeliskdepths.dungeon.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

public final class PortalSession {
    private static final ResourceKey<Level> LEGACY_MISSING_SOURCE_DIMENSION =
            ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("obeliskdepths", "legacy_missing_source_dimension")
            );

    public static final Codec<PortalSession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PortalSessionId.CODEC.fieldOf("id").forGetter(PortalSession::id),
            DungeonInstanceId.CODEC.fieldOf("instance_id").forGetter(PortalSession::instanceId),
            DungeonCodecs.UUID_CODEC.fieldOf("opener").forGetter(PortalSession::opener),
            ResourceKey.codec(Registries.DIMENSION)
                    .optionalFieldOf("source_dimension")
                    .forGetter(session -> session.legacyMissingSourceDimension
                            ? Optional.empty()
                            : Optional.of(session.sourceDimension)),
            BlockPos.CODEC.fieldOf("obelisk_pos").forGetter(PortalSession::obeliskPos),
            BlockPos.CODEC.optionalFieldOf("portal_anchor_pos")
                    .forGetter(session -> session.legacyMissingSourceDimension
                            ? Optional.empty()
                            : Optional.of(session.portalAnchorPos)),
            DungeonAccessMode.CODEC.fieldOf("access_mode").forGetter(PortalSession::accessMode),
            Codec.LONG.fieldOf("expires_at_game_time").forGetter(PortalSession::expiresAtGameTime),
            DungeonCodecs.UUID_CODEC.listOf()
                    .optionalFieldOf("participants", List.of())
                    .forGetter(session -> List.copyOf(session.participants))
    ).apply(instance, PortalSession::fromCodec));

    private final PortalSessionId id;
    private final DungeonInstanceId instanceId;
    private final UUID opener;
    private final ResourceKey<Level> sourceDimension;
    private final BlockPos obeliskPos;
    private final BlockPos portalAnchorPos;
    private final DungeonAccessMode accessMode;
    private final Set<UUID> participants = new HashSet<>();
    private final long expiresAtGameTime;
    private final boolean legacyMissingSourceDimension;

    public PortalSession(
            PortalSessionId id,
            DungeonInstanceId instanceId,
            UUID opener,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos,
            BlockPos portalAnchorPos,
            DungeonAccessMode accessMode,
            long expiresAtGameTime
    ) {
        this(id, instanceId, opener, sourceDimension, obeliskPos, portalAnchorPos, accessMode, expiresAtGameTime, false);
    }

    private PortalSession(
            PortalSessionId id,
            DungeonInstanceId instanceId,
            UUID opener,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos,
            BlockPos portalAnchorPos,
            DungeonAccessMode accessMode,
            long expiresAtGameTime,
            boolean legacyMissingSourceDimension
    ) {
        this.id = id;
        this.instanceId = instanceId;
        this.opener = opener;
        this.sourceDimension = sourceDimension;
        this.obeliskPos = obeliskPos.immutable();
        this.portalAnchorPos = portalAnchorPos.immutable();
        this.accessMode = accessMode;
        this.expiresAtGameTime = expiresAtGameTime;
        this.legacyMissingSourceDimension = legacyMissingSourceDimension;
    }

    private static PortalSession fromCodec(
            PortalSessionId id,
            DungeonInstanceId instanceId,
            UUID opener,
            Optional<ResourceKey<Level>> sourceDimension,
            BlockPos obeliskPos,
            Optional<BlockPos> portalAnchorPos,
            DungeonAccessMode accessMode,
            long expiresAtGameTime,
            List<UUID> participants
    ) {
        boolean legacyMissingSource = sourceDimension.isEmpty() || portalAnchorPos.isEmpty();
        PortalSession session = new PortalSession(
                id,
                instanceId,
                opener,
                sourceDimension.orElse(LEGACY_MISSING_SOURCE_DIMENSION),
                obeliskPos,
                portalAnchorPos.orElse(obeliskPos),
                accessMode,
                expiresAtGameTime,
                legacyMissingSource
        );

        session.participants.addAll(participants);
        return session;
    }

    public PortalSessionId id() {
        return this.id;
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public UUID opener() {
        return this.opener;
    }

    public ResourceKey<Level> sourceDimension() {
        return this.sourceDimension;
    }

    public BlockPos obeliskPos() {
        return this.obeliskPos;
    }

    public BlockPos portalAnchorPos() {
        return this.portalAnchorPos;
    }

    public DungeonAccessMode accessMode() {
        return this.accessMode;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(this.participants);
    }

    public boolean addParticipant(UUID playerId) {
        return this.participants.add(playerId);
    }

    public boolean removeParticipant(UUID playerId) {
        return this.participants.remove(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return this.participants.contains(playerId);
    }

    public long expiresAtGameTime() {
        return this.expiresAtGameTime;
    }

    public boolean isExpired(long gameTime) {
        return gameTime >= this.expiresAtGameTime;
    }

    public boolean hasValidSourceIdentity() {
        return !this.legacyMissingSourceDimension;
    }
}
