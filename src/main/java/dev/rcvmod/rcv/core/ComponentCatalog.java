package dev.rcvmod.rcv.core;

import java.util.EnumSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** Maps block states to roles and classifies the special vanilla families RCV knows about (§5.3). */
public final class ComponentCatalog {

    private ComponentCatalog() {
    }

    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public static EnumSet<Role> roles(BlockState state) {
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        if (state.isSignalSource()) {
            roles.add(Role.SOURCE);
        }
        if (state.hasAnalogOutputSignal()) {
            roles.add(Role.ANALOG_SOURCE);
        }
        if (isTransmitter(state)) {
            roles.add(Role.TRANSMITTER);
        }
        if (isConsumer(state)) {
            roles.add(Role.CONSUMER);
        }
        return roles;
    }

    public static boolean isComponent(BlockState state) {
        return state.isSignalSource() || state.hasAnalogOutputSignal() || isTransmitter(state) || isConsumer(state)
                || isConnectivity(state) || isDistanceBlock(state) || isRail(state);
    }

    public static boolean isTransmitter(BlockState state) {
        return isWire(state) || isDiode(state) || isRedstoneTorch(state);
    }

    public static boolean isWire(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE);
    }

    public static boolean isDiode(BlockState state) {
        return state.getBlock() instanceof DiodeBlock;
    }

    public static boolean isRepeater(BlockState state) {
        return state.getBlock() instanceof RepeaterBlock;
    }

    public static boolean isComparator(BlockState state) {
        return state.getBlock() instanceof ComparatorBlock;
    }

    public static boolean isObserver(BlockState state) {
        return state.getBlock() instanceof ObserverBlock;
    }

    public static boolean isRedstoneTorch(BlockState state) {
        return state.getBlock() instanceof RedstoneTorchBlock || state.getBlock() instanceof RedstoneWallTorchBlock;
    }

    /** Direction from a redstone torch towards the block it is attached to, or {@code null}. */
    public static @Nullable Direction torchAttach(BlockState state) {
        if (state.getBlock() instanceof RedstoneWallTorchBlock) {
            return state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
        }
        if (state.getBlock() instanceof RedstoneTorchBlock) {
            return Direction.DOWN;
        }
        return null;
    }

    public static boolean isPiston(BlockState state) {
        return state.getBlock() instanceof PistonBaseBlock;
    }

    public static boolean isDispenserLike(BlockState state) {
        return state.getBlock() instanceof DispenserBlock;
    }

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock;
    }

    public static boolean isTrapDoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock;
    }

    public static boolean isFenceGate(BlockState state) {
        return state.getBlock() instanceof FenceGateBlock;
    }

    public static boolean isFence(BlockState state) {
        return state.getBlock() instanceof FenceBlock;
    }

    public static boolean isIronBars(BlockState state) {
        return state.getBlock() instanceof IronBarsBlock;
    }

    public static boolean isWall(BlockState state) {
        return state.getBlock() instanceof WallBlock;
    }

    public static boolean isBell(BlockState state) {
        return state.getBlock() instanceof BellBlock;
    }

    public static boolean isConnectivity(BlockState state) {
        return isFence(state) || isIronBars(state) || isWall(state) || isFenceGate(state) || isBell(state);
    }

    public static boolean isTripwire(BlockState state) {
        return state.getBlock() instanceof TripWireBlock;
    }

    public static boolean isTripwireHook(BlockState state) {
        return state.getBlock() instanceof TripWireHookBlock;
    }

    public static boolean isRail(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock;
    }

    public static boolean isPoweredRail(BlockState state) {
        return state.is(Blocks.POWERED_RAIL) || state.is(Blocks.ACTIVATOR_RAIL);
    }

    public static boolean isLeaves(BlockState state) {
        return state.getBlock() instanceof LeavesBlock;
    }

    public static boolean isScaffolding(BlockState state) {
        return state.getBlock() instanceof ScaffoldingBlock;
    }

    public static boolean isDistanceBlock(BlockState state) {
        return isLeaves(state) || isScaffolding(state);
    }

    public static boolean isConsumer(BlockState state) {
        return state.getBlock() instanceof PistonBaseBlock
                || state.getBlock() instanceof DispenserBlock
                || state.getBlock() instanceof RedstoneLampBlock
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof PoweredRailBlock
                || state.is(Blocks.ACTIVATOR_RAIL)
                || state.getBlock() instanceof TntBlock
                || state.getBlock() instanceof NoteBlock
                || state.getBlock() instanceof CommandBlock
                || state.getBlock() instanceof HopperBlock
                || state.getBlock() instanceof CrafterBlock
                || state.getBlock() instanceof CopperBulbBlock
                || state.getBlock() instanceof BellBlock;
    }

    /** Directly responds to neighbour updates and shape updates. */
    public static boolean isResponsive(BlockState state) {
        return isComponent(state) || isWire(state) || isDiode(state) || isPiston(state) || isDoor(state)
                || isConnectivity(state) || isDistanceBlock(state) || isRail(state);
    }

    /**
     * True for blocks whose <em>redstone signal / power</em> change actually emits a neighbour
     * update (NC). Shape-only changes (fences, doors, ...) propagate through {@code updateShape}
     * and must not be treated as NC sources (§10.2).
     */
    public static boolean emitsNc(BlockState state) {
        return isWire(state) || isDiode(state) || isObserver(state) || isRedstoneTorch(state)
                || isLever(state) || isButton(state) || isNoteBlock(state) || isScaffolding(state);
    }

    /** Direction from a lever/button towards the block it is attached to, or {@code null}. */
    public static @Nullable Direction supportDirection(BlockState state) {
        if (!(state.getBlock() instanceof FaceAttachedHorizontalDirectionalBlock)) {
            return null;
        }
        Direction connected = switch (state.getValue(BlockStateProperties.ATTACH_FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            default -> state.getValue(HorizontalDirectionalBlock.FACING);
        };
        return connected.getOpposite();
    }

    public static boolean isNoteBlock(BlockState state) {
        return state.getBlock() instanceof NoteBlock;
    }

    public static boolean isButton(BlockState state) {
        return state.getBlock() instanceof ButtonBlock;
    }

    public static boolean isLever(BlockState state) {
        return state.getBlock() instanceof LeverBlock;
    }

    public static boolean isDaylightDetector(BlockState state) {
        return state.getBlock() instanceof DaylightDetectorBlock;
    }

    public static boolean isDetectorRail(BlockState state) {
        return state.getBlock() instanceof DetectorRailBlock;
    }

    public static boolean isTarget(BlockState state) {
        return state.getBlock() instanceof TargetBlock;
    }

    public static boolean isLightningRod(BlockState state) {
        return state.getBlock() instanceof LightningRodBlock;
    }

    public static boolean isSculkSensor(BlockState state) {
        return state.getBlock() instanceof SculkSensorBlock || state.getBlock() instanceof CalibratedSculkSensorBlock;
    }

    /** FACING for the directional families RCV cares about, or {@code null} when absent. */
    public static @Nullable Direction facing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return null;
    }

    /**
     * FACING used as the input side for diodes/observers/pistons/dispensers (§3.2). Mirrors the
     * concrete property each family registers, avoiding cross-family property mix-ups.
     */
    public static @Nullable Direction inputFacing(BlockState state) {
        if (state.getBlock() instanceof HorizontalDirectionalBlock
                && state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.getValue(HorizontalDirectionalBlock.FACING);
        }
        if (state.getBlock() instanceof DirectionalBlock && state.hasProperty(DirectionalBlock.FACING)) {
            return state.getValue(DirectionalBlock.FACING);
        }
        return null;
    }
}
