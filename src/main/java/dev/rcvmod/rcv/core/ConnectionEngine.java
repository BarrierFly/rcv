package dev.rcvmod.rcv.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.Nullable;

/** Computes the reachable subgraph around an origin (§4). */
public final class ConnectionEngine {

    /** Vanilla pistons can push/pull up to 12 blocks. */
    private static final int PISTON_RANGE = 12;

    private final WorldView world;
    private final GraphOptions options;
    private final QueryMode mode;
    private final ConnectionGraph graph;
    private final Map<BlockPos, List<Candidate>> outgoingCache = new HashMap<>();
    private final Set<BlockPos> visited = new HashSet<>();

    public ConnectionEngine(WorldView world, GraphOptions options, QueryMode mode) {
        this.world = world;
        this.options = options;
        this.mode = mode;
        this.graph = new ConnectionGraph(mode);
    }

    public ConnectionGraph compute(BlockPos origin) {
        BlockState originState = this.world.state(origin);
        boolean component = ComponentCatalog.isComponent(originState);
        boolean conductor = this.world.isConductor(origin);

        NodeKind originKind = component ? NodeKind.COMPONENT : (conductor ? NodeKind.CONDUCTOR : NodeKind.COMPONENT);
        Set<Role> roles = ComponentCatalog.roles(originState);
        if (conductor) {
            roles.add(Role.CONDUCTOR);
        }
        int originId = this.graph.addNode(origin, ComponentCatalog.blockId(originState), originKind, roles, 0);
        this.graph.setOrigin(originId);
        this.visited.add(origin);

        boolean startable = component || (conductor && this.mode == QueryMode.IN);
        if (!startable) {
            return this.graph;
        }

        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            GraphNode node = this.graph.node(this.graph.idOf(pos));
            if (node == null || node.depth >= this.options.depth) {
                continue;
            }
            List<Candidate> candidates = this.mode == QueryMode.OUT ? this.outgoing(pos) : this.incoming(pos);
            for (Candidate c : candidates) {
                BlockPos other = c.from().equals(pos) ? c.to() : c.from();
                if (!this.world.isLoaded(other)) {
                    continue;
                }
                if (this.options.region != null && (!this.options.region.contains(other)
                        || !this.options.region.contains(pos))) {
                    continue;
                }
                if (this.graph.nodeCount() >= this.options.maxNodes || this.graph.edgeCount() >= this.options.maxEdges) {
                    this.graph.markTruncated();
                    return this.graph;
                }
                int fromId = this.addNodeFor(c.from(), c.targetKind(), node.depth + 1);
                int toId = this.addNodeFor(c.to(), c.targetKind(), node.depth + 1);
                for (BlockPos via : c.via()) {
                    this.graph.addNode(via, ComponentCatalog.blockId(this.world.state(via)), NodeKind.VIA,
                            Set.of(), node.depth);
                }
                this.graph.addEdge(new GraphEdge(fromId, toId, c.type(), c.directed(), c.via(), c.dir(), c.port()));
                if (this.visited.add(other)) {
                    queue.add(other);
                }
            }
        }
        this.graph.sortForRender();
        return this.graph;
    }

    private int addNodeFor(BlockPos pos, @Nullable NodeKind hint, int depth) {
        BlockState state = this.world.state(pos);
        NodeKind kind;
        if (hint != null) {
            kind = hint;
        } else if (ComponentCatalog.isComponent(state)) {
            kind = NodeKind.COMPONENT;
        } else if (this.world.isConductor(pos)) {
            kind = NodeKind.CONDUCTOR;
        } else {
            kind = NodeKind.COMPONENT;
        }
        Set<Role> roles = ComponentCatalog.roles(state);
        if (this.world.isConductor(pos)) {
            roles.add(Role.CONDUCTOR);
        }
        return this.graph.addNode(pos, ComponentCatalog.blockId(state), kind, roles, depth);
    }

    // ------------------------------------------------------------------ outgoing

    private List<Candidate> outgoing(BlockPos pos) {
        List<Candidate> cached = this.outgoingCache.get(pos);
        if (cached != null) {
            return cached;
        }
        List<Candidate> result = new ArrayList<>();
        if (!this.world.isLoaded(pos)) {
            this.outgoingCache.put(pos, result);
            return result;
        }
        BlockState state = this.world.state(pos);
        if (state.isAir()) {
            this.outgoingCache.put(pos, result);
            return result;
        }

        boolean emits = state.isSignalSource();
        for (Direction k : Direction.values()) {
            BlockPos r = pos.relative(k);
            if (!this.world.isLoaded(r)) {
                continue;
            }
            BlockState rs = this.world.state(r);
            Direction dirRtoA = k.getOpposite();

            if (ComponentCatalog.isWire(rs)) {
                if (ComponentCatalog.isWire(state)) {
                    // Wire <-> wire only connects horizontally (climbing is modelled via the supporting block).
                    if (k.getAxis() != Direction.Axis.Y) {
                        this.addUndirected(result, pos, r, EdgeType.CIRCUIT, null, dirRtoA);
                    }
                } else if (wireAccepts(state, dirRtoA) && emitsToward(pos, state, k)) {
                    this.add(result, pos, r, EdgeType.CIRCUIT, true, null, null, dirRtoA, null);
                }
                continue;
            }

            if (ComponentCatalog.isDiode(rs)) {
                Direction f = ComponentCatalog.inputFacing(rs);
                if (f != null) {
                    if (k == f.getOpposite() && (emits || ComponentCatalog.isWire(state)) && emitsToward(pos, state, k)) {
                        this.add(result, pos, r, EdgeType.CIRCUIT, true, null, null, f, null);
                    } else if (ComponentCatalog.isComparator(rs) && (k == f.getClockWise() || k == f.getCounterClockWise())
                            && (emits || ComponentCatalog.isWire(state)) && emitsToward(pos, state, k)) {
                        String port = k == f.getClockWise() ? "SIDE_L" : "SIDE_R";
                        this.add(result, pos, r, EdgeType.COMPARATOR_SIDE, true, null, null, f, port);
                    }
                }
                continue;
            }

            if (ComponentCatalog.isPoweredRail(rs)) {
                if (emits && emitsToward(pos, state, k)) {
                    this.add(result, pos, r, EdgeType.RAIL, true, null, null, k, null);
                }
                continue;
            }

            if (emits && emitsToward(pos, state, k) && ComponentCatalog.isConsumer(rs)) {
                if (ComponentCatalog.isPiston(rs)) {
                    Direction f = ComponentCatalog.inputFacing(rs);
                    if (f != null && k == f.getOpposite()) {
                        // Pistons ignore the neighbour in their facing direction (that side is the head).
                        continue;
                    }
                }
                this.add(result, pos, r, EdgeType.DIRECT_ACTIVATION, true, null, null, k, null);
            }
        }

        // Wire climbing: a wire connects diagonally to a wire on top of an adjacent support block.
        // Over a redstone conductor the link is bidirectional; over a non-conductor it is one-way up.
        if (ComponentCatalog.isWire(state)) {
            boolean uncovered = !this.world.isConductor(pos.above());
            for (Direction k : Direction.Plane.HORIZONTAL) {
                BlockPos mid = pos.relative(k);
                if (!this.world.isLoaded(mid) || !this.world.isFaceSturdy(mid, Direction.UP)) {
                    continue;
                }
                BlockPos up = mid.above();
                if (uncovered && this.world.isLoaded(up) && ComponentCatalog.isWire(this.world.state(up))) {
                    if (this.world.isConductor(mid)) {
                        this.addUndirected(result, pos, up, EdgeType.CIRCUIT, null, k);
                    } else {
                        this.add(result, pos, up, EdgeType.CIRCUIT, true, null, null, k, null);
                    }
                }
            }
            // Downhill only over a redstone conductor, and only when the lower wire is not covered.
            BlockPos base = pos.below();
            if (this.world.isLoaded(base) && this.world.isConductor(base)) {
                for (Direction k : Direction.Plane.HORIZONTAL) {
                    BlockPos down = base.relative(k);
                    if (this.world.isLoaded(down) && ComponentCatalog.isWire(this.world.state(down))
                            && !this.world.isConductor(down.above())) {
                        this.addUndirected(result, pos, down, EdgeType.CIRCUIT, null, k);
                    }
                }
            }
        }

        // ANALOG: comparator reading this block from its input side.
        for (Direction k : Direction.values()) {
            BlockPos r = pos.relative(k);
            if (!this.world.isLoaded(r)) {
                continue;
            }
            BlockState rs = this.world.state(r);
            if (ComponentCatalog.isComparator(rs) && ComponentCatalog.inputFacing(rs) == k.getOpposite()) {
                if (state.hasAnalogOutputSignal()) {
                    this.add(result, pos, r, EdgeType.ANALOG, true, null, null, k, null);
                }
            }
        }

        // ANALOG via a conductor + item frame branch: A is two blocks in front of the comparator.
        for (Direction k : Direction.values()) {
            BlockPos mid = pos.relative(k);
            if (!this.world.isLoaded(mid) || !this.world.isConductor(mid)) {
                continue;
            }
            BlockPos t = mid.relative(k);
            if (!this.world.isLoaded(t)) {
                continue;
            }
            BlockState ts = this.world.state(t);
            Direction f = ComponentCatalog.inputFacing(ts);
            if (ComponentCatalog.isComparator(ts) && f == k.getOpposite()) {
                // Vanilla queries the item frame at the far block (two in front of the comparator),
                // facing the same direction as the comparator's input side.
                boolean frame = !this.world.itemFrames(pos, f).isEmpty();
                if (state.hasAnalogOutputSignal() || frame) {
                    this.add(result, pos, t, EdgeType.ANALOG, true, List.of(mid), null, k, null);
                }
            }
        }

        // CHARGE: a source/transmitter charges an adjacent conductor (weakly or strongly); a strongly
        // charged conductor then powers neighbouring components through itself (via).
        for (Direction k : Direction.values()) {
            BlockPos c = pos.relative(k);
            if (!this.world.isLoaded(c) || !this.world.isConductor(c)) {
                continue;
            }
            if (!emits || !emitsToward(pos, state, k)) {
                continue;
            }
            this.add(result, pos, c, EdgeType.CHARGE, true, null, null, k, null);
            for (Direction m : Direction.values()) {
                if (m == k.getOpposite()) {
                    continue;
                }
                BlockPos t = c.relative(m);
                if (!this.world.isLoaded(t)) {
                    continue;
                }
                BlockState ts = this.world.state(t);
                if (!canReceiveCharge(ts, m, ComponentCatalog.isWire(state))) {
                    continue;
                }
                this.add(result, pos, t, EdgeType.CHARGE, true, List.of(c), null, m, null);
            }
        }

        // PISTON.
        if (ComponentCatalog.isPiston(state)) {
            Direction f = ComponentCatalog.inputFacing(state);
            if (f != null) {
                boolean extended = state.getValue(PistonBaseBlock.EXTENDED);
                PistonResult pr = this.world.pistonStructure(pos, f, !extended);
                if (pr.resolved()) {
                    for (BlockPos p : pr.toPush()) {
                        this.add(result, pos, p, EdgeType.PISTON, true, null, NodeKind.MOVED, f, null);
                    }
                    for (BlockPos p : pr.toDestroy()) {
                        this.add(result, pos, p, EdgeType.PISTON, true, null, NodeKind.MOVED, f, null);
                    }
                }
            }
        }

        // DOOR_PAIR.
        if (ComponentCatalog.isDoor(state)) {
            Direction other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
            BlockPos half = pos.relative(other);
            if (this.world.isLoaded(half) && ComponentCatalog.isDoor(this.world.state(half))) {
                this.addUndirected(result, pos, half, EdgeType.DOOR_PAIR, null, null);
            }
        }

        // TRIPWIRE: mirrors TripWireBlock.shouldConnectTo - a wire connects to a hook only when the
        // hook faces back along the wire; two hooks never connect directly.
        if (ComponentCatalog.isTripwire(state) || ComponentCatalog.isTripwireHook(state)) {
            for (Direction k : Direction.Plane.HORIZONTAL) {
                BlockPos r = pos.relative(k);
                if (!this.world.isLoaded(r)) {
                    continue;
                }
                BlockState rs = this.world.state(r);
                boolean connect;
                if (ComponentCatalog.isTripwire(rs)) {
                    connect = ComponentCatalog.isTripwire(state)
                            || state.getValue(TripWireHookBlock.FACING) == k;
                } else if (ComponentCatalog.isTripwireHook(rs) && ComponentCatalog.isTripwire(state)) {
                    connect = rs.getValue(TripWireHookBlock.FACING) == k.getOpposite();
                } else {
                    connect = false;
                }
                if (connect) {
                    this.addUndirected(result, pos, r, EdgeType.TRIPWIRE, null, null);
                }
            }
        }

        // RAIL propagation.
        if (ComponentCatalog.isPoweredRail(state)) {
            for (BlockPos p : this.railChain(pos, this.options.railRange)) {
                this.add(result, pos, p, EdgeType.RAIL, true, null, null, null, null);
            }
        }

        // SHAPE (directed, influencer -> dependent). A door / trap door whose OPEN flip changes the
        // face a neighbouring fence / iron bars / wall sees emits the edge, whether or not it is
        // currently in the connected state. A wall below follows the wall above (UP post and arm
        // heights depend on the above block's DOWN cover), so the upper wall emits that edge.
        if (ShapeConnectivity.isOpenMutable(state)) {
            for (Direction k : Direction.Plane.HORIZONTAL) {
                BlockPos r = pos.relative(k);
                if (!this.world.isLoaded(r)) {
                    continue;
                }
                if (isFenceBarsWall(this.world.state(r))
                        && ShapeConnectivity.openTogglesFace(this.world, pos, state, k)) {
                    this.add(result, pos, r, EdgeType.SHAPE, true, null, null, k, null);
                }
            }
        }
        if (ComponentCatalog.isWall(state)) {
            BlockPos below = pos.below();
            if (this.world.isLoaded(below) && ComponentCatalog.isWall(this.world.state(below))) {
                this.add(result, pos, below, EdgeType.SHAPE, true, null, null, Direction.DOWN, null);
            }
        }

        // DISTANCE.
        if (ComponentCatalog.isLeaves(state)) {
            for (Direction k : Direction.values()) {
                BlockPos r = pos.relative(k);
                if (this.world.isLoaded(r) && ComponentCatalog.isLeaves(this.world.state(r))) {
                    this.addUndirected(result, pos, r, EdgeType.DISTANCE, null, null);
                }
            }
        }
        if (ComponentCatalog.isScaffolding(state)) {
            // Horizontal scaffolding mutually determines each other's distance.
            for (Direction k : Direction.Plane.HORIZONTAL) {
                BlockPos r = pos.relative(k);
                if (this.world.isLoaded(r) && ComponentCatalog.isScaffolding(this.world.state(r))) {
                    this.addUndirected(result, pos, r, EdgeType.DISTANCE, null, k);
                }
            }
        }
        // Scaffolding support (directed, support -> scaffolding): vanilla ScaffoldingBlock.getDistance
        // inherits the scaffold below or is anchored (DISTANCE 0) by a block below that is face-sturdy
        // upwards. A door / trap door counts even while OPEN, because flipping OPEN changes that face.
        BlockPos above = pos.above();
        if (this.world.isLoaded(above) && ComponentCatalog.isScaffolding(this.world.state(above))) {
            boolean supports = ComponentCatalog.isScaffolding(state)
                    || this.world.isFaceSturdy(pos, Direction.UP)
                    || ShapeConnectivity.openTogglesFace(this.world, pos, state, Direction.UP);
            if (supports) {
                this.add(result, pos, above, EdgeType.DISTANCE, true, null, null, Direction.UP, null);
            }
        }

        // HALF (quasi-connectivity).
        this.addHalf(pos, state, result);

        // NC.
        if (this.options.ncMode == NcMode.ALL && ComponentCatalog.canChangeState(state)
                && this.options.allows(EdgeType.NC)) {
            for (Direction k : Direction.values()) {
                BlockPos r = pos.relative(k);
                if (!this.world.isLoaded(r)) {
                    continue;
                }
                if (ComponentCatalog.isResponsive(this.world.state(r))) {
                    this.add(result, pos, r, EdgeType.NC, true, null, null, k, null);
                }
            }
        }

        // PP.
        this.addPp(pos, state, result);

        this.outgoingCache.put(pos, result);
        return result;
    }

    /**
     * Quasi-connectivity (§6.5): a piston/dispenser is also activated by a signal that powers the
     * block above it, and both halves of a door are activated by a signal near the other half.
     */
    private void addHalf(BlockPos pos, BlockState state, List<Candidate> result) {
        if (!this.options.allows(EdgeType.HALF)) {
            return;
        }
        boolean emitter = state.isSignalSource();
        // A strongly charged conductor also powers the QC space (vanilla getSignal includes strong charge).
        boolean conductor = !emitter && this.world.isConductor(pos);
        if (!emitter && !conductor) {
            return;
        }
        for (Direction k : Direction.values()) {
            boolean powers = emitter ? emitsToward(pos, state, k) : this.world.weakSignalTo(pos, k) > 0;
            if (!powers) {
                continue;
            }
            BlockPos space = pos.relative(k);
            if (!this.world.isLoaded(space)) {
                continue;
            }
            BlockPos below = space.below();
            if (!below.equals(pos) && this.world.isLoaded(below)) {
                BlockState belowState = this.world.state(below);
                if (ComponentCatalog.isPiston(belowState) || ComponentCatalog.isDispenserLike(belowState)) {
                    this.add(result, pos, below, EdgeType.HALF, true, List.of(space), null, k, null);
                }
            }
            BlockPos door = pos.relative(k);
            BlockState doorState = this.world.state(door);
            if (ComponentCatalog.isDoor(doorState)) {
                Direction sibling = doorState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        ? Direction.UP : Direction.DOWN;
                BlockPos other = door.relative(sibling);
                if (this.world.isLoaded(other)) {
                    this.add(result, pos, other, EdgeType.HALF, true, List.of(door), null, k, null);
                }
            }
        }
    }

    private void addPp(BlockPos pos, BlockState state, List<Candidate> result) {
        if (this.options.ppMode == PpMode.OFF || !this.options.allows(EdgeType.PP)) {
            return;
        }
        if (this.options.ppMode == PpMode.OBSERVER_ONLY) {
            // The observer receives the shape update; the edge originates from the block in front of
            // it, so OUT(front) yields it and IN(observer) can find it (IN only matches outgoing).
            for (Direction k : Direction.values()) {
                BlockPos r = pos.relative(k);
                if (!this.world.isLoaded(r)) {
                    continue;
                }
                BlockState rs = this.world.state(r);
                if (ComponentCatalog.isObserver(rs)) {
                    Direction f = ComponentCatalog.inputFacing(rs);
                    if (f != null && f == k.getOpposite()) {
                        this.add(result, pos, r, EdgeType.PP, true, null, null, k, null);
                    }
                }
            }
            return;
        }
        // ALL
        for (Direction k : Direction.values()) {
            BlockPos r = pos.relative(k);
            if (!this.world.isLoaded(r)) {
                continue;
            }
            if (ComponentCatalog.isResponsive(this.world.state(r))) {
                this.add(result, pos, r, EdgeType.PP, true, null, null, k, null);
            }
        }
        if (ComponentCatalog.isWire(state)) {
            for (Direction k : Direction.Plane.HORIZONTAL) {
                Direction[] diag = diagonal(k);
                for (Direction d : diag) {
                    BlockPos r = pos.relative(k).relative(d);
                    if (this.world.isLoaded(r) && ComponentCatalog.isResponsive(this.world.state(r))) {
                        this.add(result, pos, r, EdgeType.PP, true, null, null, k, null);
                    }
                }
                for (Direction vertical : new Direction[]{Direction.UP, Direction.DOWN}) {
                    BlockPos r = pos.relative(k).relative(vertical);
                    if (this.world.isLoaded(r) && ComponentCatalog.isResponsive(this.world.state(r))) {
                        this.add(result, pos, r, EdgeType.PP, true, null, null, vertical, null);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ incoming

    private List<Candidate> incoming(BlockPos pos) {
        List<Candidate> result = new ArrayList<>();
        // Undirected edges may be generated by this block alone (the far endpoint is not part of the
        // connectivity/distance family), so include them explicitly; the ±2 scan below only sees what
        // the neighbours generate.
        for (Candidate c : this.outgoing(pos)) {
            if (!c.directed() && (c.from().equals(pos) || c.to().equals(pos))) {
                result.add(c);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pred = pos.offset(dx, dy, dz);
                    for (Candidate c : this.outgoing(pred)) {
                        if (c.to().equals(pos) || (!c.directed() && c.from().equals(pos))) {
                            result.add(c);
                        }
                    }
                }
            }
        }
        // Piston edges can span up to 12 blocks, beyond the ±2 scan above. A piston that moves this
        // block must sit on one of the six axes, facing along it.
        for (Direction f : Direction.values()) {
            for (int distance = 1; distance <= PISTON_RANGE; distance++) {
                BlockPos source = pos.relative(f.getOpposite(), distance);
                if (!this.world.isLoaded(source)) {
                    continue;
                }
                BlockState sourceState = this.world.state(source);
                if (!ComponentCatalog.isPiston(sourceState) || ComponentCatalog.inputFacing(sourceState) != f) {
                    continue;
                }
                for (Candidate c : this.outgoing(source)) {
                    if (c.to().equals(pos) && c.type() == EdgeType.PISTON) {
                        result.add(c);
                    }
                }
            }
        }
        BlockState state = this.world.state(pos);
        if (ComponentCatalog.isPoweredRail(state) && this.options.allows(EdgeType.RAIL)) {
            for (BlockPos r : this.railChain(pos, this.options.railRange)) {
                if (!r.equals(pos)) {
                    result.add(new Candidate(r, pos, EdgeType.RAIL, true, List.of(), null, null, null));
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private static Direction[] diagonal(Direction horizontal) {
        return new Direction[]{horizontal.getClockWise(), horizontal.getCounterClockWise()};
    }

    private static boolean isFenceBarsWall(BlockState state) {
        return ComponentCatalog.isFence(state) || ComponentCatalog.isIronBars(state) || ComponentCatalog.isWall(state);
    }

    private List<BlockPos> railChain(BlockPos start, int max) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start);
        Deque<BlockPos> level = new ArrayDeque<>();
        level.add(start);
        for (int distance = 0; distance < max && !level.isEmpty(); distance++) {
            Deque<BlockPos> next = new ArrayDeque<>();
            for (BlockPos p : level) {
                for (BlockPos n : this.world.railConnections(p)) {
                    if (!this.world.isLoaded(n)) {
                        continue;
                    }
                    if (!ComponentCatalog.isPoweredRail(this.world.state(n))) {
                        continue;
                    }
                    if (seen.add(n)) {
                        result.add(n);
                        next.add(n);
                    }
                }
            }
            level = next;
        }
        return result;
    }

    /** Mirrors {@code RedStoneWireBlock.shouldConnectTo} (§6.2). */
    private static boolean wireAccepts(BlockState neighbour, Direction dirFromWireToNeighbour) {
        if (ComponentCatalog.isWire(neighbour)) {
            return true;
        }
        if (neighbour.getBlock() instanceof RepeaterBlock) {
            Direction f = neighbour.getValue(HorizontalDirectionalBlock.FACING);
            return f == dirFromWireToNeighbour || f.getOpposite() == dirFromWireToNeighbour;
        }
        if (neighbour.getBlock() instanceof ObserverBlock) {
            return dirFromWireToNeighbour == neighbour.getValue(DirectionalBlock.FACING);
        }
        return neighbour.isSignalSource() && dirFromWireToNeighbour != null;
    }

    private boolean emitsToward(BlockPos pos, BlockState state, Direction k) {
        if (ComponentCatalog.isDiode(state) || ComponentCatalog.isObserver(state)) {
            Direction f = ComponentCatalog.inputFacing(state);
            return f != null && k == f.getOpposite();
        }
        if (state.getBlock() instanceof RedstoneWallTorchBlock) {
            Direction f = state.getValue(HorizontalDirectionalBlock.FACING);
            return k != f.getOpposite();
        }
        if (ComponentCatalog.isRedstoneTorch(state)) {
            return k != Direction.DOWN;
        }
        if (ComponentCatalog.isWire(state)) {
            return wireEmitsToward(pos, k);
        }
        return true;
    }

    /**
     * Vanilla redstone wire emits down into the block it sits on and horizontally through every
     * connected side (its "pointing"), but never straight up.
     */
    private boolean wireEmitsToward(BlockPos wirePos, Direction k) {
        if (k == Direction.DOWN) {
            return true;
        }
        if (k == Direction.UP) {
            return false;
        }
        EnumProperty<RedstoneSide> property = wireProperty(k);
        if (property == null) {
            return false;
        }
        BlockState wire = this.world.state(wirePos);
        return wire.hasProperty(property) && wire.getValue(property).isConnected();
    }

    private static @Nullable EnumProperty<RedstoneSide> wireProperty(Direction k) {
        return switch (k) {
            case NORTH -> BlockStateProperties.NORTH_REDSTONE;
            case SOUTH -> BlockStateProperties.SOUTH_REDSTONE;
            case EAST -> BlockStateProperties.EAST_REDSTONE;
            case WEST -> BlockStateProperties.WEST_REDSTONE;
            default -> null;
        };
    }

    private static boolean canReceiveCharge(BlockState target, Direction dirFromConductorToTarget,
                                             boolean chargedByWire) {
        if (ComponentCatalog.isDiode(target)) {
            Direction f = ComponentCatalog.inputFacing(target);
            return f != null && dirFromConductorToTarget == f.getOpposite();
        }
        Direction attach = ComponentCatalog.torchAttach(target);
        if (attach != null) {
            // A torch is only affected when the charged conductor is the block it is attached to.
            return dirFromConductorToTarget == attach.getOpposite();
        }
        if (ComponentCatalog.isWire(target)) {
            // A conductor charged only by redstone wire (weak charge) must not feed redstone wire; it can
            // still power non-wire components, and a conductor charged by another component can do both.
            return !chargedByWire;
        }
        // Only blocks that actually react to power may be charged; otherwise a lever, button, ... next
        // to a charged conductor would get a bogus CHARGE edge.
        return ComponentCatalog.isConsumer(target);
    }

    private void add(List<Candidate> list, BlockPos from, BlockPos to, EdgeType type, boolean directed,
                     @Nullable List<BlockPos> via, @Nullable NodeKind targetKind, @Nullable Direction dir,
                     @Nullable String port) {
        if (!this.options.allows(type)) {
            return;
        }
        if (from.equals(to)) {
            return;
        }
        if (!this.world.isLoaded(from) || !this.world.isLoaded(to)) {
            return;
        }
        list.add(new Candidate(from.immutable(), to.immutable(), type, directed,
                via == null ? List.of() : via, dir, port, targetKind));
    }

    private void addUndirected(List<Candidate> list, BlockPos a, BlockPos b, EdgeType type,
                               @Nullable NodeKind targetKind, @Nullable Direction dir) {
        // Canonicalise so both directions produce the same stored edge.
        if (compare(a, b) <= 0) {
            this.add(list, a, b, type, false, null, targetKind, dir, null);
        } else {
            this.add(list, b, a, type, false, null, targetKind, dir, null);
        }
    }

    private static int compare(BlockPos a, BlockPos b) {
        int c = Integer.compare(a.getX(), b.getX());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(a.getY(), b.getY());
        if (c != 0) {
            return c;
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    private record Candidate(BlockPos from, BlockPos to, EdgeType type, boolean directed, List<BlockPos> via,
                             @Nullable Direction dir, @Nullable String port, @Nullable NodeKind targetKind) {
    }
}
