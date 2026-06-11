package io.github.naimjeg.obeliskdepths.dungeon.session;

public final class DungeonKillProgressTest {
    private DungeonKillProgressTest() {
    }

    public static void main(String[] args) {
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
}
