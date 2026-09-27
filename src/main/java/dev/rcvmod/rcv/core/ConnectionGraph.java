package dev.rcvmod.rcv.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Mutable result of a traversal (nodes + merged edges). */
public final class ConnectionGraph {

    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Map<Long, Integer> posIndex = new HashMap<>();
    /** Stable id -> node lookup; the {@link #nodes} list is reordered for rendering, so ids are not indices. */
    private final Map<Integer, GraphNode> byId = new HashMap<>();

    private final QueryMode mode;
    private Region region;
    private int originId = -1;
    private boolean truncated;

    public ConnectionGraph(QueryMode mode) {
        this.mode = mode;
    }

    public QueryMode mode() {
        return this.mode;
    }

    public Region region() {
        return this.region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public int originId() {
        return this.originId;
    }

    public void setOriginId(int id) {
        this.originId = id;
    }

    public boolean truncated() {
        return this.truncated;
    }

    public void markTruncated() {
        this.truncated = true;
    }

    public List<GraphNode> nodes() {
        return this.nodes;
    }

    public List<GraphEdge> edges() {
        return this.edges;
    }

    public int nodeCount() {
        return this.nodes.size();
    }

    public int edgeCount() {
        return this.edges.size();
    }

    public GraphNode node(int id) {
        return this.byId.get(id);
    }

    public int idOf(BlockPos pos) {
        Integer id = this.posIndex.get(pos.asLong());
        return id == null ? -1 : id;
    }

    /**
     * Adds (or upgrades) a node at {@code pos}. Ids are stable and equal to the node's index in the list.
     */
    public int addNode(BlockPos pos, String blockId, NodeKind kind, Set<Role> roles, int depth) {
        long key = pos.asLong();
        Integer existingId = this.posIndex.get(key);
        if (existingId != null) {
            GraphNode existing = this.nodes.get(existingId);
            NodeKind mergedKind = mergeKind(existing.kind, kind);
            java.util.EnumSet<Role> mergedRoles = java.util.EnumSet.noneOf(Role.class);
            mergedRoles.addAll(existing.roles());
            mergedRoles.addAll(roles);
            int mergedDepth = Math.min(existing.depth, depth);
            if (mergedKind != existing.kind || mergedRoles.size() != existing.roles().size()
                    || mergedDepth != existing.depth) {
                GraphNode updated = new GraphNode(existing.id, existing.pos, blockId, mergedKind, mergedRoles,
                        mergedDepth, existing.origin);
                this.nodes.set(existingId, updated);
                this.byId.put(existingId, updated);
            }
            return existingId;
        }
        int id = this.nodes.size();
        GraphNode node = new GraphNode(id, pos, blockId, kind, roles, depth, false);
        this.nodes.add(node);
        this.byId.put(id, node);
        this.posIndex.put(key, id);
        return id;
    }

    private static NodeKind mergeKind(NodeKind existing, NodeKind incoming) {
        if (existing == NodeKind.VIA) {
            return incoming;
        }
        if (incoming == NodeKind.VIA) {
            return existing;
        }
        if (existing == NodeKind.CONDUCTOR && incoming == NodeKind.COMPONENT) {
            return NodeKind.COMPONENT;
        }
        if (existing == NodeKind.COMPONENT && incoming == NodeKind.CONDUCTOR) {
            return NodeKind.COMPONENT;
        }
        return existing;
    }

    public void setOrigin(int id) {
        GraphNode node = this.byId.get(id);
        if (node == null) {
            return;
        }
        GraphNode updated = new GraphNode(node.id, node.pos, node.blockId, node.kind, node.roles(), node.depth, true);
        this.byId.put(id, updated);
        this.nodes.set(this.nodes.indexOf(node), updated);
        this.originId = id;
    }

    public boolean addEdge(GraphEdge edge) {
        if (edge.from == edge.to) {
            return false;
        }
        for (int i = 0; i < this.edges.size(); i++) {
            GraphEdge existing = this.edges.get(i);
            if (existing.from == edge.from && existing.to == edge.to) {
                if (existing.type == edge.type) {
                    this.edges.set(i, existing.mergeVia(edge));
                    return true;
                }
                if (edge.type.priority() < existing.type.priority()) {
                    this.edges.set(i, edge);
                    return true;
                }
                return false;
            }
        }
        this.edges.add(edge);
        return true;
    }

    /** Stable ordering so tests, networking and rendering agree (§4.9). */
    public void sortForRender() {
        this.nodes.sort(Comparator
                .comparingInt((GraphNode n) -> n.depth)
                .thenComparingInt(n -> n.kind.ordinal())
                .thenComparingInt(n -> n.pos.getX())
                .thenComparingInt(n -> n.pos.getY())
                .thenComparingInt(n -> n.pos.getZ()));

        Map<Integer, GraphNode> byId = new HashMap<>();
        for (GraphNode n : this.nodes) {
            byId.put(n.id, n);
        }
        this.edges.sort(Comparator
                .comparingInt((GraphEdge e) -> depthOf(byId, e.from))
                .thenComparingInt(e -> depthOf(byId, e.to))
                .thenComparingInt(e -> e.type.priority())
                .thenComparingInt(e -> e.from)
                .thenComparingInt(e -> e.to));
    }

    private static int depthOf(Map<Integer, GraphNode> byId, int id) {
        GraphNode node = byId.get(id);
        return node == null ? Integer.MAX_VALUE : node.depth;
    }
}
