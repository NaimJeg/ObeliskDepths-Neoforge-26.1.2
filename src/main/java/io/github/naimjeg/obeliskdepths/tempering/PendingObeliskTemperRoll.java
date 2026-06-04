package io.github.naimjeg.obeliskdepths.tempering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Objects;

public record PendingObeliskTemperRoll(
        Identifier pool,
        int rolls,
        boolean replaceExisting
) {
    public static final Codec<PendingObeliskTemperRoll> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC
                            .fieldOf("pool")
                            .forGetter(PendingObeliskTemperRoll::pool),

                    ExtraCodecs.POSITIVE_INT
                            .optionalFieldOf("rolls", 1)
                            .forGetter(PendingObeliskTemperRoll::rolls),

                    Codec.BOOL
                            .optionalFieldOf("replace_existing", false)
                            .forGetter(PendingObeliskTemperRoll::replaceExisting)
            ).apply(instance, PendingObeliskTemperRoll::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            PendingObeliskTemperRoll
            > STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC,
                    PendingObeliskTemperRoll::pool,

                    ByteBufCodecs.VAR_INT,
                    PendingObeliskTemperRoll::rolls,

                    ByteBufCodecs.BOOL,
                    PendingObeliskTemperRoll::replaceExisting,

                    PendingObeliskTemperRoll::new
            );

    public PendingObeliskTemperRoll {
        pool = Objects.requireNonNull(pool, "pool must not be null");
        rolls = Math.max(1, rolls);
    }
}