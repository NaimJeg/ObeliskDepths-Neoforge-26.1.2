package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterPhase;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.RoomStateStore;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DungeonKillProgressTest {
    private DungeonKillProgressTest() {
    }

    public static void main(String[] args) {
        testCompletionThreshold();
        testBossBarProgressHelpers();
        testFixedQuotaDoesNotGrowWhenReplacementRegisters();
        testEncounterDuplicateResolution();
        testEncounterPhaseTransitions();
        testInitialBossRoomLocked();
        testUnlockBossRoomsIsIdempotent();
    }

    private static void testCompletionThreshold() {
        DungeonKillProgress progress = new DungeonKillProgress(
                100,
                94,
                0.95F
        );

        assertFalse(
                progress.isComplete(),
                "94/100 should not satisfy the 95% threshold"
        );

        assertTrue(
                progress.withAddedKillScore(1).isComplete(),
                "95/100 should satisfy the 95% threshold"
        );

        assertEquals(
                0.95F,
                DungeonKillProgress.empty().completionThreshold(),
                "default completion threshold"
        );
    }

    private static void testBossBarProgressHelpers() {
        DungeonKillProgress empty = DungeonKillProgress.empty();

        assertEquals(
                0,
                empty.targetKillScore(),
                "empty progress has no effective target"
        );
        assertFalse(
                DungeonSessionProgressBarService.shouldDisplayProgress(empty),
                "zero required score should not display progress bar"
        );

        DungeonKillProgress started = new DungeonKillProgress(40, 0, 0.95F);
        assertEquals(38, started.targetKillScore(), "ceil target score");
        assertEquals(1.0F, started.remainingProgress(), "bar starts full");

        DungeonKillProgress complete = new DungeonKillProgress(40, 38, 0.95F);
        assertEquals(0.0F, complete.remainingProgress(), "bar drains empty at target");

        DungeonKillProgress overComplete = new DungeonKillProgress(40, 100, 0.95F);
        assertEquals(
                38,
                overComplete.clampedCurrentKillScore(),
                "current score clamps to target"
        );
        assertEquals(
                0.0F,
                overComplete.remainingProgress(),
                "over-complete progress remains empty"
        );
    }

    private static void testFixedQuotaDoesNotGrowWhenReplacementRegisters() {
        UUID starter = UUID.nameUUIDFromBytes("starter".getBytes());
        DungeonSession session = new DungeonSession(
                UUID.nameUUIDFromBytes("session".getBytes()),
                new DungeonInstanceId(UUID.nameUUIDFromBytes("instance".getBytes())),
                starter,
                new DungeonSiteKey(0, 0),
                DungeonSessionState.ACTIVE,
                DungeonAccessMode.OPEN,
                Set.of(starter),
                Set.of(starter),
                Set.of(),
                DungeonKillProgress.empty(),
                DungeonRewardState.empty(),
                0L,
                0L,
                false
        );

        assertTrue(
                session.initializeFixedKillQuota(20),
                "encounter start sets a fixed quota"
        );
        assertEquals(
                20,
                session.progress().requiredKillScore(),
                "spawning replacements must not change the fixed quota"
        );
        assertTrue(
                session.creditNormalCombatKill(1),
                "director-owned normal kill should add progress"
        );
        assertEquals(
                1,
                session.progress().currentKillScore(),
                "duplicate kill must not increment progress twice"
        );
    }

    private static void testEncounterDuplicateResolution() {
        DungeonRaidInstance encounter = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("encounter-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                12,
                4,
                0L
        );
        UUID mob = UUID.nameUUIDFromBytes("encounter-mob".getBytes());
        UUID unknownMob = UUID.nameUUIDFromBytes("encounter-unknown-mob".getBytes());

        assertEquals(
                12,
                encounter.normalKillQuota(),
                "encounter stores fixed normal-combat quota"
        );
        assertTrue(encounter.trackMob(mob), "first tracked mob is accepted");
        assertFalse(encounter.resolveMob(unknownMob), "unknown mob resolution is rejected");
        assertTrue(encounter.resolveMob(mob), "first resolution is accepted");
        assertFalse(encounter.resolveMob(mob), "duplicate resolution is ignored");
        assertEquals(
                0,
                encounter.trackedMobIds().size(),
                "resolved mob is removed from living tracked set"
        );
    }

    private static void testEncounterPhaseTransitions() {
        DungeonRaidInstance encounter = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("phase-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                DungeonEncounterDirector.fixedNormalKillQuota(
                        new DungeonDifficulty(1, 1.0F, 1.0F, 1)
                ),
                DungeonEncounterDirector.desiredLivingMobCount(
                        new DungeonDifficulty(1, 1.0F, 1.0F, 1)
                ),
                0L
        );

        assertEquals(
                DungeonEncounterPhase.COMBAT,
                encounter.encounterPhase(),
                "new encounter begins in combat phase"
        );
        assertTrue(
                encounter.setEncounterPhase(DungeonEncounterPhase.BOSS),
                "combat transitions to boss once"
        );
        assertFalse(
                encounter.setEncounterPhase(DungeonEncounterPhase.BOSS),
                "boss transition is idempotent"
        );
        assertTrue(encounter.markBossCompleted(), "boss completion is recorded once");
        assertFalse(encounter.markBossCompleted(), "boss completion is idempotent");
        assertTrue(encounter.markEncounterComplete(), "complete transition updates encounter");
        assertEquals(
                DungeonEncounterPhase.COMPLETE,
                encounter.encounterPhase(),
                "complete transition sets phase"
        );
        assertEquals(
                DungeonRaidStatus.WON,
                encounter.status(),
                "complete transition sets raid status"
        );
        assertTrue(encounter.isTerminal(), "complete encounter is terminal");

        DungeonRaidInstance expired = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("expired-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                12,
                4,
                0L
        );
        assertTrue(expired.markEncounterExpired(), "expired transition updates encounter");
        assertEquals(
                DungeonEncounterPhase.EXPIRED,
                expired.encounterPhase(),
                "expired transition sets phase"
        );
        assertEquals(
                DungeonRaidStatus.EXPIRED,
                expired.status(),
                "expired transition sets raid status"
        );
        assertTrue(expired.isTerminal(), "expired encounter is terminal");

        DungeonRaidInstance failed = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("failed-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                12,
                4,
                0L
        );
        assertTrue(failed.markEncounterFailed(), "failed transition updates encounter");
        assertEquals(
                DungeonEncounterPhase.FAILED,
                failed.encounterPhase(),
                "failed transition sets phase"
        );
        assertEquals(
                DungeonRaidStatus.FAILED,
                failed.status(),
                "failed transition sets raid status"
        );
        assertTrue(failed.isTerminal(), "failed encounter is terminal");
    }

    private static void testInitialBossRoomLocked() {
        DungeonInstanceId instanceId =
                new DungeonInstanceId(UUID.nameUUIDFromBytes("initial-instance".getBytes()));

        assertEquals(
                DungeonRoomStatus.DISCOVERED,
                DungeonRoomState.initial(
                        instanceId,
                        DungeonRoomType.START,
                        DungeonRoomId.of("start")
                ).status(),
                "START rooms begin discovered"
        );
        assertEquals(
                DungeonRoomStatus.LOCKED,
                DungeonRoomState.initial(
                        instanceId,
                        DungeonRoomType.BOSS,
                        DungeonRoomId.of("boss")
                ).status(),
                "BOSS rooms begin locked"
        );
    }

    private static void testUnlockBossRoomsIsIdempotent() {
        int[] dirtyCount = {0};
        RoomStateStore store = new RoomStateStore(() -> dirtyCount[0]++);
        DungeonInstanceId instanceId =
                new DungeonInstanceId(UUID.nameUUIDFromBytes("unlock-instance".getBytes()));
        DungeonRoomId bossRoomId = DungeonRoomId.of("boss");

        store.load(List.of(
                new DungeonRoomState(
                        instanceId,
                        bossRoomId,
                        DungeonRoomType.BOSS,
                        DungeonRoomStatus.LOCKED,
                        false
                ),
                new DungeonRoomState(
                        instanceId,
                        DungeonRoomId.of("combat"),
                        DungeonRoomType.COMBAT,
                        DungeonRoomStatus.UNDISCOVERED,
                        false
                )
        ));

        assertTrue(store.unlockBossRooms(instanceId), "first boss unlock changes state");
        assertEquals(1, dirtyCount[0], "unlock marks dirty once");
        assertEquals(
                DungeonRoomStatus.UNDISCOVERED,
                store.get(instanceId, bossRoomId).orElseThrow().status(),
                "boss room becomes accessible but undiscovered"
        );
        assertFalse(store.unlockBossRooms(instanceId), "second boss unlock is idempotent");
        assertEquals(1, dirtyCount[0], "idempotent unlock does not mark dirty again");
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
            float expected,
            float actual,
            String message
    ) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
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
