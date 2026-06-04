package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.display.DisplayText;
import io.github.naimjeg.damagenexus.api.enums.DamageApplicationBucket;
import io.github.naimjeg.damagenexus.api.enums.DamageChannel;
import io.github.naimjeg.damagenexus.api.enums.DamagePhase;
import io.github.naimjeg.damagenexus.api.rule.DamageNexusOperations;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleDefinition;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleRole;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleStacking;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDisplay;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntrySlot;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryStacking;
import io.github.naimjeg.damagenexus.builtin.rule.condition.AlwaysCondition;
import io.github.naimjeg.damagenexus.builtin.rule.condition.IsCriticalCondition;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public final class ObeliskTemperingEntryFactory {

    /*
     * These are DamageNexus built-in pre-multiplier bucket IDs.
     * Keep them as Identifiers here instead of importing DN internal registry classes.
     */
    private static final Identifier DN_FIRE_DAMAGE_BUCKET =
            Identifier.fromNamespaceAndPath("damagenexus", "fire_damage");

    private static final Identifier DN_CRIT_DAMAGE_BUCKET =
            Identifier.fromNamespaceAndPath("damagenexus", "crit_damage");
    private static final Identifier FIRE_TEMPERING_ENTRY =
            id("tempering/fire_edge");
    private static final Identifier CRIT_TEMPERING_ENTRY =
            id("tempering/critical_edge");

    private ObeliskTemperingEntryFactory() {
    }

    public static DamageEntryDefinition createFireTemperingEntry() {
        return new DamageEntryDefinition(
                FIRE_TEMPERING_ENTRY,
                display(
                        "fire_edge",
                        "Fire Edge",
                        List.of(
                                "+4 fire damage",
                                "+15% fire damage scaling"
                        ),
                        "A tempering mark that burns through the weapon edge.",
                        true
                ),
                DamageEntrySlot.WEAPON,
                List.of(
                        fireBaseDamageRule(),
                        fireScalingRule()
                ),
                DamageEntryStacking.UNIQUE_GROUP,
                Optional.of(id("tempering/fire"))
        );
    }

    public static DamageEntryDefinition createCritTemperingEntry() {
        return new DamageEntryDefinition(
                CRIT_TEMPERING_ENTRY,
                display(
                        "critical_edge",
                        "Critical Edge",
                        List.of(
                                "+20% physical damage on critical hits"
                        ),
                        "A tempering mark that rewards clean decisive strikes.",
                        true
                ),
                DamageEntrySlot.WEAPON,
                List.of(
                        criticalPhysicalScalingRule()
                ),
                DamageEntryStacking.UNIQUE_GROUP,
                Optional.of(id("tempering/critical"))
        );
    }

    public static Optional<DamageEntryDefinition> createById(
            Identifier entryId
    ) {
        if (FIRE_TEMPERING_ENTRY.equals(entryId)) {
            return Optional.of(createFireTemperingEntry());
        }

        if (CRIT_TEMPERING_ENTRY.equals(entryId)) {
            return Optional.of(createCritTemperingEntry());
        }

        return Optional.empty();
    }

    private static DamageRuleDefinition fireBaseDamageRule() {
        return new DamageRuleDefinition(
                id("tempering/fire_edge/base_fire"),
                DamageRuleRole.OFFENSIVE,
                DamagePhase.BASE_MODIFICATION,
                520,
                List.of(new AlwaysCondition()),
                List.of(DamageNexusOperations.addBaseDamage(
                        DamageChannel.FIRE_ID,
                        DamageApplicationBucket.DN_RULE_BASE,
                        4.0f
                )),
                DamageRuleStacking.STACK,
                Optional.empty(),
                Optional.of("Obelisk Tempering: +4 Fire")
        );
    }

    private static DamageRuleDefinition fireScalingRule() {
        return new DamageRuleDefinition(
                id("tempering/fire_edge/fire_scaling"),
                DamageRuleRole.OFFENSIVE,
                DamagePhase.TYPE_SCALING,
                510,
                List.of(new AlwaysCondition()),
                List.of(DamageNexusOperations.addChannelPreMultiplier(
                        DamageChannel.FIRE_ID,
                        DN_FIRE_DAMAGE_BUCKET,
                        0.15f
                )),
                DamageRuleStacking.STACK,
                Optional.empty(),
                Optional.of("Obelisk Tempering: +15% Fire")
        );
    }

    private static DamageRuleDefinition criticalPhysicalScalingRule() {
        return new DamageRuleDefinition(
                id("tempering/critical_edge/crit_physical_scaling"),
                DamageRuleRole.OFFENSIVE,
                DamagePhase.CRITICAL_HIT,
                510,
                List.of(new IsCriticalCondition()),
                List.of(DamageNexusOperations.addChannelPreMultiplier(
                        DamageChannel.PHYSICAL_ID,
                        DN_CRIT_DAMAGE_BUCKET,
                        0.20f
                )),
                DamageRuleStacking.STACK,
                Optional.empty(),
                Optional.of("Obelisk Tempering: +20% Physical Critical Damage")
        );
    }

    private static DamageEntryDisplay display(
            String key,
            String fallbackName,
            List<String> fallbackTooltip,
            String fallbackFlavor,
            boolean showRuleBreakdown
    ) {
        return new DamageEntryDisplay(
                DisplayText.translatableWithFallback(
                        langKey(key, "name"),
                        fallbackName
                ),
                fallbackTooltip
                        .stream()
                        .map(DisplayText::literal)
                        .toList(),
                Optional.of(DisplayText.translatableWithFallback(
                        langKey(key, "flavor"),
                        fallbackFlavor
                )),
                showRuleBreakdown
        );
    }

    private static String langKey(String key, String suffix) {
        return "entry."
                + ObeliskDepths.MOD_ID
                + "."
                + key
                + "."
                + suffix;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(
                ObeliskDepths.MOD_ID,
                path
        );
    }
}
