package dev.rcvmod.rcv.core;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Read-only result of {@code PistonStructureResolver} (§6.7). */
public record PistonResult(boolean resolved, List<BlockPos> toPush, List<BlockPos> toDestroy) {

    public static PistonResult empty() {
        return new PistonResult(false, List.of(), List.of());
    }
}
