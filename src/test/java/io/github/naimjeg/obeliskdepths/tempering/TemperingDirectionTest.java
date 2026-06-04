package io.github.naimjeg.obeliskdepths.tempering;

public final class TemperingDirectionTest {
    private TemperingDirectionTest() {
    }

    public static void main(String[] args) {
        assertSame(
                TemperingDirection.BALANCE,
                TemperingDirection.byId(-1),
                "invalid negative id should fall back to balance"
        );
        assertSame(
                TemperingDirection.BALANCE,
                TemperingDirection.byId(99),
                "invalid high id should fall back to balance"
        );
        assertEquals(0, TemperingDirection.BALANCE.id(), "balance id");
        assertEquals(1, TemperingDirection.EDGE.id(), "edge id");
        assertEquals(2, TemperingDirection.GUARD.id(), "guard id");
        assertEquals(3, TemperingDirection.ECHO.id(), "echo id");
    }

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }
}
