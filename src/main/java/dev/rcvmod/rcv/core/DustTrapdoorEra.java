package dev.rcvmod.rcv.core;

import dev.rcvmod.rcv.RCV;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Decides whether the current environment behaves like pre-1.20 redstone, i.e. whether a trap
 * door's {@code OPEN} actually gates the adjacent wire's connection. Only then can
 * {@link EdgeType#DUST_TRAPDOOR} edges exist, so getting this wrong in the other direction would
 * draw edges that cannot happen.
 *
 * <p>Vanilla removed the gate in 1.20 by replacing {@code canSurviveOn(T)} with an unconditional
 * {@code state instanceof TrapDoorBlock ||}. Mods reinstate it - Carpet TIS Addition's
 * {@code dustTrapdoorReintroduced} rule and AntiShadowPatch's {@code BringBackTrapdoorUpdateSkipping}
 * both patch the very same expression - so a version check alone is not enough, and a whitelist of
 * mod ids cannot cover unknown reimplementations.
 *
 * <p>Three layers, strongest first:
 * <ol>
 *   <li><b>Probe (preferred).</b> Build a tiny synthetic world and ask the JVM's <em>actual</em>
 *       {@code RedStoneWireBlock} whether a wire still powers a side through an open trap door. A
 *       reinstated mixin is already applied to the loaded class, so this sees the real behaviour for
 *       any mod, known or not. It needs no version knowledge: the answer differs between eras
 *       because the gate expression differs.</li>
 *   <li><b>Mod inspection (cross-check and fallback).</b> Reflect the known rules. Used to explain
 *       the decision in the log, and as the fallback when the probe cannot run.</li>
 *   <li><b>User override.</b> {@code auto|on|off} wins outright.</li>
 * </ol>
 *
 * <p>"No conclusion" resolves to {@code false}. Showing an edge that cannot occur while debugging is
 * worse than not showing one, so the default is conservative.
 */
public final class DustTrapdoorEra {

    private static final Object UNRESOLVED = new Object();
    private static volatile Object cached;

    private DustTrapdoorEra() {
    }

    /**
     * @return {@code true} when dust-trapdoor edges should be computed
     */
    public static boolean legacy(DustTrapdoorMode mode) {
        if (mode == DustTrapdoorMode.ON) {
            return true;
        }
        if (mode == DustTrapdoorMode.OFF) {
            return false;
        }
        Object result = cached;
        if (result == null) {
            synchronized (DustTrapdoorEra.class) {
                result = cached;
                if (result == null) {
                    result = detect();
                    cached = result;
                }
            }
        }
        return result == Boolean.TRUE;
    }

    /** Drops the memoised probe result; called on {@code /rcv reload} and on config hot-reload. */
    public static void reset() {
        cached = null;
    }

    private static Object detect() {
        try {
            Boolean probed = probe();
            if (probed != null) {
                Boolean byMod = inspectMods();
                if (byMod != null && byMod != probed) {
                    RCV.LOGGER.warn(
                            "Dust-trapdoor probe says {}, but the installed reintroduction mods say {}. "
                                    + "Trusting the probe; set \"dustTrapdoor\" in the RCV config to override.",
                            probed ? "legacy" : "modern", byMod ? "legacy" : "modern");
                }
                return probed;
            }
        } catch (Throwable t) {
            RCV.LOGGER.warn("Dust-trapdoor behaviour probe failed; falling back to mod inspection", t);
        }
        Boolean byMod = inspectMods();
        return byMod != null ? byMod : Boolean.FALSE;
    }

    // ------------------------------------------------------------------ probe

    /**
     * Asks the loaded {@code RedStoneWireBlock} whether a wire still powers the side an <em>open</em>
     * top-half trap door sits on.
     *
     * <pre>
     *   y=1:  .  D  .          D = wire, the premise that makes the gate branch reachable
     *   y=0:  .  W  T          W = wire with POWER 1, all four sides NONE (a "dot")
     *   y=-1: .  .  .
     * </pre>
     *
     * {@code W}'s four sides must stay at their stored {@code NONE}: that is what makes
     * {@code isDot(stored)} hold, which skips the back-fill segment and is the only configuration in
     * which the two eras disagree. {@code POWER} must be non-zero because {@code getSignal} returns 0
     * outright otherwise. Anything not listed resolves to air - {@code getMissingConnections} reads the
     * wire's neighbours and their above/below, and a {@code null} there would blow up mid-probe.
     *
     * @return {@code true} for legacy (no signal through the open door), {@code false} for modern, or
     *         {@code null} when the probe could not be answered. Package-private so the engine tests
     *         can smoke-test it on every supported Minecraft version.
     */
    static @Nullable Boolean probe() {
        BlockPos wire = new BlockPos(0, 0, 0);
        BlockPos trapDoor = new BlockPos(1, 0, 0);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(wire, Blocks.REDSTONE_WIRE.defaultBlockState()
                .setValue(BlockStateProperties.POWER, 1));
        // Open, and explicitly a top half: a bottom half can never be sturdy upwards.
        blocks.put(trapDoor, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(BlockStateProperties.OPEN, true));
        blocks.put(trapDoor.above(), Blocks.REDSTONE_WIRE.defaultBlockState());
        blocks.put(trapDoor.below(), Blocks.STONE.defaultBlockState());
        blocks.put(wire.above(), Blocks.AIR.defaultBlockState());

        // side runs from the receiver (the trap door) towards the emitter (the wire).
        int signal = blocks.get(wire).getSignal(new ProbeBlockGetter(blocks), wire, Direction.WEST);
        return signal == 0;
    }

    /**
     * Minimal read-only {@link net.minecraft.world.level.BlockGetter}. The wire algorithm only ever
     * asks it for block states - a trap door's {@code isFaceSturdy} is precomputed into its state
     * cache at construction time and never touches the level - so the remaining methods are stubs.
     *
     * <p>{@code getMinBuildHeight} and {@code getMinY} are both declared because vanilla renamed the
     * former to the latter in 1.21.9. Whichever one this Minecraft version does not know is simply an
     * extra public method.
     */
    private static final class ProbeBlockGetter implements net.minecraft.world.level.BlockGetter {

        private final Map<BlockPos, BlockState> blocks;

        ProbeBlockGetter(Map<BlockPos, BlockState> blocks) {
            this.blocks = blocks;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = this.blocks.get(pos);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @SuppressWarnings("unused")
        public int getMinBuildHeight() {
            return -64;
        }

        @SuppressWarnings("unused")
        public int getMinY() {
            return -64;
        }
    }

    // ------------------------------------------------------------------ mod inspection

    /**
     * Reads the switch of every mod we know about that reinstates the gate.
     *
     * @return {@code null} when nothing is installed or nothing could be read
     */
    private static @Nullable Boolean inspectMods() {
        try {
            if (FabricLoader.getInstance().isModLoaded("carpet-tis-addition")) {
                Boolean tis = readStaticBoolean("carpettisaddition.CarpetTISAdditionSettings",
                        "dustTrapdoorReintroduced");
                if (tis != null) {
                    return tis;
                }
            }
            if (FabricLoader.getInstance().isModLoaded("antishadowpatch")) {
                // AntiShadowPatch decides at class-load time whether to apply its mixin, from an
                // fconfiglib JSON value that is neither static nor readable by reflection. Assume the
                // gate is back: the mod ships enabled by default and a false positive here only means
                // edges that a user who disabled the option will not see.
                RCV.LOGGER.info("AntiShadowPatch detected; assuming the dust-trapdoor gate is reinstated");
                return Boolean.TRUE;
            }
        } catch (Throwable t) {
            RCV.LOGGER.debug("Could not inspect reintroduction mods", t);
        }
        return null;
    }

    private static @Nullable Boolean readStaticBoolean(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className);
            Field field = type.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Boolean b ? b : null;
        } catch (Throwable t) {
            RCV.LOGGER.debug("Could not read {}.{}", className, fieldName, t);
            return null;
        }
    }
}