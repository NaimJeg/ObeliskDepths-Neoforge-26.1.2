package io.github.naimjeg.obeliskdepths.dungeon.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class DungeonRaidInstance {
    public static final Codec<DungeonRaidInstance> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonRaidId.CODEC.fieldOf("id")
                            .forGetter(DungeonRaidInstance::id),

                    DungeonInstanceId.CODEC.fieldOf("dungeon_instance_id")
                            .forGetter(DungeonRaidInstance::dungeonInstanceId),

                    DungeonRoomId.CODEC.optionalFieldOf("room_id")
                            .forGetter(DungeonRaidInstance::roomId),

                    Identifier.CODEC.fieldOf("raid_type")
                            .forGetter(DungeonRaidInstance::raidType),

                    DungeonRaidStatus.CODEC
                            .optionalFieldOf("status", DungeonRaidStatus.PREPARING)
                            .forGetter(DungeonRaidInstance::status),

                    Codec.INT.optionalFieldOf("current_wave", 0)
                            .forGetter(DungeonRaidInstance::currentWave),

                    Codec.LONG.optionalFieldOf("next_wave_game_time", 0L)
                            .forGetter(DungeonRaidInstance::nextWaveGameTime),

                    Codec.INT.optionalFieldOf("spawned_mob_count", 0)
                            .forGetter(DungeonRaidInstance::spawnedMobCount),

                    Codec.INT.optionalFieldOf("killed_mob_count", 0)
                            .forGetter(DungeonRaidInstance::killedMobCount)
            ).apply(instance, DungeonRaidInstance::new));

    private final DungeonRaidId id;
    private final DungeonInstanceId dungeonInstanceId;
    private final Optional<DungeonRoomId> roomId;
    private final Identifier raidType;

    private DungeonRaidStatus status;
    private int currentWave;
    private long nextWaveGameTime;
    private int spawnedMobCount;
    private int killedMobCount;

    public DungeonRaidInstance(
            DungeonRaidId id,
            DungeonInstanceId dungeonInstanceId,
            Optional<DungeonRoomId> roomId,
            Identifier raidType,
            DungeonRaidStatus status,
            int currentWave,
            long nextWaveGameTime,
            int spawnedMobCount,
            int killedMobCount
    ) {
        this.id = id;
        this.dungeonInstanceId = dungeonInstanceId;
        this.roomId = roomId;
        this.raidType = raidType;
        this.status = status;
        this.currentWave = currentWave;
        this.nextWaveGameTime = nextWaveGameTime;
        this.spawnedMobCount = spawnedMobCount;
        this.killedMobCount = killedMobCount;
    }

    public static DungeonRaidInstance createRoomRaid(
            DungeonInstanceId dungeonInstanceId,
            DungeonRoomId roomId,
            Identifier raidType,
            long gameTime
    ) {
        return createRoomRaid(
                dungeonInstanceId,
                Optional.of(roomId),
                raidType,
                gameTime
        );
    }

    private static DungeonRaidInstance createRoomRaid(
            DungeonInstanceId dungeonInstanceId,
            Optional<DungeonRoomId> roomId,
            Identifier raidType,
            long gameTime
    ) {
        return new DungeonRaidInstance(
                DungeonRaidId.create(),
                dungeonInstanceId,
                roomId,
                raidType,
                DungeonRaidStatus.PREPARING,
                0,
                gameTime + 100L,
                0,
                0
        );
    }

    public DungeonRaidId id() {
        return this.id;
    }

    public DungeonInstanceId dungeonInstanceId() {
        return this.dungeonInstanceId;
    }

    public Optional<DungeonRoomId> roomId() {
        return this.roomId;
    }

    public Identifier raidType() {
        return this.raidType;
    }

    public DungeonRaidStatus status() {
        return this.status;
    }

    public int currentWave() {
        return this.currentWave;
    }

    public long nextWaveGameTime() {
        return this.nextWaveGameTime;
    }

    public int spawnedMobCount() {
        return this.spawnedMobCount;
    }

    public int killedMobCount() {
        return this.killedMobCount;
    }

    public boolean setStatus(DungeonRaidStatus status) {
        if (this.status == status) {
            return false;
        }

        this.status = status;
        return true;
    }

    public void advanceWave(long nextWaveGameTime) {
        this.currentWave++;
        this.nextWaveGameTime = nextWaveGameTime;
    }

    public void markMobSpawned() {
        this.spawnedMobCount++;
    }

    public void markMobKilled() {
        this.killedMobCount++;
    }

    public boolean isTerminal() {
        return this.status == DungeonRaidStatus.WON
                || this.status == DungeonRaidStatus.FAILED
                || this.status == DungeonRaidStatus.EXPIRED;
    }
}