package dev.rcvmod.rcv.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.Vec3;
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
        return compute(world, origin, mode, ppMode, ncMode, false);
    }

    private static ConnectionGraph compute(WorldView world, BlockPos origin, QueryMode mode, PpMode ppMode,
                                           NcMode ncMode, boolean dustTrapdoorLegacy) {
        GraphOptions options = new GraphOptions(8, TypeMask.all(), ncMode, ppMode, null,
                GraphOptions.MAX_NODES, GraphOptions.MAX_EDGES, GraphOptions.DEFAULT_RAIL_RANGE,
                dustTrapdoorLegacy);
        return new ConnectionEngine(world, options, mode).compute(origin);
    }

    /** {@code /rcv} with the dust-trapdoor gate active, i.e. a pre-1.20 (or reinstated) environment. */
    private static ConnectionGraph legacy(WorldView world, BlockPos origin, QueryMode mode) {
        return compute(world, origin, mode, PpMode.OFF, NcMode.OFF, true);
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

    /** Front segment: a real {@code T -> W} edge anchored on the trap door itself. */
    private static boolean hasFrontSegment(ConnectionGraph graph, BlockPos trapDoor, BlockPos wire) {
        for (GraphEdge edge : graph.edges()) {
            GraphNode from = graph.node(edge.from);
            if (edge.type == EdgeType.DUST_TRAPDOOR && from.kind == NodeKind.COMPONENT
                    && from.pos.equals(trapDoor) && graph.node(edge.to).pos.equals(wire)) {
                return true;
            }
        }
        return false;
    }

    /** Back segment: anchored on the virtual midpoint node between the trap door and the wire. */
    private static boolean hasBackSegment(ConnectionGraph graph, BlockPos trapDoor, BlockPos wire,
                                          BlockPos downstream) {
        Vec3 expectedAnchor = Vec3.atCenterOf(trapDoor).add(Vec3.atCenterOf(wire)).scale(0.5);
        for (GraphEdge edge : graph.edges()) {
            GraphNode from = graph.node(edge.from);
            if (edge.type == EdgeType.DUST_TRAPDOOR && from.kind == NodeKind.JUNCTION
                    && expectedAnchor.equals(from.center()) && graph.node(edge.to).pos.equals(downstream)) {
                return true;
            }
        }
        return false;
    }

    private static int countEdges(ConnectionGraph graph, EdgeType type) {
        int count = 0;
        for (GraphEdge edge : graph.edges()) {
            if (edge.type == type) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasDustTrapdoorEdgeFrom(ConnectionGraph graph, BlockPos pos) {
        for (GraphEdge edge : graph.edges()) {
            if (edge.type == EdgeType.DUST_TRAPDOOR && graph.node(edge.from).pos.equals(pos)) {
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
    void comparatorSideInputIsTopological() {
        BlockPos comparator = new BlockPos(0, 0, 0);
        BlockPos side = new BlockPos(1, 0, 0);
        BlockState comparatorState = Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);

        // Topology-only: any strong-signal source counts, even one whose direct signal points away.
        FakeWorld torch = new FakeWorld()
                .set(comparator, comparatorState)
                .set(side, Blocks.REDSTONE_TORCH.defaultBlockState());
        assertTrue(hasEdge(compute(torch, side, QueryMode.OUT),
                EdgeType.COMPARATOR_SIDE, side, comparator));

        // A plain conductor is not a signal source, so it is not a control input. Query IN so the
        // engine still calls outgoing(side) for a non-startable block.
        FakeWorld stone = new FakeWorld()
                .set(comparator, comparatorState)
                .set(side, Blocks.STONE.defaultBlockState())
                .conductor(side);
        assertFalse(hasEdge(compute(stone, comparator, QueryMode.IN),
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
    void poweredRailNcCoversBelowAndSlopeAbove() {
        BlockPos rail = new BlockPos(0, 0, 0);
        BlockPos belowTarget = new BlockPos(0, -2, 0);
        BlockPos aboveTarget = new BlockPos(0, 2, 0);
        FakeWorld world = new FakeWorld()
                .set(rail, Blocks.POWERED_RAIL.defaultBlockState()
                        .setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, RailShape.ASCENDING_EAST))
                .set(belowTarget, Blocks.OAK_FENCE.defaultBlockState())
                .set(aboveTarget, Blocks.OAK_FENCE.defaultBlockState());

        ConnectionGraph graph = compute(world, rail, QueryMode.OUT, PpMode.OFF, NcMode.ALL);
        assertTrue(hasEdge(graph, EdgeType.NC, rail, belowTarget));
        assertTrue(hasEdge(graph, EdgeType.NC, rail, aboveTarget));
    }

    @Test
    void detectorRailNcNotifiesConnectedRail() {
        BlockPos detector = new BlockPos(0, 0, 0);
        BlockPos connected = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(detector, Blocks.DETECTOR_RAIL.defaultBlockState())
                .set(connected, Blocks.RAIL.defaultBlockState())
                .railConnections(detector, List.of(connected));

        assertTrue(hasEdge(compute(world, detector, QueryMode.OUT, PpMode.OFF, NcMode.ALL),
                EdgeType.NC, detector, connected));
    }

    @Test
    void repeaterSideInputOnlyFromDiodeFacingIt() {
        BlockPos repeater = new BlockPos(0, 0, 0);
        BlockPos side = new BlockPos(1, 0, 0);
        BlockState repeaterState = Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);

        // A repeater whose output faces west feeds this repeater's east side and locks it.
        FakeWorld facingIn = new FakeWorld()
                .set(repeater, repeaterState)
                .set(side, Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)); // output = WEST
        assertTrue(hasEdge(compute(facingIn, side, QueryMode.OUT),
                EdgeType.REPEATER_SIDE, side, repeater));

        // A repeater whose output faces away does not lock.
        FakeWorld facingAway = new FakeWorld()
                .set(repeater, repeaterState)
                .set(side, Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)); // output = EAST
        assertFalse(hasEdge(compute(facingAway, side, QueryMode.OUT),
                EdgeType.REPEATER_SIDE, side, repeater));

        // A torch is not a diode, so it cannot lock a repeater.
        FakeWorld torch = new FakeWorld()
                .set(repeater, repeaterState)
                .set(side, Blocks.REDSTONE_TORCH.defaultBlockState());
        assertFalse(hasEdge(compute(torch, side, QueryMode.OUT),
                EdgeType.REPEATER_SIDE, side, repeater));
    }

    @Test
    void wireNcReachesTwoHopNeighbours() {
        BlockPos wire = new BlockPos(0, 0, 0);
        BlockPos twoHop = new BlockPos(2, 0, 0);
        FakeWorld world = new FakeWorld()
                .set(wire, Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(twoHop, Blocks.OAK_FENCE.defaultBlockState());

        // The signal-change path updates the 6 neighbours of the wire and of each of its neighbours.
        assertTrue(hasEdge(compute(world, wire, QueryMode.OUT, PpMode.OFF, NcMode.ALL),
                EdgeType.NC, wire, twoHop));
        assertTrue(hasEdge(compute(world, twoHop, QueryMode.IN, PpMode.OFF, NcMode.ALL),
                EdgeType.NC, wire, twoHop));
    }

    @Test
    void pistonMovedBlocksEmitNcAndPp() {
        BlockPos piston = new BlockPos(0, 0, 0);
        BlockPos moved = new BlockPos(1, 0, 0);
        BlockPos movedNeighbour = new BlockPos(1, 0, 1);
        FakeWorld world = new FakeWorld()
                .set(piston, Blocks.PISTON.defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.EAST))
                .set(moved, Blocks.STONE.defaultBlockState())
                .set(movedNeighbour, Blocks.OAK_FENCE.defaultBlockState())
                .piston(new PistonResult(true, List.of(moved), List.of()));

        ConnectionGraph ncGraph = compute(world, piston, QueryMode.OUT, PpMode.OFF, NcMode.ALL);
        assertTrue(hasEdge(ncGraph, EdgeType.PISTON, piston, moved));
        assertTrue(hasEdge(ncGraph, EdgeType.NC, moved, movedNeighbour));

        // NC outranks PP for the same pair, so PP is checked with NC disabled.
        ConnectionGraph ppGraph = compute(world, piston, QueryMode.OUT, PpMode.ALL, NcMode.OFF);
        assertTrue(hasEdge(ppGraph, EdgeType.PP, moved, movedNeighbour));
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

    // ------------------------------------------------------------------ dust-trapdoor gate

    /**
     * Builds the §2.5 scenario from the design plan.
     *
     * <pre>
     *   y=1:  .  D  .
     *   y=0:  .  W  T      W = wire, T = closed top-half trap door, D = the wire resting on T
     * </pre>
     *
     * T sits east of W, so the gate direction is {@code EAST} and the two perpendicular sides of W
     * are north and south.
     */
    private static BlockPos wire() {
        return new BlockPos(0, 0, 0);
    }

    private static BlockPos trapDoor() {
        return new BlockPos(1, 0, 0);
    }

    private static BlockPos premiseWire() {
        return new BlockPos(1, 1, 0);
    }

    private static FakeWorld gateWorld() {
        return new FakeWorld()
                .set(wire(), Blocks.REDSTONE_WIRE.defaultBlockState()
                        .setValue(BlockStateProperties.EAST_REDSTONE, RedstoneSide.SIDE))
                // Note Half's first constant is BOTTOM, so a default trap door state is a bottom half
                // and can never carry the wire above it; the gate needs an explicit top half.
                .set(trapDoor(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.TOP))
                .set(premiseWire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                // The only face that can ever be full upwards on a trap door.
                .sturdyWhenClosed(trapDoor(), Direction.UP);
    }

    /** {@link #gateWorld()} plus §2.5 scenario B's already-connected north side. */
    private static FakeWorld gateWorldScenarioB() {
        return gateWorld()
                .set(wire().north(), Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Test
    void dustTrapdoorLegacyScenarioBProducesFrontAndOneBackSegment() {
        // §2.5 scenario B: the north side is already connected (a repeater), the south side is free.
        BlockPos north = wire().north();
        BlockPos south = wire().south();
        FakeWorld world = gateWorld()
                .set(north, Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
                .set(south, Blocks.REDSTONE_LAMP.defaultBlockState());

        ConnectionGraph graph = legacy(world, trapDoor(), QueryMode.OUT);
        assertTrue(hasFrontSegment(graph, trapDoor(), wire()));
        // Only the south side flips: the north side is SIDE in both states and the back-fill never
        // rewrites an already-connected side.
        assertTrue(hasBackSegment(graph, trapDoor(), wire(), south));
        assertFalse(hasBackSegment(graph, trapDoor(), wire(), north));
        assertEquals(2, countEdges(graph, EdgeType.DUST_TRAPDOOR));
    }

    @Test
    void dustTrapdoorEdgesSurviveTheOpenFlip() {
        // The edges express control, not a current reading, so flipping OPEN must not add or remove
        // them - only the recomputed sides have to change.
        BlockPos south = wire().south();
        FakeWorld closed = gateWorld()
                .set(new BlockPos(0, 0, -1), Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
                .set(south, Blocks.REDSTONE_LAMP.defaultBlockState());
        FakeWorld open = closed.set(trapDoor(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(BlockStateProperties.OPEN, true));

        ConnectionGraph closedGraph = legacy(closed, trapDoor(), QueryMode.OUT);
        ConnectionGraph openGraph = legacy(open, trapDoor(), QueryMode.OUT);

        assertTrue(hasFrontSegment(closedGraph, trapDoor(), wire()));
        assertTrue(hasFrontSegment(openGraph, trapDoor(), wire()));
        assertTrue(hasBackSegment(closedGraph, trapDoor(), wire(), south));
        assertTrue(hasBackSegment(openGraph, trapDoor(), wire(), south));

        // ... while the wire's connection towards the door really did flip. The door sits east of the wire,
        // so the gated side property is EAST.
        assertEquals(RedstoneSide.SIDE,
                DustTrapdoor.wireSides(closed, wire(), true, trapDoor(), false).get(Direction.EAST));
        assertEquals(RedstoneSide.NONE,
                DustTrapdoor.wireSides(closed, wire(), true, trapDoor(), true).get(Direction.EAST));
    }

    @Test
    void dustTrapdoorBackSegmentPolarityIsOppositeToTheFront() {
        // Scenario B, where the north side is already connected: bl6 is false, so the back-fill cannot
        // hide the door's own flip. Scenario A would show SIDE on both sides instead - that masking is
        // covered by dustTrapdoorBackFillCanHideTheGate.
        FakeWorld world = gateWorldScenarioB().set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());
        // Front: closed = SIDE, open = NONE (opening cuts the door's own side).
        assertEquals(RedstoneSide.SIDE,
                DustTrapdoor.wireSides(world, wire(), true, trapDoor(), false).get(Direction.EAST));
        assertEquals(RedstoneSide.NONE,
                DustTrapdoor.wireSides(world, wire(), true, trapDoor(), true).get(Direction.EAST));
        // Back: closed = NONE, open = SIDE (opening turns the free sides on).
        assertEquals(RedstoneSide.NONE,
                DustTrapdoor.wireSides(world, wire(), true, trapDoor(), false).get(Direction.SOUTH));
        assertEquals(RedstoneSide.SIDE,
                DustTrapdoor.wireSides(world, wire(), true, trapDoor(), true).get(Direction.SOUTH));
    }

    @Test
    void dustTrapdoorModernEraProducesNothing() {
        // §2.6: from 1.20 the gate expression is an unconditional `instanceof TrapDoorBlock`, which
        // no longer depends on OPEN, so both recomputations agree and no edge exists. That this falls
        // out of the predicate - rather than from an early `if (!legacy) return` - is the point.
        FakeWorld world = gateWorld().set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());
        ConnectionGraph graph = compute(world, trapDoor(), QueryMode.OUT, PpMode.OFF, NcMode.OFF, false);
        assertEquals(0, countEdges(graph, EdgeType.DUST_TRAPDOOR));
    }

    @Test
    void dustTrapdoorIsReachableFromBothEndsButDoesNotMisreport() {
        FakeWorld world = gateWorldScenarioB().set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());

        assertTrue(hasFrontSegment(legacy(world, trapDoor(), QueryMode.OUT), trapDoor(), wire()));
        assertTrue(hasFrontSegment(legacy(world, wire(), QueryMode.IN), trapDoor(), wire()));

        // The back segment's target is a real block two hops away from the trap door, still inside the
        // ±2 scan the IN walk uses.
        BlockPos south = wire().south();
        assertTrue(hasBackSegment(legacy(world, south, QueryMode.IN), trapDoor(), wire(), south));

        // Directed: the wire never originates one itself. (Walking out from the wire does reach the door
        // via DIRECT_ACTIVATION and therefore shows the door's outgoing edges too - what must not
        // happen is the wire being the source.)
        assertFalse(hasDustTrapdoorEdgeFrom(legacy(world, wire(), QueryMode.OUT), wire()));
        assertFalse(hasDustTrapdoorEdgeFrom(legacy(world, wire(), QueryMode.IN), wire()));
    }

    @Test
    void dustTrapdoorRequiresThePremiseWireAboveTheDoor() {
        FakeWorld noPremise = gateWorld()
                .set(premiseWire(), Blocks.OAK_FENCE.defaultBlockState())
                .set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());
        assertEquals(0, countEdges(legacy(noPremise, trapDoor(), QueryMode.OUT), EdgeType.DUST_TRAPDOOR));

        // ... and nothing conductive may sit on the wire itself, or nonNormalCubeAbove is false.
        FakeWorld covered = gateWorld()
                .set(wire().above(), Blocks.REDSTONE_BLOCK.defaultBlockState())
                .conductor(wire().above())
                .set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());
        assertEquals(0, countEdges(legacy(covered, trapDoor(), QueryMode.OUT), EdgeType.DUST_TRAPDOOR));
    }

    @Test
    void dustTrapdoorIgnoresThePremiseWireItself() {
        // D is only what makes the gate branch reachable; it is never a target. D and W are diagonal,
        // and getConnectingSide only walks the horizontal plane anyway.
        FakeWorld world = gateWorld().set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());
        ConnectionGraph graph = legacy(world, trapDoor(), QueryMode.OUT);
        assertFalse(hasEdge(graph, EdgeType.DUST_TRAPDOOR, trapDoor(), premiseWire()));
        assertFalse(hasEdge(graph, EdgeType.DUST_TRAPDOOR, trapDoor(), new BlockPos(1, 0, -1)));
    }

    @Test
    void dustTrapdoorBottomHalfIsNeverAGate() {
        // Only a closed top half has a full UP face; the bottom half can never carry the wire above.
        FakeWorld world = new FakeWorld()
                .set(wire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(trapDoor(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM))
                .set(premiseWire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .sturdyWhenClosed(trapDoor(), Direction.UP)
                .set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());

        assertEquals(0, countEdges(legacy(world, trapDoor(), QueryMode.OUT), EdgeType.DUST_TRAPDOOR));
    }

    @Test
    void dustTrapdoorOutranksPpOnTheTrapDoorToWirePair() {
        // With PP=ALL the trap door -> wire pair also gets a PP edge; same-pair merging keeps only the
        // highest priority, so a priority >= PP would make the new type invisible by default.
        FakeWorld world = gateWorld()
                .set(new BlockPos(0, 0, -1), Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
                .set(wire().south(), Blocks.REDSTONE_LAMP.defaultBlockState());
        ConnectionGraph graph = compute(world, trapDoor(), QueryMode.OUT, PpMode.ALL, NcMode.OFF, true);

        // PP(14) would have taken this pair; DUST_TRAPDOOR(12) outranks it, so the merge keeps the
        // new type and the PP edge is gone rather than silently hiding it.
        assertTrue(hasFrontSegment(graph, trapDoor(), wire()));
        assertFalse(hasEdge(graph, EdgeType.PP, trapDoor(), wire()));
    }

    @Test
    void dustTrapdoorBothPerpendicularSidesConnectedKeepsOnlyTheFrontSegment() {
        // bl6 (no vertical side connected) is false, so the back-fill cannot push the door's own side
        // back to SIDE and the front flip survives - but the two vertical sides are SIDE either way.
        BlockPos north = wire().north();
        BlockPos south = wire().south();
        FakeWorld world = gateWorld()
                .set(north, Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(south, Blocks.REDSTONE_WIRE.defaultBlockState());

        ConnectionGraph graph = legacy(world, trapDoor(), QueryMode.OUT);
        assertTrue(hasFrontSegment(graph, trapDoor(), wire()));
        assertFalse(hasBackSegment(graph, trapDoor(), wire(), north));
        assertFalse(hasBackSegment(graph, trapDoor(), wire(), south));
    }

    @Test
    void dustTrapdoorOppositeSideConnectedSuppressesTheBackSegment() {
        // bl7 requires the direction opposite the door to be free too; a wire there blocks it while
        // leaving the front flip intact.
        FakeWorld world = gateWorld()
                .set(wire().west(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(new BlockPos(0, 0, 1), Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
                .set(wire().north(), Blocks.REDSTONE_LAMP.defaultBlockState());

        ConnectionGraph graph = legacy(world, trapDoor(), QueryMode.OUT);
        assertTrue(hasFrontSegment(graph, trapDoor(), wire()));
        assertFalse(hasBackSegment(graph, trapDoor(), wire(), wire().north()));
    }

    @Test
    void dustTrapdoorBackFillCanHideTheGate() {
        // §2.5 scenario A: both vertical sides free and the opposite side free, so bl6 holds and the
        // back-fill restores the door's side to SIDE when open. The front segment is therefore NOT
        // emitted even though the raw side flipped, while both vertical sides now flip the other way.
        BlockPos north = wire().north();
        BlockPos south = wire().south();
        FakeWorld world = gateWorld()
                .set(north, Blocks.REDSTONE_LAMP.defaultBlockState())
                .set(south, Blocks.REDSTONE_LAMP.defaultBlockState());

        ConnectionGraph graph = legacy(world, trapDoor(), QueryMode.OUT);
        assertFalse(hasFrontSegment(graph, trapDoor(), wire()));
        assertTrue(hasBackSegment(graph, trapDoor(), wire(), north));
        assertTrue(hasBackSegment(graph, trapDoor(), wire(), south));
    }

    @Test
    void dustTrapdoorStoredDotStateShortCircuitsTheBackFill() {
        // §2.5 scenario D: when the stored sides are all NONE, vanilla's isDot(stored) early return
        // skips the whole back-fill, so opening the door leaves every side at NONE. The result is the
        // opposite of scenario A on the same layout - which is why RCV reads the stored state here
        // instead of assuming "not a dot".
        BlockPos north = wire().north();
        BlockPos south = wire().south();
        FakeWorld world = new FakeWorld()
                .set(wire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(trapDoor(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.TOP))
                .set(premiseWire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .sturdyWhenClosed(trapDoor(), Direction.UP)
                .set(north, Blocks.REDSTONE_LAMP.defaultBlockState())
                .set(south, Blocks.REDSTONE_LAMP.defaultBlockState());

        ConnectionGraph graph = legacy(world, trapDoor(), QueryMode.OUT);
        assertTrue(hasFrontSegment(graph, trapDoor(), wire()));
        assertFalse(hasBackSegment(graph, trapDoor(), wire(), north));
        assertFalse(hasBackSegment(graph, trapDoor(), wire(), south));
    }

    @Test
    void dustTrapdoorBackSegmentTargetSelection() {
        // Build on scenario B so the north side is genuinely free to flip.
        // A wire, a diode or an observer already reads SIDE through shouldConnectTo and can therefore
        // never flip; a plain block and plain air are simply not connected either way.
        for (BlockState filler : List.of(Blocks.REDSTONE_WIRE.defaultBlockState(),
                Blocks.REPEATER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
                Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH),
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.AIR.defaultBlockState())) {
            FakeWorld world = gateWorldScenarioB().set(wire().north(), filler);
            assertFalse(hasBackSegment(legacy(world, trapDoor(), QueryMode.OUT), trapDoor(), wire(), wire().north()),
                    "unexpected back segment for " + filler);
        }

        // A plain redstone conductor counts...
        FakeWorld conductor = gateWorldScenarioB()
                .set(wire().north(), Blocks.STONE.defaultBlockState()).conductor(wire().north());
        assertTrue(hasBackSegment(legacy(conductor, trapDoor(), QueryMode.OUT), trapDoor(), wire(), wire().north()));

        // ... and so does a plain consumer.
        FakeWorld consumer = gateWorldScenarioB().set(wire().north(), Blocks.REDSTONE_LAMP.defaultBlockState());
        assertTrue(hasBackSegment(legacy(consumer, trapDoor(), QueryMode.OUT), trapDoor(), wire(), wire().north()));
    }

    @Test
    void dustTrapdoorDownstreamTakesPartInTheRestOfTheGraphNormally() {
        // The gated downstream is an ordinary node: the new edge must not isolate it from the
        // connections it already had.
        BlockPos lamp = wire().north();
        FakeWorld world = gateWorldScenarioB()
                .set(lamp, Blocks.REDSTONE_LAMP.defaultBlockState())
                .set(lamp.east(), Blocks.LEVER.defaultBlockState());

        ConnectionGraph graph = legacy(world, lamp, QueryMode.IN);
        assertTrue(hasBackSegment(graph, trapDoor(), wire(), lamp));
        assertTrue(hasEdge(graph, EdgeType.DIRECT_ACTIVATION, lamp.east(), lamp));
    }

    @Test
    void dustTrapdoorSegmentsCoexistWithTheWiresOwnEdge() {
        // With the door currently open the wire really does point at the downstream lamp, so W has an
        // edge of its own; the back segment must not swallow it (and vice versa).
        BlockPos north = wire().north();
        FakeWorld world = new FakeWorld()
                .set(wire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(trapDoor(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.TOP)
                        .setValue(BlockStateProperties.OPEN, true))
                .set(premiseWire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .sturdyWhenClosed(trapDoor(), Direction.UP)
                .set(north, Blocks.REDSTONE_LAMP.defaultBlockState())
                // Keep bl6 false so the front flip is not masked by the back-fill.
                .set(wire().south(), Blocks.REPEATER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));

        ConnectionGraph graph = legacy(world, trapDoor(), QueryMode.OUT);
        assertTrue(hasBackSegment(graph, trapDoor(), wire(), north));
        assertTrue(hasEdge(graph, EdgeType.DIRECT_ACTIVATION, wire(), north));
    }

    @Test
    void dustTrapdoorJunctionNodeIsNeverTraversed() {
        // A junction has no block state; reading the midpoint's real block would invent edges.
        BlockPos north = wire().north();
        FakeWorld world = gateWorld().set(north, Blocks.REDSTONE_LAMP.defaultBlockState());

        ConnectionGraph graph = legacy(world, north, QueryMode.IN);
        int junctions = 0;
        for (GraphNode node : graph.nodes()) {
            if (node.kind != NodeKind.JUNCTION) {
                continue;
            }
            junctions++;
            for (GraphEdge edge : graph.edges()) {
                if (edge.from == node.id) {
                    assertEquals(EdgeType.DUST_TRAPDOOR, edge.type);
                }
            }
        }
        assertEquals(1, junctions);
        // The graph never learned about the trap door's neighbours on its own initiative.
        assertFalse(graph.nodes().stream().anyMatch(n -> n.pos.equals(wire().south())));
    }


    @Test
    void wirePowerIsRecomputedRatherThanReadFromTheStoredShape() {
        // A1: vanilla's getSignal asks getConnectionState, so a wire whose stored shape is stale still
        // emits according to the live sides. A full conductor neighbour is exactly the case where the
        // two disagree - vanilla reports NONE even if the stored property claims SIDE. (A redstone
        // block would not do: being a signal source, a wire points *into* it.)
        //
        // The wire north and west of W keep both back-fill guards false, so the NONE the live
        // computation produces for EAST survives instead of being promoted back to SIDE.
        BlockPos wire = wire();
        FakeWorld stale = new FakeWorld()
                .set(wire, Blocks.REDSTONE_WIRE.defaultBlockState()
                        .setValue(BlockStateProperties.EAST_REDSTONE, RedstoneSide.SIDE))
                .set(wire.east(), Blocks.STONE.defaultBlockState())
                .conductor(wire.east())
                .set(wire.north(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(wire.west(), Blocks.REDSTONE_WIRE.defaultBlockState());

        assertEquals(RedstoneSide.NONE, DustTrapdoor.wireSides(stale, wire, true).get(Direction.EAST));
        assertFalse(hasEdge(legacy(stale, wire, QueryMode.OUT), EdgeType.DIRECT_ACTIVATION, wire, wire.east()));
    }

    @Test
    void dustTrapdoorEraProbeRunsAgainstTheRealVanillaImplementation() {
        // Every supported version runs this test task, so it is a genuine cross-version check that the
        // probe's fake BlockGetter is complete enough to drive the loaded RedStoneWireBlock, and that
        // the two eras are distinguishable through it at all.
        Boolean probed = DustTrapdoorEra.probe();
        assertNotNull(probed, "the behaviour probe must produce a verdict, not an exception");
        // Verified by running this task on every supported version: 1.19.4 reports legacy, 1.20.1 /
        // 1.21.1 / 1.21.10 / 1.21.11 / 26.3 report modern - i.e. the probe needs no version table and
        // correctly follows the gate expression the loaded game actually runs.

        // The same question answered through the ported predicate: an open top-half trap door drops
        // the wire's connection to it before 1.20 and keeps it afterwards.
        FakeWorld probeWorld = new FakeWorld()
                .set(wire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(trapDoor(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.TOP)
                        .setValue(BlockStateProperties.OPEN, true))
                .set(premiseWire(), Blocks.REDSTONE_WIRE.defaultBlockState())
                .set(trapDoor().below(), Blocks.STONE.defaultBlockState())
                .sturdyWhenClosed(trapDoor(), Direction.UP);
        assertEquals(RedstoneSide.NONE,
                DustTrapdoor.wireSides(probeWorld, wire(), true).get(Direction.EAST));
        assertEquals(RedstoneSide.SIDE,
                DustTrapdoor.wireSides(probeWorld, wire(), false).get(Direction.EAST));
    }

    private static final class FakeWorld implements WorldView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> conductors = new HashSet<>();
        private final Set<BlockPos> sturdyAlways = new HashSet<>();
        private final Map<BlockPos, Set<Direction>> sturdyWhenOpen = new HashMap<>();
        private final Map<BlockPos, Set<Direction>> sturdyWhenClosed = new HashMap<>();
        private final Map<BlockPos, List<BlockPos>> railConnections = new HashMap<>();
        private PistonResult pistonResult = PistonResult.empty();

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

        FakeWorld piston(PistonResult result) {
            this.pistonResult = result;
            return this;
        }

        FakeWorld railConnections(BlockPos pos, List<BlockPos> connections) {
            this.railConnections.put(pos, connections);
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
        public BlockEntity blockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public List<ItemFrame> itemFrames(BlockPos pos, Direction face) {
            return List.of();
        }

        @Override
        public PistonResult pistonStructure(BlockPos pos, Direction facing, boolean extending) {
            return this.pistonResult;
        }

        @Override
        public List<BlockPos> railConnections(BlockPos railPos) {
            return this.railConnections.getOrDefault(railPos, List.of());
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
