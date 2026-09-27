package dev.rcvmod.rcv.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * SHAPE connection rules, expressed as "when A's easily-toggled state changes, B's shape follows".
 *
 * <p>The only easily-toggled state RCV models here is {@code OPEN} on doors and trap doors: flipping
 * it moves a thin panel, which flips the face sturdiness a neighbouring fence / iron bars / wall (or
 * a scaffolding that is supported from below) sees. The connection is reported even when the block
 * is not currently in the connected state, as long as flipping {@code OPEN} would change the face.
 *
 * <p>Fence gates are deliberately excluded: a closed gate's {@code getBlockSupportShape} is not a
 * full face, so it is never {@code isFaceSturdy(UP)} and cannot support scaffolding (the §12 #5
 * finding in the review report was based on the {@code getBlockSupportShape} toggle alone).
 */
public final class ShapeConnectivity {

    private ShapeConnectivity() {
    }

    /** True when the block is a door or trap door (it has the {@code OPEN} property). */
    public static boolean isOpenMutable(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    /**
     * True when flipping the {@code OPEN} state of {@code state} at {@code pos} changes whether that
     * face is sturdy, i.e. the neighbour on that side gains/loses its connection.
     */
    public static boolean openTogglesFace(WorldView world, BlockPos pos, BlockState state, Direction face) {
        if (!isOpenMutable(state)) {
            return false;
        }
        boolean now = world.isFaceSturdy(state, pos, face);
        BlockState toggled = state.setValue(BlockStateProperties.OPEN, !state.getValue(BlockStateProperties.OPEN));
        boolean flipped = world.isFaceSturdy(toggled, pos, face);
        return now != flipped;
    }
}
