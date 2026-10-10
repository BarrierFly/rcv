package dev.rcvmod.rcv.core;

/** Visual/structural kind of a graph node. */
public enum NodeKind {
    COMPONENT,
    CONDUCTOR,
    MOVED,
    VIA,
    /**
     * Fractional midpoint of two adjacent blocks, used as the origin of a
     * {@link EdgeType#DUST_TRAPDOOR} back segment so it does not collide with the wire's own edge to
     * the same block. Carries no block state and is never traversed: the BFS works on {@code BlockPos}
     * and junction nodes are deliberately absent from the position index.
     */
    JUNCTION
}