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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.Nullable;

/** Computes the reachable subgraph around an origin (§4). */
public final class ConnectionEngine {

    public static final int RAIL_RANGE = 8;

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
                BlockPos other = this.mode == QueryMode.OUT ? c.to() : c.from();
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
                if (wireAccepts(state, dirRtoA)) {
                    boolean undirected = ComponentCatalog.isWire(state);
                    this.add(result, pos, r, EdgeType.CIRCUIT, !undirected, null, null, dirRtoA, null);
                }
                continue;
            }

            if (ComponentCatalog.isDiode(rs)) {
                Direction f = ComponentCatalog.inputFacing(rs);
                if (f != null) {
                    if (k == f.getOpposite() && (emits || ComponentCatalog.isWire(state))) {
                        this.add(result, pos, r, EdgeType.CIRCUIT, true, null, null, f, null);
                    } else if (ComponentCatalog.isComparator(rs) && (k == f.getClockWise() || k == f.getCounterClockWise())
                            && (emits || ComponentCatalog.isWire(state))) {
                        String port = k == f.getClockWise() ? "SIDE_L" : "SIDE_R";
                        this.add(result, pos, r, EdgeType.COMPARATOR_SIDE, true, null, null, f, port);
                    }
                }
                continue;
            }

            if (ComponentCatalog.isPoweredRail(rs)) {
                if (emits && emitsToward(state, k)) {
                    this.add(result, pos, r, EdgeType.RAIL, true, null, null, k, null);
                }
                continue;
            }

            if (emits && emitsToward(state, k) && ComponentCatalog.isConsumer(rs)) {
                this.add(result, pos, r, EdgeType.DIRECT_ACTIVATION, true, null, null, k, null);
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
                boolean frame = !this.world.itemFrames(mid, k).isEmpty();
                if (state.hasAnalogOutputSignal() || frame) {
                    this.add(result, pos, t, EdgeType.ANALOG, true, List.of(mid), null, k, null);
                }
            }
        }

        // CHARGE: strongly charge a conductor, which then powers neighbouring components.
        for (Direction k : Direction.values()) {
            BlockPos c = pos.relative(k);
            if (!this.world.isLoaded(c) || !this.world.isConductor(c)) {
                continue;
            }
            if (!stronglyPowers(pos, k, state)) {
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
                if (!canReceiveCharge(ts, m)) {
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
                        this.add(result, pos, p, EdgeType.PISTON, true, null, null, f, null);
                    }
                    for (BlockPos p : pr.toDestroy()) {
                        this.add(result, pos, p, EdgeType.PISTON, true, null, null, f, null);
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

        // TRIPWIRE.
        if (ComponentCatalog.isTripwire(state) || ComponentCatalog.isTripwireHook(state)) {
            for (Direction k : Direction.Plane.HORIZONTAL) {
                BlockPos r = pos.relative(k);
                if (!this.world.isLoaded(r)) {
                    continue;
                }
                BlockState rs = this.world.state(r);
                if (ComponentCatalog.isTripwire(rs) || ComponentCatalog.isTripwireHook(rs)) {
                    this.addUndirected(result, pos, r, EdgeType.TRIPWIRE, null, null);
                }
            }
        }

        // RAIL propagation.
        if (ComponentCatalog.isPoweredRail(state)) {
            for (BlockPos p : this.railChain(pos, RAIL_RANGE)) {
                this.add(result, pos, p, EdgeType.RAIL, true, null, null, null, null);
            }
        }

        // SHAPE.
        for (Direction k : Direction.values()) {
            BlockPos r = pos.relative(k);
            if (!this.world.isLoaded(r)) {
                continue;
            }
            BlockState rs = this.world.state(r);
            if (ComponentCatalog.isConnectivity(rs) && neighbourAffectsConnectivity(pos, state, r, rs, k)) {
                this.add(result, pos, r, EdgeType.SHAPE, true, null, null, k, null);
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
            for (Direction k : Direction.Plane.HORIZONTAL) {
                BlockPos r = pos.relative(k);
                if (this.world.isLoaded(r) && ComponentCatalog.isScaffolding(this.world.state(r))) {
                    this.addUndirected(result, pos, r, EdgeType.DISTANCE, null, null);
                }
            }
            BlockPos up = pos.above();
            if (this.world.isLoaded(up) && ComponentCatalog.isScaffolding(this.world.state(up))) {
                this.add(result, pos, up, EdgeType.DISTANCE, true, null, null, Direction.UP, null);
            }
        }

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

    private void addPp(BlockPos pos, BlockState state, List<Candidate> result) {
        if (this.options.ppMode == PpMode.OFF || !this.options.allows(EdgeType.PP)) {
            return;
        }
        if (this.options.ppMode == PpMode.OBSERVER_ONLY) {
            if (ComponentCatalog.isObserver(state)) {
                Direction f = ComponentCatalog.inputFacing(state);
                if (f != null) {
                    BlockPos front = pos.relative(f);
                    if (this.world.isLoaded(front)) {
                        this.add(result, front, pos, EdgeType.PP, true, null, null, f, null);
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
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pred = pos.offset(dx, dy, dz);
                    for (Candidate c : this.outgoing(pred)) {
                        if (c.to().equals(pos)) {
                            result.add(c);
                        }
                    }
                }
            }
        }
        BlockState state = this.world.state(pos);
        if (ComponentCatalog.isPoweredRail(state)) {
            for (BlockPos r : this.railChain(pos, RAIL_RANGE)) {
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

    private static boolean emitsToward(BlockState state, Direction k) {
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
            return k != Direction.DOWN;
        }
        return true;
    }

    private boolean stronglyPowers(BlockPos pos, Direction k, BlockState state) {
        if (stronglyEmitsToward(state, k)) {
            return true;
        }
        return this.world.directSignalTo(pos, k) > 0;
    }

    private static boolean stronglyEmitsToward(BlockState state, Direction k) {
        if (state.is(Blocks.REDSTONE_BLOCK)) {
            return true;
        }
        if (ComponentCatalog.isDiode(state) || ComponentCatalog.isObserver(state)) {
            Direction f = ComponentCatalog.inputFacing(state);
            return f != null && k == f.getOpposite();
        }
        if (ComponentCatalog.isRedstoneTorch(state)) {
            return k == Direction.UP;
        }
        return false;
    }

    private static boolean canReceiveCharge(BlockState target, Direction dirFromConductorToTarget) {
        if (ComponentCatalog.isDiode(target)) {
            Direction f = ComponentCatalog.inputFacing(target);
            return f != null && dirFromConductorToTarget == f.getOpposite();
        }
        if (ComponentCatalog.isRedstoneTorch(target)) {
            return true;
        }
        return ComponentCatalog.isResponsive(target);
    }

    private static boolean neighbourAffectsConnectivity(BlockPos a, BlockState as, BlockPos b, BlockState bs,
                                                        Direction dirAtoB) {
        Direction dirBtoA = dirAtoB.getOpposite();
        if (ComponentCatalog.isFence(bs) || ComponentCatalog.isIronBars(bs)) {
            if (ComponentCatalog.isFence(as) || ComponentCatalog.isIronBars(as)
                    || ComponentCatalog.isFenceGate(as)) {
                return true;
            }
            return false;
        }
        if (ComponentCatalog.isWall(bs)) {
            if (ComponentCatalog.isWall(as) && b.equals(a.below())) {
                return true;
            }
            if (ComponentCatalog.isWall(as) || ComponentCatalog.isFence(as)
                    || ComponentCatalog.isIronBars(as) || ComponentCatalog.isFenceGate(as)) {
                return true;
            }
            return false;
        }
        if (ComponentCatalog.isFenceGate(bs)) {
            return ComponentCatalog.isFence(as) || ComponentCatalog.isWall(as) || ComponentCatalog.isIronBars(as);
        }
        if (ComponentCatalog.isBell(bs)) {
            return ComponentCatalog.isFence(as) || ComponentCatalog.isWall(as) || ComponentCatalog.isIronBars(as)
                    || ComponentCatalog.isDoor(as) || ComponentCatalog.isTrapDoor(as);
        }
        if (ComponentCatalog.isFenceGate(as) || ComponentCatalog.isDoor(as) || ComponentCatalog.isTrapDoor(as)
                || ComponentCatalog.isPiston(as)) {
            return true;
        }
        return false;
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
