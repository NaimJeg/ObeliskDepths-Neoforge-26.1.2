package io.github.naimjeg.obeliskdepths.block;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalEntityService;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.ObeliskInteractionHandler;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class ObeliskBlock extends Block {
    public static final MapCodec<ObeliskBlock> CODEC = simpleCodec(ObeliskBlock::new);

    public static final EnumProperty<ObeliskPart> PART =
            EnumProperty.create("part", ObeliskPart.class);

    public ObeliskBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, ObeliskPart.BOTTOM));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        if (pos.getY() + 2 >= level.getMaxY()) {
            return null;
        }

        if (!level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }

        if (!level.getBlockState(pos.above(2)).canBeReplaced(context)) {
            return null;
        }

        return this.defaultBlockState().setValue(PART, ObeliskPart.BOTTOM);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        level.setBlock(pos.above(), state.setValue(PART, ObeliskPart.MIDDLE), Block.UPDATE_ALL);
        level.setBlock(pos.above(2), state.setValue(PART, ObeliskPart.TOP), Block.UPDATE_ALL);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        if (level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            serverPlayer.sendOverlayMessage(
                    Component.translatable("message.obeliskdepths.obelisk.inside_dungeon_denied")
            );
            return InteractionResult.FAIL;
        }

        BlockPos bottomPos = this.getBottomPos(pos, state);

        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, opener) -> new ObeliskPortalMenu(
                        containerId,
                        inventory,
                        ContainerLevelAccess.create(level, bottomPos),
                        bottomPos
                ),
                Component.translatable("container.obeliskdepths.obelisk_portal")
        ));

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void destroy(
            LevelAccessor level,
            BlockPos pos,
            BlockState state
    ) {
        if (level instanceof ServerLevel sourceLevel
                && !sourceLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)
                && state.hasProperty(PART)) {
            ServerLevel dungeonLevel = sourceLevel.getServer()
                    .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

            if (dungeonLevel != null) {
                DungeonPortalEntityService.closeSessionsForSourceObelisk(
                        sourceLevel,
                        dungeonLevel,
                        sourceLevel.dimension(),
                        this.getBottomPos(pos, state)
                );
            }
        }

        super.destroy(level, pos, state);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbor,
            BlockPos neighborPos,
            BlockState neighborState,
            RandomSource random
    ) {
        ObeliskPart part = state.getValue(PART);

        if (directionToNeighbor == Direction.DOWN) {
            if (!state.canSurvive(level, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        if (directionToNeighbor == Direction.UP) {
            if (part == ObeliskPart.BOTTOM && !isMiddle(level, pos.above())) {
                return Blocks.AIR.defaultBlockState();
            }

            if (part == ObeliskPart.MIDDLE && !isTop(level, pos.above())) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        return super.updateShape(
                state,
                level,
                ticks,
                pos,
                directionToNeighbor,
                neighborPos,
                neighborState,
                random
        );
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        ObeliskPart part = state.getValue(PART);

        return switch (part) {
            case BOTTOM -> {
                BlockPos below = pos.below();
                yield level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
            }

            case MIDDLE -> isBottom(level, pos.below());

            case TOP -> isMiddle(level, pos.below());
        };
    }

    private BlockPos getBottomPos(BlockPos pos, BlockState state) {
        return switch (state.getValue(PART)) {
            case BOTTOM -> pos;
            case MIDDLE -> pos.below();
            case TOP -> pos.below(2);
        };
    }

    private boolean isBottom(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(PART) == ObeliskPart.BOTTOM;
    }

    private boolean isMiddle(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(PART) == ObeliskPart.MIDDLE;
    }

    private boolean isTop(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(PART) == ObeliskPart.TOP;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }
}
