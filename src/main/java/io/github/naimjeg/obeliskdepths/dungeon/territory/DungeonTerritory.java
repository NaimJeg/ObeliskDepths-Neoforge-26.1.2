package io.github.naimjeg.obeliskdepths.dungeon.territory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public record DungeonTerritory(
        DungeonTerritoryId id,
        DungeonInstanceId instanceId,
        DungeonBounds bounds,
        BlockPos startPos
) {
    public static final Codec<DungeonTerritory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DungeonTerritoryId.CODEC.fieldOf("id").forGetter(DungeonTerritory::id),
            DungeonInstanceId.CODEC.optionalFieldOf("instance_id")
                    .forGetter(territory -> Optional.ofNullable(territory.instanceId())),
            DungeonBounds.CODEC.fieldOf("bounds").forGetter(DungeonTerritory::bounds),
            BlockPos.CODEC.fieldOf("start_pos").forGetter(DungeonTerritory::startPos)
    ).apply(instance, (id, instanceId, bounds, startPos) ->
            new DungeonTerritory(id, instanceId.orElse(null), bounds, startPos)
    ));

    public DungeonTerritory withInstanceId(DungeonInstanceId instanceId) {
        return new DungeonTerritory(this.id, instanceId, this.bounds, this.startPos);
    }
}