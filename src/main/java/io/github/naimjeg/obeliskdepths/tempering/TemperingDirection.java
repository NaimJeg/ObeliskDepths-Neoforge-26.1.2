package io.github.naimjeg.obeliskdepths.tempering;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum TemperingDirection {
    BALANCE(0, "balance"),
    EDGE(1, "edge"),
    GUARD(2, "guard"),
    ECHO(3, "echo");

    public static final Codec<TemperingDirection> CODEC =
            Codec.STRING.xmap(
                    TemperingDirection::bySerializedName,
                    TemperingDirection::serializedName
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TemperingDirection
            > STREAM_CODEC =
            StreamCodec.of(
                    (buffer, direction) -> ByteBufCodecs.VAR_INT.encode(
                            buffer,
                            direction.id()
                    ),
                    buffer -> byId(ByteBufCodecs.VAR_INT.decode(buffer))
            );

    private final int id;
    private final String serializedName;

    TemperingDirection(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return this.id;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public Component displayName() {
        return Component.translatable("tempering_direction.obeliskdepths." + this.serializedName);
    }

    public static TemperingDirection byId(int id) {
        for (TemperingDirection direction : values()) {
            if (direction.id == id) {
                return direction;
            }
        }

        return BALANCE;
    }

    public static TemperingDirection bySerializedName(String name) {
        if (name == null || name.isBlank()) {
            return BALANCE;
        }

        String normalized = name.toLowerCase(Locale.ROOT);

        for (TemperingDirection direction : values()) {
            if (direction.serializedName.equals(normalized)) {
                return direction;
            }
        }

        return BALANCE;
    }
}
