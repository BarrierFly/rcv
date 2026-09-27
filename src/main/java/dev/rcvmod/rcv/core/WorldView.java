package dev.rcvmod.rcv.core;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of the world used by the traversal. Kept deliberately small so the same engine can
 * run against a {@code ServerLevel}, a {@code ClientLevel} or a fake world in unit tests.
 *
 * <p>Signal directions follow the vanilla convention: methods take the direction pointing from the
 * emitter to the receiver; the implementation flips it before calling {@code SignalGetter} (see
 * {@code §3.1}).
 */
public interface WorldView {

    boolean isLoaded(BlockPos pos);

    boolean isClientSide();

    BlockState state(BlockPos pos);

    /** Strictly equals {@code BlockState.isRedstoneConductor(level, pos)} (§3). */
    boolean isConductor(BlockPos pos);

    /** Weak signal emitted by {@code emitter} into the neighbour located at {@code emitter.relative(dir)}. */
    int weakSignalTo(BlockPos emitter, Direction dirFromEmitterToReceiver);

    /** Strong (direct) signal emitted by {@code emitter} into the neighbour at {@code emitter.relative(dir)}. */
    int directSignalTo(BlockPos emitter, Direction dirFromEmitterToReceiver);

    int controlInputSignal(BlockPos emitterPos, Direction dirFromReceiverToEmitter, boolean diodesOnly);

    boolean hasNeighborSignal(BlockPos pos);

    int bestNeighborSignal(BlockPos pos);

    @Nullable BlockEntity blockEntity(BlockPos pos);

    List<ItemFrame> itemFrames(BlockPos pos, Direction face);

    /** Runs a read-only {@code PistonStructureResolver}. */
    PistonResult pistonStructure(BlockPos pos, Direction facing, boolean extending);

    /** Rail neighbours reachable from {@code railPos} according to {@code RailState}. */
    List<BlockPos> railConnections(BlockPos railPos);

    boolean isFaceSturdy(BlockPos pos, Direction face);
}
