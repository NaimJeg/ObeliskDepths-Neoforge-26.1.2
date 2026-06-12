package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;

public final class ObeliskDungeonPlacementSettings {
    /*
     * Preliminary testing constants.
     *
     * These must match data/obeliskdepths/worldgen/structure_set/
     * obelisk_dungeons.json while the overlap guard predicts nearby vanilla
     * placement candidates. Later, prefer one datagen/config source of truth or
     * a custom placement strategy.
     *
     * These values are only for worldgen candidate guard/debug prediction. They
     * are not authoritative runtime dungeon site metadata.
     */
    public static final int SPACING = 8;
    public static final int SEPARATION = 4;
    public static final int SALT = 91827364;

    private ObeliskDungeonPlacementSettings() {
    }
}
