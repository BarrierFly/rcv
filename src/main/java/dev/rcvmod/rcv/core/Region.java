package dev.rcvmod.rcv.core;

import net.minecraft.core.BlockPos;

/** Inclusive axis-aligned region selection (§7.2). */
public record Region(BlockPos min, BlockPos max) {

    public static Region of(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        return new Region(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.min.getX() && pos.getX() <= this.max.getX()
                && pos.getY() >= this.min.getY() && pos.getY() <= this.max.getY()
                && pos.getZ() >= this.min.getZ() && pos.getZ() <= this.max.getZ();
    }

    public boolean containsAll(BlockPos a, BlockPos b) {
        return this.contains(a) && this.contains(b);
    }

    public long volume() {
        return (long) (this.max.getX() - this.min.getX() + 1)
                * (this.max.getY() - this.min.getY() + 1)
                * (this.max.getZ() - this.min.getZ() + 1);
    }

    public Region union(Region other) {
        if (other == null) {
            return this;
        }
        return of(
                new BlockPos(Math.min(this.min.getX(), other.min.getX()), Math.min(this.min.getY(), other.min.getY()),
                        Math.min(this.min.getZ(), other.min.getZ())),
                new BlockPos(Math.max(this.max.getX(), other.max.getX()), Math.max(this.max.getY(), other.max.getY()),
                        Math.max(this.max.getZ(), other.max.getZ())));
    }
}
