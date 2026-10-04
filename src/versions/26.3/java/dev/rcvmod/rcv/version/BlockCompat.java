package dev.rcvmod.rcv.version;

import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block families that do not exist on every supported version. The crafter and the copper bulbs
 * arrived in 1.21 and the calibrated sculk sensor in 1.20, so older targets report {@code false}
 * and their {@code ComponentCatalog} consumers simply skip those blocks.
 */
public final class BlockCompat {

    private BlockCompat() {
    }

    public static boolean isCrafter(BlockState state) {
        return state.getBlock() instanceof CrafterBlock;
    }

    public static boolean isCalibratedSculkSensor(BlockState state) {
        return state.getBlock() instanceof CalibratedSculkSensorBlock;
    }

    public static boolean isCopperBulb(BlockState state) {
        return state.getBlock() instanceof CopperBulbBlock;
    }
}
