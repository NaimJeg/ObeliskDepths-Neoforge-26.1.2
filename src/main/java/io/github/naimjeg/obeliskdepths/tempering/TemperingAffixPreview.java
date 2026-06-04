package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public record TemperingAffixPreview(
        Identifier entryId,
        Component displayName,
        Component description,
        int weight
) {
}
