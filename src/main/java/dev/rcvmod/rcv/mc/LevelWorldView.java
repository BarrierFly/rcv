package dev.rcvmod.rcv.mc;

import dev.rcvmod.rcv.core.PistonResult;
import dev.rcvmod.rcv.core.WorldView;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** {@link WorldView} backed by a {@code ServerLevel} or {@code ClientLevel}. */
public final class LevelWorldView implements WorldView {

    private final Level level;

    public LevelWorldView(Level level) {
        this.level = level;
    }

    public Level level() {
        return this.level;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return this.level.isLoaded(pos);
    }

    @Override
    public boolean isClientSide() {
        return this.level.isClientSide();
    }

    @Override
    public BlockState state(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    @Override
    public boolean isConductor(BlockPos pos) {
        return this.level.getBlockState(pos).isRedstoneConductor(this.level, pos);
    }

    @Override
    public int weakSignalTo(BlockPos emitter, Direction dirFromEmitterToReceiver) {
        return this.level.getSignal(emitter, dirFromEmitterToReceiver.getOpposite());
    }

    @Override
    public int directSignalTo(BlockPos emitter, Direction dirFromEmitterToReceiver) {
        return this.level.getDirectSignal(emitter, dirFromEmitterToReceiver.getOpposite());
    }

    @Override
    public int controlInputSignal(BlockPos emitterPos, Direction dirFromReceiverToEmitter, boolean diodesOnly) {
        return this.level.getControlInputSignal(emitterPos, dirFromReceiverToEmitter, diodesOnly);
    }

    @Override
    public boolean hasNeighborSignal(BlockPos pos) {
        return this.level.hasNeighborSignal(pos);
    }

    @Override
    public int bestNeighborSignal(BlockPos pos) {
        return this.level.getBestNeighborSignal(pos);
    }

    @Override
    public @Nullable BlockEntity blockEntity(BlockPos pos) {
        return this.level.getBlockEntity(pos);
    }

    @Override
    public List<ItemFrame> itemFrames(BlockPos pos, Direction face) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        return this.level.getEntitiesOfClass(ItemFrame.class, box, frame -> frame.getDirection() == face);
    }

    @Override
    public PistonResult pistonStructure(BlockPos pos, Direction facing, boolean extending) {
        PistonStructureResolver resolver = new PistonStructureResolver(this.level, pos, facing, extending);
        boolean resolved = resolver.resolve();
        if (!resolved) {
            return PistonResult.empty();
        }
        return new PistonResult(true, List.copyOf(resolver.getToPush()), List.copyOf(resolver.getToDestroy()));
    }

    @Override
    public List<BlockPos> railConnections(BlockPos railPos) {
        BlockState state = this.level.getBlockState(railPos);
        if (!(state.getBlock() instanceof BaseRailBlock)) {
            return List.of();
        }
        return List.copyOf(new RailState(this.level, railPos, state).getConnections());
    }

    @Override
    public boolean isFaceSturdy(BlockPos pos, Direction face) {
        return this.level.getBlockState(pos).isFaceSturdy(this.level, pos, face);
    }
}
