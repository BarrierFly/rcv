package dev.rcvmod.rcv.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SHAPE connection rules, expressed as "if A's state changes, B's shape follows".
 *
 * <p>Only state dependencies are reported, not static adjacency:
 * <ul>
 *   <li>A fence/iron bars/wall connects to a door/trap door only while that neighbour's face is
 *       sturdy towards it. The {@code OPEN} state flips that face, so the connection genuinely
 *       changes with an easily-toggled state.</li>
 *   <li>Two walls influence each other's post/side shape (the {@code UP} property and the collision
 *       cover of an adjacent wall), so wall-to-wall links are kept.</li>
 * </ul>
 * Purely static links - fence to fence, iron bars to wall, a full block's face support, bell
 * attachment or fence-gate orientation - are deliberately not reported.
 */
public final class ShapeConnectivity {

    private ShapeConnectivity() {
    }

    /**
     * True when the block at {@code selfPos} has a state-dependent shape connection towards its
     * neighbour in direction {@code dir}.
     */
    public static boolean connectsToward(WorldView world, BlockPos selfPos, BlockState self, Direction dir) {
        BlockPos neighbourPos = selfPos.relative(dir);
        if (!world.isLoaded(neighbourPos)) {
            return false;
        }
        BlockState neighbour = world.state(neighbourPos);
        Direction dirNeighbourToSelf = dir.getOpposite();

        if (isFenceBarsWall(self)) {
            // Side connections only: a door / trap door with a sturdy face towards this block. The
            // face flips with OPEN, so this is a state dependency rather than static support.
            if (dir.getAxis().isHorizontal() && isOpenMutable(neighbour)
                    && !Block.isExceptionForConnection(neighbour)
                    && world.isFaceSturdy(neighbourPos, dirNeighbourToSelf)) {
                return true;
            }
            // Walls follow an adjacent wall's state (UP / collision cover).
            if (self.getBlock() instanceof WallBlock && neighbour.getBlock() instanceof WallBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFenceBarsWall(BlockState state) {
        return state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof IronBarsBlock
                || state.getBlock() instanceof WallBlock;
    }

    /** Blocks whose face sturdiness is toggled by an easily changed state ({@code OPEN}). */
    private static boolean isOpenMutable(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;
    }
}
