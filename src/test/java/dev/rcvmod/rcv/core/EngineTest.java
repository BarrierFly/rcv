package dev.rcvmod.rcv.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Engine-level tests over a fake {@link WorldView}: IN/OUT symmetry, SHAPE, DISTANCE and PP. */
class EngineTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ConnectionGraph compute(WorldView world, BlockPos origin, QueryMode mode) {
        return compute(world, origin, mode, PpMode.OFF);
    }

    private static ConnectionGraph compute(WorldView world, BlockPos origin, QueryMode mode, PpMode ppMode) {
        return compute(world, origin, mode, ppMode, NcMode.OFF);
    }

    private static ConnectionGraph compute(WorldView world, BlockPos origin, QueryMode mode, PpMode ppMode,
                                           NcMode ncMode) {
        GraphOptions options = new GraphOptions(8, TypeMask.all(), ncMode, ppMode, null,
                GraphOptions.MAX_NODES, GraphOptions.MAX_EDGES);
        return new ConnectionEngine(world, options, mode).compute(origin);
    }

    private static boolean hasEdge(ConnectionGraph graph, EdgeType type, BlockPos from, BlockPos to) {
        for (GraphEdge edge : graph.edges()) {
            if (edge.type == type && graph.node(edge.from).pos.equals(from)
                    && graph.node(edge.to).pos.equals(to)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void openTrapDoorInfluencesAdjacentFenceEvenWhenCurrentlyClosed() {
        BlockPos trapDoor = new BlockPos(1, 0, 0);
        BlockPos fence = new BlockPos(0, 0, 0);
        // The trap door only becomes sturdy towards the fence when open; it is currently closed.
        FakeWorld world = new FakeWorld()
                .set(trapDoor, Blocks.OAK_TRAPDOOR.defaultBlockState())
                .set(fence, Blocks.OAK_FENCE.defaultBlockState())
                .sturdyWhenOpen(trapDoor, Direction.WEST);

        assertTrue(hasEdge(compute(world, trapDoor, QueryMode.OUT), EdgeType.SHAPE, trapDoor, fence));
        assertTrue(hasEdge(compute(world, fence, QueryMode.IN), EdgeType.SHAPE, trapDoor, fence));
        assertFalse(hasEdge(compute(world, fence, QueryMode.OUT), EdgeType.SHAPE, trapDoor, fence));
        assertFalse(hasEdge(compute(world, trapDoor, QueryMode.IN), EdgeType.SHAPE, trapDoor, fence));
    }

    @Test
    void fenceDoesNotConnectToFullBlock() {
        BlockPos fence = new BlockPos(0, 0, 0);
        BlockPos stone = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(fence, Blocks.OAK_FENCE.defaultBlockState())
                .set(stone, Blocks.STONE.defaultBlockState())
                .conductor(stone)
                .sturdy(stone);

        assertFalse(hasEdge(compute(world, fence, QueryMode.OUT), EdgeType.SHAPE, fence, stone));
    }

    @Test
    void fenceDoesNotConnectToIronBars() {
        BlockPos fence = new BlockPos(0, 0, 0);
        BlockPos bars = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(fence, Blocks.OAK_FENCE.defaultBlockState())
                .set(bars, Blocks.IRON_BARS.defaultBlockState())
                .sturdy(bars);

        assertFalse(hasEdge(compute(world, fence, QueryMode.OUT), EdgeType.SHAPE, fence, bars));
    }

    @Test
    void ironBarsDoNotConnectToWall() {
        BlockPos bars = new BlockPos(0, 0, 0);
        BlockPos wall = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(bars, Blocks.IRON_BARS.defaultBlockState())
                .set(wall, Blocks.COBBLESTONE_WALL.defaultBlockState());

        assertFalse(hasEdge(compute(world, bars, QueryMode.OUT), EdgeType.SHAPE, bars, wall));
    }

    @Test
    void upperWallInfluencesLowerWallOnly() {
        BlockPos lower = new BlockPos(0, 0, 0);
        BlockPos upper = new BlockPos(0, 1, 0);
        FakeWorld world = new FakeWorld()
                .set(lower, Blocks.COBBLESTONE_WALL.defaultBlockState())
                .set(upper, Blocks.COBBLESTONE_WALL.defaultBlockState());

        assertTrue(hasEdge(compute(world, upper, QueryMode.OUT), EdgeType.SHAPE, upper, lower));
        assertTrue(hasEdge(compute(world, lower, QueryMode.IN), EdgeType.SHAPE, upper, lower));
        assertFalse(hasEdge(compute(world, lower, QueryMode.OUT), EdgeType.SHAPE, upper, lower));
        assertFalse(hasEdge(compute(world, upper, QueryMode.IN), EdgeType.SHAPE, upper, lower));
    }

    @Test
    void horizontalWallsDoNotShareShapeDependency() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(a, Blocks.COBBLESTONE_WALL.defaultBlockState())
                .set(b, Blocks.COBBLESTONE_WALL.defaultBlockState());

        assertFalse(hasEdge(compute(world, a, QueryMode.OUT), EdgeType.SHAPE, a, b));
        assertFalse(hasEdge(compute(world, b, QueryMode.OUT), EdgeType.SHAPE, a, b));
    }

    @Test
    void openTrapDoorSupportsScaffoldingAbove() {
        BlockPos support = new BlockPos(0, 0, 0);
        BlockPos scaffold = new BlockPos(0, 1, 0);
        // Open (so not currently sturdy upwards), but closing it would make it sturdy.
        FakeWorld world = new FakeWorld()
                .set(support, Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.OPEN, true))
                .set(scaffold, Blocks.SCAFFOLDING.defaultBlockState())
                .sturdyWhenClosed(support, Direction.UP);

        assertTrue(hasEdge(compute(world, support, QueryMode.OUT), EdgeType.DISTANCE, support, scaffold));
        assertTrue(hasEdge(compute(world, scaffold, QueryMode.IN), EdgeType.DISTANCE, support, scaffold));
        assertFalse(hasEdge(compute(world, scaffold, QueryMode.OUT), EdgeType.DISTANCE, support, scaffold));
        assertFalse(hasEdge(compute(world, support, QueryMode.IN), EdgeType.DISTANCE, support, scaffold));
    }

    @Test
    void ppObserverOnlyOriginatesFromTheBlockInFront() {
        BlockPos front = new BlockPos(0, 0, 0);
        BlockPos observer = new BlockPos(1, 0, 0);
        BlockState observerState = Blocks.OBSERVER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST);
        FakeWorld world = new FakeWorld()
                .set(front, Blocks.REDSTONE_LAMP.defaultBlockState())
                .set(observer, observerState);

        assertTrue(hasEdge(compute(world, front, QueryMode.OUT, PpMode.OBSERVER_ONLY),
                EdgeType.PP, front, observer));
        assertTrue(hasEdge(compute(world, observer, QueryMode.IN, PpMode.OBSERVER_ONLY),
                EdgeType.PP, front, observer));
        assertFalse(hasEdge(compute(world, observer, QueryMode.OUT, PpMode.OBSERVER_ONLY),
                EdgeType.PP, front, observer));
    }

    @Test
    void tripwireOnlyConnectsToHookFacingIt() {
        BlockPos wire = new BlockPos(0, 0, 0);
        BlockPos hook = new BlockPos(1, 0, 0);

        FakeWorld facingWire = new FakeWorld()
                .set(wire, Blocks.TRIPWIRE.defaultBlockState())
                .set(hook, Blocks.TRIPWIRE_HOOK.defaultBlockState()
                        .setValue(TripWireHookBlock.FACING, Direction.WEST));
        assertTrue(hasEdge(compute(facingWire, hook, QueryMode.OUT), EdgeType.TRIPWIRE, wire, hook));

        FakeWorld facingAway = new FakeWorld()
                .set(wire, Blocks.TRIPWIRE.defaultBlockState())
                .set(hook, Blocks.TRIPWIRE_HOOK.defaultBlockState()
                        .setValue(TripWireHookBlock.FACING, Direction.EAST));
        assertFalse(hasEdge(compute(facingAway, hook, QueryMode.OUT), EdgeType.TRIPWIRE, wire, hook));
    }

    @Test
    void comparatorSideInputNeedsControlInput() {
        BlockPos comparator = new BlockPos(0, 0, 0);
        BlockPos side = new BlockPos(1, 0, 0);
        BlockState comparatorState = Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);

        // A standing torch emits no strong signal sideways, so it must not become a side input.
        FakeWorld noSignal = new FakeWorld()
                .set(comparator, comparatorState)
                .set(side, Blocks.REDSTONE_TORCH.defaultBlockState());
        assertFalse(hasEdge(compute(noSignal, side, QueryMode.OUT),
                EdgeType.COMPARATOR_SIDE, side, comparator));

        // A redstone block does provide 15 as a control input regardless of direction.
        FakeWorld withSignal = new FakeWorld()
                .set(comparator, comparatorState)
                .set(side, Blocks.REDSTONE_BLOCK.defaultBlockState())
                .controlInput(side, 15);
        assertTrue(hasEdge(compute(withSignal, side, QueryMode.OUT),
                EdgeType.COMPARATOR_SIDE, side, comparator));
    }

    @Test
    void ncOnlyFromSignalChangingBlocks() {
        BlockPos lever = new BlockPos(0, 0, 0);
        BlockPos fence = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(lever, Blocks.LEVER.defaultBlockState())
                .set(fence, Blocks.OAK_FENCE.defaultBlockState());
        assertTrue(hasEdge(compute(world, lever, QueryMode.OUT, PpMode.OFF, NcMode.ALL),
                EdgeType.NC, lever, fence));

        // A fence gate changes shape (flag 2), not signal, so it must not emit NC.
        BlockPos gate = new BlockPos(3, 0, 0);
        BlockPos gateLamp = new BlockPos(4, 0, 0);
        FakeWorld gateWorld = new FakeWorld()
                .set(gate, Blocks.OAK_FENCE_GATE.defaultBlockState())
                .set(gateLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        assertFalse(hasEdge(compute(gateWorld, gate, QueryMode.OUT, PpMode.OFF, NcMode.ALL),
                EdgeType.NC, gate, gateLamp));
    }

    @Test
    void ncRepeaterUpdatesOutputAndItsNeighbours() {
        BlockPos repeater = new BlockPos(0, 0, 0);
        BlockPos front = new BlockPos(0, 0, 1);
        BlockPos frontSide = new BlockPos(1, 0, 1);
        FakeWorld world = new FakeWorld()
                .set(repeater, Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
                .set(front, Blocks.OAK_FENCE.defaultBlockState())
                .set(frontSide, Blocks.OAK_FENCE.defaultBlockState());

        ConnectionGraph graph = compute(world, repeater, QueryMode.OUT, PpMode.OFF, NcMode.ALL);
        assertTrue(hasEdge(graph, EdgeType.NC, repeater, front));
        assertTrue(hasEdge(graph, EdgeType.NC, repeater, frontSide));
        assertTrue(hasEdge(compute(world, front, QueryMode.IN, PpMode.OFF, NcMode.ALL),
                EdgeType.NC, repeater, front));
    }

    @Test
    void openFenceGateSupportsScaffoldingAbove() {
        BlockPos gate = new BlockPos(0, 0, 0);
        BlockPos scaffold = new BlockPos(0, 1, 0);
        FakeWorld world = new FakeWorld()
                .set(gate, Blocks.OAK_FENCE_GATE.defaultBlockState()
                        .setValue(BlockStateProperties.OPEN, true))
                .set(scaffold, Blocks.SCAFFOLDING.defaultBlockState())
                .sturdyWhenClosed(gate, Direction.UP);

        assertTrue(hasEdge(compute(world, gate, QueryMode.OUT), EdgeType.DISTANCE, gate, scaffold));
        assertTrue(hasEdge(compute(world, scaffold, QueryMode.IN), EdgeType.DISTANCE, gate, scaffold));
        assertFalse(hasEdge(compute(world, scaffold, QueryMode.OUT), EdgeType.DISTANCE, gate, scaffold));
    }

    @Test
    void wireIndirectShapeUpdateTargetsDiagonalWire() {
        BlockPos wire = new BlockPos(0, 0, 0);
        BlockPos side = new BlockPos(1, 0, 0);
        BlockPos lower = new BlockPos(1, -1, 0);
        BlockState wireState = Blocks.REDSTONE_WIRE.defaultBlockState()
                .setValue(BlockStateProperties.EAST_REDSTONE, RedstoneSide.SIDE);
        FakeWorld world = new FakeWorld()
                .set(wire, wireState)
                .set(side, Blocks.STONE.defaultBlockState())
                .conductor(side)
                .set(lower, Blocks.REDSTONE_WIRE.defaultBlockState());

        assertTrue(hasEdge(compute(world, wire, QueryMode.OUT, PpMode.ALL),
                EdgeType.PP, wire, lower));

        // The previous same-level diagonal approximation is not part of the vanilla update.
        BlockPos diagonal = new BlockPos(1, 0, 1);
        FakeWorld sameLevel = new FakeWorld()
                .set(wire, wireState)
                .set(side, Blocks.STONE.defaultBlockState())
                .conductor(side)
                .set(diagonal, Blocks.REDSTONE_WIRE.defaultBlockState());
        assertFalse(hasEdge(compute(sameLevel, wire, QueryMode.OUT, PpMode.ALL),
                EdgeType.PP, wire, diagonal));
    }

    private static final class FakeWorld implements WorldView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> conductors = new HashSet<>();
        private final Set<BlockPos> sturdyAlways = new HashSet<>();
        private final Map<BlockPos, Set<Direction>> sturdyWhenOpen = new HashMap<>();
        private final Map<BlockPos, Set<Direction>> sturdyWhenClosed = new HashMap<>();
        private final Map<BlockPos, Integer> controlInputs = new HashMap<>();

        FakeWorld set(BlockPos pos, BlockState state) {
            this.states.put(pos, state);
            return this;
        }

        FakeWorld conductor(BlockPos pos) {
            this.conductors.add(pos);
            return this;
        }

        FakeWorld sturdy(BlockPos pos) {
            this.sturdyAlways.add(pos);
            return this;
        }

        FakeWorld sturdyWhenOpen(BlockPos pos, Direction... faces) {
            this.sturdyWhenOpen.computeIfAbsent(pos, key -> new HashSet<>()).addAll(List.of(faces));
            return this;
        }

        FakeWorld sturdyWhenClosed(BlockPos pos, Direction... faces) {
            this.sturdyWhenClosed.computeIfAbsent(pos, key -> new HashSet<>()).addAll(List.of(faces));
            return this;
        }

        FakeWorld controlInput(BlockPos pos, int value) {
            this.controlInputs.put(pos, value);
            return this;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return true;
        }

        @Override
        public boolean isClientSide() {
            return false;
        }

        @Override
        public BlockState state(BlockPos pos) {
            return this.states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean isConductor(BlockPos pos) {
            return this.conductors.contains(pos);
        }

        @Override
        public int weakSignalTo(BlockPos emitter, Direction dirFromEmitterToReceiver) {
            return 0;
        }

        @Override
        public int controlInputSignal(BlockPos emitterPos, Direction dirFromReceiverToEmitter, boolean diodesOnly) {
            return this.controlInputs.getOrDefault(emitterPos, 0);
        }

        @Override
        public BlockEntity blockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public List<ItemFrame> itemFrames(BlockPos pos, Direction face) {
            return List.of();
        }

        @Override
        public PistonResult pistonStructure(BlockPos pos, Direction facing, boolean extending) {
            return PistonResult.empty();
        }

        @Override
        public List<BlockPos> railConnections(BlockPos railPos) {
            return List.of();
        }

        @Override
        public boolean isFaceSturdy(BlockPos pos, Direction face) {
            return isFaceSturdy(state(pos), pos, face);
        }

        @Override
        public boolean isFaceSturdy(BlockState state, BlockPos pos, Direction face) {
            if (this.sturdyAlways.contains(pos)) {
                return true;
            }
            if (state.getOptionalValue(BlockStateProperties.OPEN).orElse(false)) {
                return this.sturdyWhenOpen.getOrDefault(pos, Set.of()).contains(face);
            }
            return this.sturdyWhenClosed.getOrDefault(pos, Set.of()).contains(face);
        }
    }
}
