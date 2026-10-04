package dev.rcvmod.rcv.version;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Block families that do not exist on every supported version. On 1.19.4 neither the crafter and the
 * copper bulbs (both 1.21) nor the calibrated sculk sensor (1.20) are registered, so all three
 * predicates are constant and their {@code ComponentCatalog} consumers skip those blocks.
 */
public final class BlockCompat {

    private BlockCompat() {
    }

    public static boolean isCrafter(BlockState state) {
        return false;
    }

    public static boolean isCalibratedSculkSensor(BlockState state) {
        return false;
    }

    public static boolean isCopperBulb(BlockState state) {
        return false;
    }
}
