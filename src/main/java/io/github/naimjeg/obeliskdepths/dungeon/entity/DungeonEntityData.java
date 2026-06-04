package io.github.naimjeg.obeliskdepths.dungeon.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;

import java.util.Optional;

public record DungeonEntityData(
        Optional<DungeonInstanceId> instanceId,
        Optional<DungeonRaidId> raidId,
        int wave,
        int ticksOutsideDungeon
) {
    public static final MapCodec<DungeonEntityData> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DungeonInstanceId.CODEC.optionalFieldOf("instance_id")
                            .forGetter(DungeonEntityData::instanceId),
                    DungeonRaidId.CODEC.optionalFieldOf("raid_id")
                            .forGetter(DungeonEntityData::raidId),
                    Codec.INT.optionalFieldOf("wave", 0)
                            .forGetter(DungeonEntityData::wave),
                    Codec.INT.optionalFieldOf("ticks_outside_dungeon", 0)
                            .forGetter(DungeonEntityData::ticksOutsideDungeon)
            ).apply(instance, DungeonEntityData::new));

    public static final Codec<DungeonEntityData> CODEC =
            MAP_CODEC.codec();

    public static DungeonEntityData empty() {
        return new DungeonEntityData(
                Optional.empty(),
                Optional.empty(),
                0,
                0
        );
    }

    public static DungeonEntityData raidMob(
            DungeonInstanceId instanceId,
            DungeonRaidId raidId,
            int wave
    ) {
        return new DungeonEntityData(
                Optional.of(instanceId),
                Optional.of(raidId),
                wave,
                0
        );
    }

    public DungeonEntityData withTicksOutsideDungeon(int ticks) {
        return new DungeonEntityData(
                this.instanceId,
                this.raidId,
                this.wave,
                ticks
        );
    }

    public boolean isEmpty() {
        return this.instanceId.isEmpty()
                && this.raidId.isEmpty()
                && this.wave == 0
                && this.ticksOutsideDungeon == 0;
    }
}