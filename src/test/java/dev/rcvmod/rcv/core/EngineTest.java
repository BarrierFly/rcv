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

    private static boolean hasEdge(ConnectionGraph graph, EdgeType type, BlockPos a, BlockPos b) {
        for (GraphEdge edge : graph.edges()) {
            if (edge.type != type) {
                continue;
            }
            BlockPos from = graph.node(edge.from).pos;
            BlockPos to = graph.node(edge.to).pos;
            if ((from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void fenceConnectsToFullBlockInBothDirections() {
        BlockPos fence = new BlockPos(0, 0, 0);
        BlockPos stone = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(fence, Blocks.OAK_FENCE.defaultBlockState())
                .set(stone, Blocks.STONE.defaultBlockState())
                .conductor(stone)
                .sturdy(stone);

        assertTrue(hasEdge(compute(world, fence, QueryMode.OUT), EdgeType.SHAPE, fence, stone));
        assertTrue(hasEdge(compute(world, stone, QueryMode.IN), EdgeType.SHAPE, fence, stone));
    }

    @Test
    void fenceDoesNotConnectToIronBars() {
        BlockPos fence = new BlockPos(0, 0, 0);
        BlockPos bars = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(fence, Blocks.OAK_FENCE.defaultBlockState())
                .set(bars, Blocks.IRON_BARS.defaultBlockState());

        assertFalse(hasEdge(compute(world, fence, QueryMode.OUT), EdgeType.SHAPE, fence, bars));
    }

    @Test
    void ironBarsConnectToWallInBothDirections() {
        BlockPos bars = new BlockPos(0, 0, 0);
        BlockPos wall = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(bars, Blocks.IRON_BARS.defaultBlockState())
                .set(wall, Blocks.COBBLESTONE_WALL.defaultBlockState());

        assertTrue(hasEdge(compute(world, bars, QueryMode.OUT), EdgeType.SHAPE, bars, wall));
        assertTrue(hasEdge(compute(world, wall, QueryMode.OUT), EdgeType.SHAPE, bars, wall));
    }

    @Test
    void scaffoldingDependsOnBlockBelowInBothDirections() {
        BlockPos support = new BlockPos(0, 0, 0);
        BlockPos scaffold = new BlockPos(0, 1, 0);
        FakeWorld world = new FakeWorld()
                .set(support, Blocks.OAK_TRAPDOOR.defaultBlockState())
                .sturdy(support)
                .set(scaffold, Blocks.SCAFFOLDING.defaultBlockState());

        assertTrue(hasEdge(compute(world, scaffold, QueryMode.OUT), EdgeType.DISTANCE, scaffold, support));
        assertTrue(hasEdge(compute(world, scaffold, QueryMode.IN), EdgeType.DISTANCE, scaffold, support));
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
        private final Set<BlockPos> sturdy = new HashSet<>();

        FakeWorld set(BlockPos pos, BlockState state) {
            this.states.put(pos, state);
            return this;
        }

        FakeWorld conductor(BlockPos pos) {
            this.conductors.add(pos);
            return this;
        }

        FakeWorld sturdy(BlockPos pos) {
            this.sturdy.add(pos);
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
            return this.sturdy.contains(pos);
        }
    }
}
