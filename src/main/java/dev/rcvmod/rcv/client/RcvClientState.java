package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ComponentCatalog;
import dev.rcvmod.rcv.core.ConnectionEngine;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.GraphOptions;
import dev.rcvmod.rcv.core.QueryMode;
import dev.rcvmod.rcv.core.Region;
import dev.rcvmod.rcv.core.TypeMask;
import dev.rcvmod.rcv.mc.LevelWorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Per-client visualization state (§2, session scoped, one set per player). */
public final class RcvClientState {

    private static ConnectionGraph graph;
    private static QueryMode mode = QueryMode.IN;
    private static BlockPos origin;
    private static int depth = 16;
    private static TypeMask typeMask = TypeMask.all();
    private static Region region;
    private static int ticksSinceQuery = -1;

    public static boolean wandEnabled = true;
    public static boolean regionSelectionMode = false;
    public static BlockPos pos1;
    public static BlockPos pos2;

    private RcvClientState() {
    }

    public static @Nullable ConnectionGraph graph() {
        return graph;
    }

    public static QueryMode mode() {
        return mode;
    }

    public static @Nullable BlockPos origin() {
        return origin;
    }

    public static int depth() {
        return depth;
    }

    public static TypeMask typeMask() {
        return typeMask;
    }

    public static @Nullable Region region() {
        return region;
    }

    public static int ticksSinceQuery() {
        return ticksSinceQuery;
    }

    public static void tick() {
        if (graph == null) {
            return;
        }
        if (ticksSinceQuery >= 0) {
            ticksSinceQuery++;
        }
        int autoClear = RcvConfig.get().autoClearSeconds;
        if (autoClear > 0 && ticksSinceQuery >= autoClear * 20) {
            clear();
        }
    }

    public static void setMode(QueryMode newMode) {
        mode = newMode;
        recompute();
    }

    public static void setDepth(int newDepth) {
        depth = newDepth;
        recompute();
    }

    public static void setRegion(@Nullable Region newRegion) {
        region = newRegion;
        recompute();
    }

    public static void setTypeMask(TypeMask mask) {
        typeMask = mask.copy();
        recompute();
    }

    public static void clear() {
        graph = null;
        origin = null;
        ticksSinceQuery = -1;
    }

    public static void refresh() {
        recompute();
    }

    /** Computes locally from the client level (C only / S+C fallback). */
    public static void recompute() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || origin == null) {
            return;
        }
        LevelWorldView world = new LevelWorldView(mc.level);
        BlockState state = world.state(origin);
        if (!ComponentCatalog.isComponent(state) && !world.isConductor(origin)) {
            graph = null;
            return;
        }
        RcvConfig config = RcvConfig.get();
        GraphOptions options = new GraphOptions(depth, typeMask, config.ncMode(), config.ppMode(), region,
                GraphOptions.MAX_NODES, GraphOptions.MAX_EDGES);
        graph = new ConnectionEngine(world, options, mode).compute(origin);
        ticksSinceQuery = 0;
    }

    public static void setOriginAndCompute(BlockPos pos) {
        origin = pos.immutable();
        recompute();
    }

    public static void runQuery(QueryMode newMode, BlockPos newOrigin, int newDepth, TypeMask newMask,
                                @Nullable Region newRegion) {
        mode = newMode;
        origin = newOrigin.immutable();
        depth = newDepth;
        typeMask = newMask.copy();
        region = newRegion;
        recompute();
    }
}
