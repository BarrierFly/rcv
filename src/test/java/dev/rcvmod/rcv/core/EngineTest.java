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
        GraphOptions options = new GraphOptions(8, TypeMask.all(), NcMode.OFF, ppMode, null,
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

    private static final class FakeWorld implements WorldView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> conductors = new HashSet<>();
        private final Set<BlockPos> sturdyAlways = new HashSet<>();
        private final Map<BlockPos, Set<Direction>> sturdyWhenOpen = new HashMap<>();
        private final Map<BlockPos, Set<Direction>> sturdyWhenClosed = new HashMap<>();

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
        public int directSignalTo(BlockPos emitter, Direction dirFromEmitterToReceiver) {
            return 0;
        }

        @Override
        public int controlInputSignal(BlockPos emitterPos, Direction dirFromReceiverToEmitter, boolean diodesOnly) {
            return 0;
        }

        @Override
        public boolean hasNeighborSignal(BlockPos pos) {
            return false;
        }

        @Override
        public int bestNeighborSignal(BlockPos pos) {
            return 0;
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
