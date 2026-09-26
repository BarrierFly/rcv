package dev.rcvmod.rcv.core;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** A single node in the connection graph. */
public final class GraphNode {

    public final int id;
    public final BlockPos pos;
    public final String blockId;
    public final NodeKind kind;
    private final EnumSet<Role> roles;
    public final int depth;
    public final boolean origin;

    public GraphNode(int id, BlockPos pos, String blockId, NodeKind kind, Set<Role> roles, int depth,
                     boolean origin) {
        this.id = id;
        this.pos = pos.immutable();
        this.blockId = blockId;
        this.kind = kind;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.depth = depth;
        this.origin = origin;
    }

    public Set<Role> roles() {
        return this.roles;
    }

    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    public boolean isComponent() {
        return this.kind == NodeKind.COMPONENT;
    }

    public boolean isConductor() {
        return this.kind == NodeKind.CONDUCTOR || this.roles.contains(Role.CONDUCTOR);
    }

    public GraphNode withRoles(Set<Role> newRoles) {
        GraphNode node = new GraphNode(this.id, this.pos, this.blockId, this.kind, newRoles, this.depth, this.origin);
        return node;
    }

    public GraphNode withKind(NodeKind newKind) {
        return new GraphNode(this.id, this.pos, this.blockId, newKind, this.roles, this.depth, this.origin);
    }

    @Override
    public String toString() {
        return "GraphNode#" + this.id + "(" + this.blockId + " @" + this.pos.toShortString() + " d" + this.depth + ")";
    }
}
