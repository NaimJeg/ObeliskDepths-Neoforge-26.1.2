package io.github.naimjeg.obeliskdepths.worldgen.structure;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/*
 * Worldgen-owned dungeon piece.
 *
 * This is the authoritative source for room/corridor metadata after a
 * StructureStart has been generated.
 *
 * Stored metadata:
 *   role      - semantic role of this piece, such as start/combat/corridor
 *   roomId    - stable id used by runtime room state
 *   anchorPos - semantic anchor for spawn/reward/debug logic
 *   boundingBox - inherited physical bounds used for chunk placement
 *
 * Runtime systems must project room metadata from this piece instead of
 * reconstructing room layout independently.
 */
public final class ObeliskDungeonPiece extends StructurePiece {
    private static final String TAG_ROLE = "Role";
    private static final String TAG_ROOM_ID = "RoomId";
    private static final String TAG_ANCHOR_X = "AnchorX";
    private static final String TAG_ANCHOR_Y = "AnchorY";
    private static final String TAG_ANCHOR_Z = "AnchorZ";

    private final ObeliskDungeonPieceRole role;
    private final String roomId;
    private final BlockPos anchorPos;

    public ObeliskDungeonPiece(
            ObeliskDungeonPieceRole role,
            String roomId,
            BlockPos anchorPos,
            BoundingBox boundingBox
    ) {
        super(
                ModWorldgen.OBELISK_DUNGEON_PIECE.get(),
                0,
                boundingBox
        );

        this.role = role;
        this.roomId = roomId;
        this.anchorPos = anchorPos;
    }

    public ObeliskDungeonPiece(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        super(ModWorldgen.OBELISK_DUNGEON_PIECE.get(), tag);

        this.role = ObeliskDungeonPieceRole.byName(
                tag.getStringOr(TAG_ROLE, ObeliskDungeonPieceRole.START_ROOM.serializedName())
        );

        this.roomId = tag.getStringOr(TAG_ROOM_ID, this.role.serializedName());

        this.anchorPos = new BlockPos(
                tag.getIntOr(TAG_ANCHOR_X, this.boundingBox.getCenter().getX()),
                tag.getIntOr(TAG_ANCHOR_Y, this.boundingBox.minY() + 1),
                tag.getIntOr(TAG_ANCHOR_Z, this.boundingBox.getCenter().getZ())
        );
    }

    public ObeliskDungeonPieceRole role() {
        return this.role;
    }

    public String roomId() {
        return this.roomId;
    }

    public BlockPos anchorPos() {
        return this.anchorPos;
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        tag.putString(TAG_ROLE, this.role.serializedName());
        tag.putString(TAG_ROOM_ID, this.roomId);
        tag.putInt(TAG_ANCHOR_X, this.anchorPos.getX());
        tag.putInt(TAG_ANCHOR_Y, this.anchorPos.getY());
        tag.putInt(TAG_ANCHOR_Z, this.anchorPos.getZ());
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            BlockPos referencePos
    ) {
        BoundingBox pieceBB = this.getBoundingBox();

        placeDebugPlane(level, chunkBB, pieceBB);
    }

    /*
     * Temporary renderer. Later replace this with StructureTemplate placement
     * loaded from vanilla structure-block .nbt files while preserving
     * role/roomId/anchor/boundingBox metadata.
     */
    private void placeDebugPlane(
            WorldGenLevel level,
            BoundingBox chunkBB,
            BoundingBox pieceBB
    ) {
        int floorY = pieceBB.minY();

        if (floorY < chunkBB.minY() || floorY > chunkBB.maxY()) {
            return;
        }

        int minX = Math.max(pieceBB.minX(), chunkBB.minX());
        int maxX = Math.min(pieceBB.maxX(), chunkBB.maxX());
        int minZ = Math.max(pieceBB.minZ(), chunkBB.minZ());
        int maxZ = Math.min(pieceBB.maxZ(), chunkBB.maxZ());

        if (minX > maxX || minZ > maxZ) {
            return;
        }

        BlockState state = floorStateForRole();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), state, 2);
            }
        }
    }

    private BlockState floorStateForRole() {
        return switch (this.role) {
            case START_ROOM, EXIT_ROOM, CORRIDOR -> ModBlocks.DUNGEON_BRICKS.get().defaultBlockState();
            case COMBAT_ROOM -> ModBlocks.DUNGEON_STONE.get().defaultBlockState();
            case TREASURE_ROOM -> ModBlocks.DUNGEON_TILES.get().defaultBlockState();
        };
    }
}
