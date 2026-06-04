package io.github.naimjeg.obeliskdepths.dungeon.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import net.minecraft.core.BlockPos;

import java.util.*;

public final class PortalSession {
    public static final Codec<PortalSession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PortalSessionId.CODEC.fieldOf("id").forGetter(PortalSession::id),
            DungeonInstanceId.CODEC.fieldOf("instance_id").forGetter(PortalSession::instanceId),
            DungeonCodecs.UUID_CODEC.fieldOf("opener").forGetter(PortalSession::opener),
            BlockPos.CODEC.fieldOf("obelisk_pos").forGetter(PortalSession::obeliskPos),
            DungeonAccessMode.CODEC.fieldOf("access_mode").forGetter(PortalSession::accessMode),
            Codec.LONG.fieldOf("expires_at_game_time").forGetter(PortalSession::expiresAtGameTime),
            DungeonCodecs.UUID_CODEC.listOf()
                    .optionalFieldOf("participants", List.of())
                    .forGetter(session -> List.copyOf(session.participants))
    ).apply(instance, PortalSession::fromCodec));

    private final PortalSessionId id;
    private final DungeonInstanceId instanceId;
    private final UUID opener;
    private final BlockPos obeliskPos;
    private final DungeonAccessMode accessMode;
    private final Set<UUID> participants = new HashSet<>();
    private final long expiresAtGameTime;

    public PortalSession(
            PortalSessionId id,
            DungeonInstanceId instanceId,
            UUID opener,
            BlockPos obeliskPos,
            DungeonAccessMode accessMode,
            long expiresAtGameTime
    ) {
        this.id = id;
        this.instanceId = instanceId;
        this.opener = opener;
        this.obeliskPos = obeliskPos.immutable();
        this.accessMode = accessMode;
        this.expiresAtGameTime = expiresAtGameTime;
    }

    private static PortalSession fromCodec(
            PortalSessionId id,
            DungeonInstanceId instanceId,
            UUID opener,
            BlockPos obeliskPos,
            DungeonAccessMode accessMode,
            long expiresAtGameTime,
            List<UUID> participants
    ) {
        PortalSession session = new PortalSession(
                id,
                instanceId,
                opener,
                obeliskPos,
                accessMode,
                expiresAtGameTime
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

    public BlockPos obeliskPos() {
        return this.obeliskPos;
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
}