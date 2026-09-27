package dev.rcvmod.rcv.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Vanilla shape-connection rules (§5.1).
 *
 * <p>Each method mirrors the {@code updateShape} / {@code connectsTo} logic of the corresponding
 * vanilla block: a fence/bars/wall connects to a neighbour when the neighbour is a face-sturdy,
 * non-exception block, another member of its own family, or an aligned fence gate. Replacing the
 * former block-family whitelist makes the computed SHAPE edges match vanilla.
 */
public final class ShapeConnectivity {

    private ShapeConnectivity() {
    }

    /**
     * True when the block at {@code selfPos} has a shape connection/attachment towards its neighbour
     * in direction {@code dir}, i.e. the corresponding vanilla connection property would be set.
     */
    public static boolean connectsToward(WorldView world, BlockPos selfPos, BlockState self, Direction dir) {
        BlockPos neighbourPos = selfPos.relative(dir);
        if (!world.isLoaded(neighbourPos)) {
            return false;
        }
        BlockState neighbour = world.state(neighbourPos);
        Direction dirNeighbourToSelf = dir.getOpposite();
        boolean sturdy = world.isFaceSturdy(neighbourPos, dirNeighbourToSelf);

        if (self.getBlock() instanceof FenceBlock) {
            return fenceConnectsTo(self, neighbour, sturdy, dirNeighbourToSelf);
        }
        if (self.getBlock() instanceof IronBarsBlock) {
            return ironBarsAttachsTo(neighbour, sturdy);
        }
        if (self.getBlock() instanceof WallBlock) {
            return wallConnectsTo(neighbour, sturdy, dirNeighbourToSelf);
        }
        if (self.getBlock() instanceof FenceGateBlock) {
            return gateInWall(self, neighbour, dir);
        }
        if (self.getBlock() instanceof BellBlock) {
            return bellAttachedTo(world, self, neighbourPos, dir);
        }
        return false;
    }

    /** Mirrors {@code FenceBlock.connectsTo}. */
    private static boolean fenceConnectsTo(BlockState self, BlockState neighbour, boolean sturdy,
                                           Direction dirNeighbourToSelf) {
        boolean sameFence = neighbour.is(BlockTags.FENCES)
                && neighbour.is(BlockTags.WOODEN_FENCES) == self.is(BlockTags.WOODEN_FENCES);
        boolean alignedGate = neighbour.getBlock() instanceof FenceGateBlock
                && FenceGateBlock.connectsToDirection(neighbour, dirNeighbourToSelf);
        return !Block.isExceptionForConnection(neighbour) && sturdy || sameFence || alignedGate;
    }

    /** Mirrors {@code IronBarsBlock.attachsTo}. */
    private static boolean ironBarsAttachsTo(BlockState neighbour, boolean sturdy) {
        return !Block.isExceptionForConnection(neighbour) && sturdy
                || neighbour.getBlock() instanceof IronBarsBlock
                || neighbour.is(BlockTags.WALLS);
    }

    /** Mirrors {@code WallBlock.connectsTo}. */
    private static boolean wallConnectsTo(BlockState neighbour, boolean sturdy, Direction dirNeighbourToSelf) {
        boolean alignedGate = neighbour.getBlock() instanceof FenceGateBlock
                && FenceGateBlock.connectsToDirection(neighbour, dirNeighbourToSelf);
        return neighbour.is(BlockTags.WALLS)
                || !Block.isExceptionForConnection(neighbour) && sturdy
                || neighbour.getBlock() instanceof IronBarsBlock
                || alignedGate;
    }

    /** Mirrors the {@code IN_WALL} part of {@code FenceGateBlock.updateShape}: only walls on the sides. */
    private static boolean gateInWall(BlockState gate, BlockState neighbour, Direction dir) {
        if (!neighbour.is(BlockTags.WALLS)) {
            return false;
        }
        Direction facing = gate.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return dir.getAxis() == facing.getClockWise().getAxis();
    }

    /**
     * A bell is connected to the single block it hangs from (below for a floor bell, above for a
     * ceiling bell, behind it for a wall bell) when that block provides a sturdy face towards it.
     */
    private static boolean bellAttachedTo(WorldView world, BlockState bell, BlockPos neighbourPos, Direction dir) {
        BellAttachType attachment = bell.getValue(BlockStateProperties.BELL_ATTACHMENT);
        Direction support = switch (attachment) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case SINGLE_WALL, DOUBLE_WALL -> bell.getValue(BlockStateProperties.HORIZONTAL_FACING);
        };
        return support == dir && world.isFaceSturdy(neighbourPos, dir.getOpposite());
    }
}
