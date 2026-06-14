package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterPhase;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardStatus;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class DungeonRuntimeStateTest {
    private DungeonRuntimeStateTest() {
    }

    public static void main(String[] args) {
        testUnknownAndDuplicateMobResolution();
        testMissingMobStateIsBounded();
        testAuthoritativeNormalProgress();
        testBossKillDoesNotIncrementNormalProgress();
        testRaidCodecRestoresRuntimeProgress();
        testRewardStateRequiresPlacementForAvailability();
        testRewardClaimIsIdempotent();
    }

    private static void testUnknownAndDuplicateMobResolution() {
        DungeonRaidInstance encounter = encounter("resolution", 3);
        UUID tracked = UUID.nameUUIDFromBytes("tracked".getBytes());
        UUID unknown = UUID.nameUUIDFromBytes("unknown".getBytes());

        assertTrue(encounter.trackMob(tracked), "tracked mob is accepted");
        assertFalse(encounter.resolveMob(unknown), "unknown mob cannot resolve");
        assertTrue(encounter.resolveMob(tracked), "tracked mob resolves once");
        assertFalse(encounter.resolveMob(tracked), "duplicate resolve is rejected");
        assertEquals(0, encounter.resolvedMobIds().size(), "legacy resolved history is not retained");
    }

    private static void testMissingMobStateIsBounded() {
        DungeonRaidInstance encounter = encounter("missing", 3);
        UUID mob = UUID.nameUUIDFromBytes("missing-mob".getBytes());
        long start = 100L;

        encounter.trackMob(mob);
        assertTrue(encounter.markMobTemporarilyMissing(mob, start), "missing timestamp is persisted");
        assertFalse(encounter.markMobTemporarilyMissing(mob, start + 20L), "missing timestamp is stable");
        assertEquals(Optional.of(start), encounter.missingSinceGameTime(mob), "missing since value");
        assertTrue(
                start + DungeonEncounterDirector.MISSING_MOB_RECONCILE_TIMEOUT_TICKS
                        >= encounter.missingSinceGameTime(mob).orElseThrow(),
                "timeout can be evaluated from persisted timestamp"
        );
        assertTrue(encounter.resolveMob(mob), "permanently missing mob can be terminally reconciled");
    }

    private static void testAuthoritativeNormalProgress() {
        DungeonRaidInstance encounter = encounter("progress", 2);

        assertTrue(encounter.creditNormalKill(), "first normal kill credits");
        assertTrue(encounter.creditNormalKill(), "second normal kill credits");
        assertFalse(encounter.creditNormalKill(), "quota clamps extra normal progress");
        assertEquals(2, encounter.creditedNormalKills(), "credited normal kills are authoritative");
        assertTrue(encounter.normalKillQuotaComplete(), "quota completion uses encounter state");
    }

    private static void testBossKillDoesNotIncrementNormalProgress() {
        DungeonRaidInstance encounter = encounter("boss", 2);
        encounter.setEncounterPhase(DungeonEncounterPhase.BOSS);
        assertTrue(encounter.markBossCompleted(), "boss completion records once");
        assertEquals(0, encounter.creditedNormalKills(), "boss completion does not credit normal progress");
    }

    private static void testRaidCodecRestoresRuntimeProgress() {
        DungeonRaidInstance encounter = encounter("codec", 5);
        UUID mob = UUID.nameUUIDFromBytes("codec-mob".getBytes());
        encounter.trackMob(mob);
        encounter.markMobTemporarilyMissing(mob, 42L);
        encounter.creditNormalKill();

        var encoded = DungeonRaidInstance.CODEC.encodeStart(JsonOps.INSTANCE, encounter)
                .getOrThrow();
        DungeonRaidInstance decoded = DungeonRaidInstance.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(5, decoded.normalKillQuota(), "quota survives codec");
        assertEquals(1, decoded.creditedNormalKills(), "progress survives codec");
        assertEquals(DungeonEncounterPhase.COMBAT, decoded.encounterPhase(), "phase survives codec");
        assertTrue(decoded.trackedMobIds().contains(mob), "tracked mob survives codec");
        assertEquals(Optional.of(42L), decoded.missingSinceGameTime(mob), "missing timestamp survives codec");
    }

    private static void testRewardStateRequiresPlacementForAvailability() {
        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("reward-instance".getBytes())),
                Optional.empty(),
                0L
        );

        assertEquals(DungeonRewardStatus.BOSS_DEFEATED, reward.status(), "boss death is not availability");
        assertTrue(reward.markPlacementPending(), "reward can enter placement pending");
        assertEquals(DungeonRewardStatus.PLACEMENT_PENDING, reward.status(), "pending is explicit");
        assertTrue(reward.markAvailable(new BlockPos(1, 2, 3)), "placement makes reward available");
        assertEquals(Optional.of(new BlockPos(1, 2, 3)), reward.rewardPos(), "available reward has position");
    }

    private static void testRewardClaimIsIdempotent() {
        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("claim-instance".getBytes())),
                Optional.empty(),
                0L
        );
        reward.markAvailable(new BlockPos(1, 2, 3));

        assertTrue(reward.markOpened(), "first open changes state");
        assertFalse(reward.markOpened(), "duplicate open is idempotent");
        assertTrue(reward.markClaimed(), "first claim changes state");
        assertFalse(reward.markClaimed(), "duplicate claim is idempotent");
        assertEquals(DungeonRewardStatus.CLAIMED, reward.status(), "reward remains claimed");
    }

    private static DungeonRaidInstance encounter(
            String name,
            int quota
    ) {
        return DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes((name + "-instance").getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                quota,
                3,
                0L
        );
    }

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(
            boolean value,
            String message
    ) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
