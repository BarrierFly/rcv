package dev.rcvmod.rcv.core;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A single node in the connection graph. */
public final class GraphNode {

    public final int id;
    public final BlockPos pos;
    public final String blockId;
    public final NodeKind kind;
    private final EnumSet<Role> roles;
    public final int depth;
    public final boolean origin;
    /**
     * World-space point to draw at, when it is not the centre of {@link #pos}. Only
     * {@link NodeKind#JUNCTION} nodes set this, because the midpoint of two adjacent blocks lands on
     * half-block coordinates that no {@code BlockPos} can hold.
     */
    public final @Nullable Vec3 anchor;

    public GraphNode(int id, BlockPos pos, String blockId, NodeKind kind, Set<Role> roles, int depth,
                     boolean origin) {
        this(id, pos, blockId, kind, roles, depth, origin, null);
    }

    public GraphNode(int id, BlockPos pos, String blockId, NodeKind kind, Set<Role> roles, int depth,
                     boolean origin, @Nullable Vec3 anchor) {
        this.id = id;
        this.pos = pos.immutable();
        this.blockId = blockId;
        this.kind = kind;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.depth = depth;
        this.origin = origin;
        this.anchor = anchor;
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

    /** World-space centre used for drawing; the fractional anchor wins over the block centre. */
    public Vec3 center() {
        return this.anchor != null ? this.anchor : Vec3.atCenterOf(this.pos);
    }

    public GraphNode withRoles(Set<Role> newRoles) {
        GraphNode node = new GraphNode(this.id, this.pos, this.blockId, this.kind, newRoles, this.depth,
                this.origin, this.anchor);
        return node;
    }

    public GraphNode withKind(NodeKind newKind) {
        return new GraphNode(this.id, this.pos, this.blockId, newKind, this.roles, this.depth, this.origin,
                this.anchor);
    }

    @Override
    public String toString() {
        return "GraphNode#" + this.id + "(" + this.blockId + " @" + this.pos.toShortString() + " d" + this.depth + ")";
    }
}
