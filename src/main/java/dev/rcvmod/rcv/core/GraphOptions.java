package dev.rcvmod.rcv.core;

/** Everything that parameterises a single graph computation. */
public final class GraphOptions {

    public static final int DEFAULT_DEPTH = 16;
    public static final int MAX_DEPTH = 64;
    public static final int MAX_NODES = 4096;
    public static final int MAX_EDGES = 16384;
    public static final int DEFAULT_RAIL_RANGE = 8;

    public final int depth;
    public final TypeMask typeMask;
    public final NcMode ncMode;
    public final PpMode ppMode;
    public final Region region;
    public final int maxNodes;
    public final int maxEdges;
    public final int railRange;
    /**
     * Whether the environment behaves like pre-1.20 redstone, where a trap door's {@code OPEN} gated
     * the adjacent wire's connection. Only then can {@link EdgeType#DUST_TRAPDOOR} edges exist; see
     * {@link DustTrapdoorEra} for how this is decided.
     */
    public final boolean dustTrapdoorLegacy;

    public GraphOptions(int depth, TypeMask typeMask, NcMode ncMode, PpMode ppMode, Region region,
                        int maxNodes, int maxEdges) {
        this(depth, typeMask, ncMode, ppMode, region, maxNodes, maxEdges, DEFAULT_RAIL_RANGE, false);
    }

    public GraphOptions(int depth, TypeMask typeMask, NcMode ncMode, PpMode ppMode, Region region,
                        int maxNodes, int maxEdges, int railRange) {
        this(depth, typeMask, ncMode, ppMode, region, maxNodes, maxEdges, railRange, false);
    }

    public GraphOptions(int depth, TypeMask typeMask, NcMode ncMode, PpMode ppMode, Region region,
                        int maxNodes, int maxEdges, int railRange, boolean dustTrapdoorLegacy) {
        this.depth = Math.max(1, Math.min(depth, MAX_DEPTH));
        this.typeMask = typeMask;
        this.ncMode = ncMode;
        this.ppMode = ppMode;
        this.region = region;
        this.maxNodes = maxNodes;
        this.maxEdges = maxEdges;
        this.railRange = Math.max(1, railRange);
        this.dustTrapdoorLegacy = dustTrapdoorLegacy;
    }

    public static GraphOptions defaults() {
        return new GraphOptions(DEFAULT_DEPTH, TypeMask.all(), NcMode.OFF, PpMode.OBSERVER_ONLY, null,
                MAX_NODES, MAX_EDGES, DEFAULT_RAIL_RANGE, false);
    }

    public GraphOptions withDepth(int newDepth) {
        return new GraphOptions(newDepth, this.typeMask, this.ncMode, this.ppMode, this.region, this.maxNodes,
                this.maxEdges, this.railRange, this.dustTrapdoorLegacy);
    }

    public GraphOptions withRegion(Region newRegion) {
        return new GraphOptions(this.depth, this.typeMask, this.ncMode, this.ppMode, newRegion, this.maxNodes,
                this.maxEdges, this.railRange, this.dustTrapdoorLegacy);
    }

    public GraphOptions withLimits(int nodes, int edges) {
        return new GraphOptions(this.depth, this.typeMask, this.ncMode, this.ppMode, this.region, nodes, edges,
                this.railRange, this.dustTrapdoorLegacy);
    }

    public GraphOptions withDustTrapdoorLegacy(boolean legacy) {
        return new GraphOptions(this.depth, this.typeMask, this.ncMode, this.ppMode, this.region, this.maxNodes,
                this.maxEdges, this.railRange, legacy);
    }

    public boolean allows(EdgeType type) {
        return this.typeMask.allows(type);
    }
}
