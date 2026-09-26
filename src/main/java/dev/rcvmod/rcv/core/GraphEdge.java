package dev.rcvmod.rcv.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** A directed/undirected connection between two nodes. */
public final class GraphEdge {

    public final int from;
    public final int to;
    public final EdgeType type;
    public final boolean directed;
    private final List<BlockPos> via;
    public final @Nullable Direction dir;
    public final @Nullable String port;

    public GraphEdge(int from, int to, EdgeType type, boolean directed, List<BlockPos> via,
                     @Nullable Direction dir, @Nullable String port) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.directed = directed;
        this.via = via == null || via.isEmpty() ? List.of() : new ArrayList<>(via);
        this.dir = dir;
        this.port = port;
    }

    public List<BlockPos> via() {
        return Collections.unmodifiableList(this.via);
    }

    public boolean hasVia() {
        return !this.via.isEmpty();
    }

    /** Merge another edge that shares the same endpoints, returning a new edge with the union of vias. */
    public GraphEdge mergeVia(GraphEdge other) {
        List<BlockPos> merged = new ArrayList<>(this.via);
        for (BlockPos pos : other.via) {
            if (!merged.contains(pos)) {
                merged.add(pos);
            }
        }
        return new GraphEdge(this.from, this.to, this.type, this.directed, merged, this.dir, this.port);
    }

    public boolean sameKey(GraphEdge other) {
        return this.from == other.from && this.to == other.to && this.type == other.type
                && this.dir == other.dir && java.util.Objects.equals(this.port, other.port);
    }

    @Override
    public String toString() {
        return "GraphEdge#" + this.from + "-" + this.to + " " + this.type
                + (this.directed ? " ->" : " <->") + (this.via.isEmpty() ? "" : " via " + this.via);
    }
}
