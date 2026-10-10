package dev.rcvmod.rcv.core;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EdgeType#DUST_TRAPDOOR} rules: a top-half trap door whose {@code OPEN} flip changes which
 * ways the horizontally adjacent redstone wire {@code W} connects.
 *
 * <p>This is a faithful port of {@code RedStoneWireBlock#getConnectionState} and
 * {@code #getConnectingSide}. Porting the whole {@code getConnectionState} (rather than the bare
 * {@code getConnectingSide}) is required because vanilla {@code getSignal} reads the connection
 * <em>state</em>, and its back-fill segment promotes {@code NONE} to {@code SIDE}; a bare port would
 * report flips that vanilla never sees.
 *
 * <p>Everything here is a pure function of the world: {@link #wireSides} can be asked for the sides
 * a wire would have with one specific trap door flipped, which is how the engine decides whether an
 * edge exists at all ({@code closed != open}). The edge is a <em>control</em> relationship, so it is
 * emitted for both trap door states and only the polarity of the flip is carried by the geometry.
 *
 * <p>The one version-dependent input is {@code legacy}. Up to 1.19.4 vanilla gated the connection on
 * {@code canSurviveOn(trapDoor)}; 1.20 replaced that with an unconditional
 * {@code state instanceof TrapDoorBlock ||}, which removed the gate. The field therefore selects
 * which of the two the current environment (possibly modded) behaves like - see
 * {@link DustTrapdoorEra}.
 */
public final class DustTrapdoor {

    private DustTrapdoor() {
    }

    /**
     * True for a top-half trap door. Only that half can have a wire resting on it: a closed top half
     * is the only trap door shape whose {@code UP} face is a full face, so it is the only one where
     * {@code canSurviveOn} holds (F2). The bottom half is never sturdy upwards and an open trap door
     * is a thin 3/16 panel, so neither can carry the wire above.
     */
    public static boolean isGateTrapDoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.HALF) == Half.TOP;
    }

    /**
     * Mirrors {@code RedStoneWireBlock#isDot} (1.19.4 {@code :213-217}): all four stored sides are
     * {@code NONE}.
     */
    public static boolean isDot(BlockState wire) {
        return !side(wire, Direction.NORTH).isConnected()
                && !side(wire, Direction.SOUTH).isConnected()
                && !side(wire, Direction.EAST).isConnected()
                && !side(wire, Direction.WEST).isConnected();
    }

    /**
     * The four connection sides a wire actually has, as vanilla computes them on the fly.
     *
     * <p>{@code flipPos}/{@code flipOpen} optionally substitute one trap door's {@code OPEN} value,
     * which is how the engine probes "does flipping this trap door change anything?". Pass
     * {@code flipPos == null} for the world's real state (that is what vanilla's
     * {@code getSignal} sees, and what {@link ConnectionEngine#wireEmitsToward} must use).
     *
     * @param legacy {@code true} for pre-1.20 semantics ({@code bl = canSurviveOn(T)}), {@code false}
     *              for 1.20+ ({@code bl = T instanceof TrapDoorBlock || canSurviveOn(T)}, which no
     *              longer depends on {@code OPEN} and therefore yields no gate at all)
     */
    public static Map<Direction, RedstoneSide> wireSides(WorldView world, BlockPos wire, boolean legacy,
                                                          @Nullable BlockPos flipPos, boolean flipOpen) {
        BlockState stored = world.state(wire);
        EnumMap<Direction, RedstoneSide> raw = new EnumMap<>(Direction.class);
        // getMissingConnections feeds in defaultBlockState(), so all four sides start at NONE and are
        // recomputed unconditionally; the stored connections never take part.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            raw.put(direction, connectingSide(world, wire, direction, legacy, flipPos, flipOpen));
        }

        // getConnectionState early-returns a still-dot state, skipping the whole back-fill segment.
        if (isDot(stored) && !connected(raw, Direction.NORTH) && !connected(raw, Direction.SOUTH)
                && !connected(raw, Direction.EAST) && !connected(raw, Direction.WEST)) {
            return raw;
        }

        boolean noVertical = !connected(raw, Direction.NORTH) && !connected(raw, Direction.SOUTH);
        boolean noHorizontal = !connected(raw, Direction.EAST) && !connected(raw, Direction.WEST);
        EnumMap<Direction, RedstoneSide> result = new EnumMap<>(raw);
        // The back-fill only ever promotes NONE -> SIDE; a side that is already connected is left be.
        backFill(result, Direction.WEST, noVertical);
        backFill(result, Direction.EAST, noVertical);
        backFill(result, Direction.NORTH, noHorizontal);
        backFill(result, Direction.SOUTH, noHorizontal);
        return result;
    }

    /** Convenience overload for the world's real state. */
    public static Map<Direction, RedstoneSide> wireSides(WorldView world, BlockPos wire, boolean legacy) {
        return wireSides(world, wire, legacy, null, false);
    }

    /**
     * Mirrors the four-argument {@code RedStoneWireBlock#getConnectingSide}
     * (1.19.4 {@code :258-276}; 1.20+ is line-for-line the same apart from the {@code bl} expression).
     */
    public static RedstoneSide connectingSide(WorldView world, BlockPos wire, Direction direction, boolean legacy,
                                              @Nullable BlockPos flipPos, boolean flipOpen) {
        BlockPos neighbourPos = wire.relative(direction);
        BlockState neighbour = world.state(neighbourPos);
        if (flipPos != null && flipPos.equals(neighbourPos)) {
            neighbour = withOpen(neighbour, flipOpen);
        }
        if (!world.isConductor(wire.above())) {
            boolean bl = legacy ? canSurviveOn(world, neighbourPos, neighbour)
                    : neighbour.getBlock() instanceof TrapDoorBlock || canSurviveOn(world, neighbourPos, neighbour);
            // The single-argument shouldConnectTo: "is there a wire resting on the neighbour?", i.e. the
            // gate premise D. Note it is the wire-accepting predicate, not the diode/observer-aware one.
            if (bl && shouldConnectTo(world.state(neighbourPos.above()), null)) {
                return world.isFaceSturdy(neighbour, neighbourPos, direction.getOpposite())
                        ? RedstoneSide.UP : RedstoneSide.SIDE;
            }
        }
        return !shouldConnectTo(neighbour, direction)
                && (world.isConductor(neighbourPos) || !shouldConnectTo(world.state(neighbourPos.below()), null))
                ? RedstoneSide.NONE : RedstoneSide.SIDE;
    }

    /**
     * Mirrors {@code RedStoneWireBlock#canSurviveOn} (1.19.4 {@code :285-287}).
     *
     * <p>Vanilla has no explicit half check here: a bottom half is already never sturdy upwards. The
     * check is kept anyway because it is what the real rule amounts to, and it keeps the port honest
     * against test doubles that answer {@code isFaceSturdy} directly.
     */
    public static boolean canSurviveOn(WorldView world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.HALF) != Half.TOP) {
            return false;
        }
        return world.isFaceSturdy(state, pos, Direction.UP) || state.is(Blocks.HOPPER);
    }

    /**
     * Mirrors {@code RedStoneWireBlock#shouldConnectTo(BlockState, @Nullable Direction)}
     * (1.19.4 {@code :447-456}).
     *
     * <p>From 26.3 vanilla this is {@code state.shouldRedstoneWireConnectTo(level, pos, direction)}
     * (see {@code RedstoneWireBlock:391-393} in {@code d5822ca2a1}), but every override - wire,
     * repeater, observer and the {@code BlockBehaviour} default - evaluates to exactly the expression
     * below, so one ported form covers all six supported versions.
     */
    public static boolean shouldConnectTo(BlockState state, @Nullable Direction direction) {
        if (state.is(Blocks.REDSTONE_WIRE)) {
            return true;
        }
        if (state.is(Blocks.REPEATER)) {
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            return facing == direction || facing.getOpposite() == direction;
        }
        if (state.is(Blocks.OBSERVER)) {
            return direction == state.getValue(ObserverBlock.FACING);
        }
        return state.isSignalSource() && direction != null;
    }

    /**
     * Preconditions for the gate to be observable at all, checked once per {@code (trap door, dir)}
     * pair before the expensive flip probe:
     *
     * <ol>
     *   <li>the influencer is a top-half trap door;</li>
     *   <li>{@code dir} points at a redstone wire ({@code W});</li>
     *   <li>{@code nonNormalCubeAbove} - nothing conductor sits directly on {@code W};</li>
     *   <li>the single-argument {@code shouldConnectTo} holds above the trap door, i.e. the premise
     *       block {@code D} is a wire.</li>
     * </ol>
     *
     * Signal strength is deliberately not part of this: {@code getSignal} is all-or-nothing for the
     * edge, and the flip probe asks the same question. Neither is {@code canSurviveOn(trapDoor)}: that
     * only holds while the trap door is closed, and both segments have to exist in either state. The
     * {@code bl} expression therefore lives inside {@link #connectingSide}, which the engine evaluates
     * twice per direction - once per {@code OPEN} value - so an environment whose {@code bl} ignores
     * {@code OPEN} simply yields two equal results and emits nothing at all.
     */
    public static boolean gate(WorldView world, BlockPos trapDoor, Direction dir) {
        if (!world.isLoaded(trapDoor) || !isGateTrapDoor(world.state(trapDoor))) {
            return false;
        }
        BlockPos wire = trapDoor.relative(dir);
        if (!world.isLoaded(wire) || !ComponentCatalog.isWire(world.state(wire))) {
            return false;
        }
        if (world.isConductor(wire.above())) {
            return false;
        }
        return shouldConnectTo(world.state(trapDoor.above()), null);
    }

    private static void backFill(Map<Direction, RedstoneSide> sides, Direction direction, boolean apply) {
        if (apply && sides.get(direction) == RedstoneSide.NONE) {
            sides.put(direction, RedstoneSide.SIDE);
        }
    }

    private static boolean connected(Map<Direction, RedstoneSide> sides, Direction direction) {
        return sides.get(direction).isConnected();
    }

    private static RedstoneSide side(BlockState wire, Direction direction) {
        return wire.getValue(switch (direction) {
            case NORTH -> BlockStateProperties.NORTH_REDSTONE;
            case SOUTH -> BlockStateProperties.SOUTH_REDSTONE;
            case EAST -> BlockStateProperties.EAST_REDSTONE;
            case WEST -> BlockStateProperties.WEST_REDSTONE;
            default -> throw new IllegalArgumentException("not horizontal: " + direction);
        });
    }

    private static BlockState withOpen(BlockState state, boolean open) {
        return state.hasProperty(BlockStateProperties.OPEN)
                ? state.setValue(BlockStateProperties.OPEN, open) : state;
    }
}
