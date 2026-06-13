package io.github.naimjeg.obeliskdepths.network;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.menu.ObeliskTemperingMenu;
import io.github.naimjeg.obeliskdepths.tempering.TemperingAffixPreview;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

public record ClientboundTemperingDirectionStatePayload(
        int containerId,
        Optional<Identifier> selectedDirectionId,
        List<TemperingDirectionView> directions,
        List<TemperingAffixPreview> selectedPreviews
) implements CustomPacketPayload {
    public static final Type<ClientboundTemperingDirectionStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "tempering_direction_state"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ClientboundTemperingDirectionStatePayload
            > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientboundTemperingDirectionStatePayload::containerId,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC),
            ClientboundTemperingDirectionStatePayload::selectedDirectionId,
            TemperingDirectionView.STREAM_CODEC.apply(ByteBufCodecs.list()),
            ClientboundTemperingDirectionStatePayload::directions,
            TemperingAffixPreview.STREAM_CODEC.apply(ByteBufCodecs.list()),
            ClientboundTemperingDirectionStatePayload::selectedPreviews,
            ClientboundTemperingDirectionStatePayload::new
    );

    public ClientboundTemperingDirectionStatePayload {
        selectedDirectionId = selectedDirectionId == null
                ? Optional.empty()
                : selectedDirectionId;
        directions = directions == null
                ? List.of()
                : List.copyOf(directions);
        selectedPreviews = selectedPreviews == null
                ? List.of()
                : List.copyOf(selectedPreviews);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ClientboundTemperingDirectionStatePayload payload,
            IPayloadContext context
    ) {
        Player player = context.player();

        if (!(player.containerMenu instanceof ObeliskTemperingMenu menu)) {
            return;
        }

        if (menu.containerId != payload.containerId()) {
            return;
        }

        menu.applyDirectionStateFromServer(
                payload.selectedDirectionId().orElse(null),
                payload.directions(),
                payload.selectedPreviews()
        );
    }
}
