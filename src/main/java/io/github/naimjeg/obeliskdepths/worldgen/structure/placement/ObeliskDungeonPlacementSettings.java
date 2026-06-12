package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;

public final class ObeliskDungeonPlacementSettings {
    /*
     * These constants mirror data/obeliskdepths/worldgen/structure_set/
     * obelisk_dungeons.json and are used by cheap candidate lookup/debug
     * diagnostics. The separation is intentionally larger than the temporary
     * radial debug site's conservative radius so overlap checks do not need to
     * regenerate neighboring layouts.
     */
    public static final int SPACING = 64;
    public static final int SEPARATION = 40;
    public static final int SALT = 91827364;
    public static final int MAX_SITE_RADIUS_BLOCKS = 256;
    public static final int MAX_LOOKUP_CANDIDATES = 128;

    private ObeliskDungeonPlacementSettings() {
    }
}
