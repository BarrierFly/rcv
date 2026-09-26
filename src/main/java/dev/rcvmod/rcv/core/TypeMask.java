package dev.rcvmod.rcv.core;

import java.util.EnumSet;

/** Which {@link EdgeType}s a query should compute. */
public final class TypeMask {

    private final EnumSet<EdgeType> enabled;

    private TypeMask(EnumSet<EdgeType> enabled) {
        this.enabled = enabled;
    }

    public static TypeMask all() {
        return new TypeMask(EnumSet.allOf(EdgeType.class));
    }

    public static TypeMask none() {
        return new TypeMask(EnumSet.noneOf(EdgeType.class));
    }

    public boolean allows(EdgeType type) {
        return this.enabled.contains(type);
    }

    public void set(EdgeType type, boolean value) {
        if (value) {
            this.enabled.add(type);
        } else {
            this.enabled.remove(type);
        }
    }

    public void toggle(EdgeType type) {
        this.set(type, !this.allows(type));
    }

    public TypeMask copy() {
        return new TypeMask(EnumSet.copyOf(this.enabled));
    }

    public EnumSet<EdgeType> asSet() {
        return EnumSet.copyOf(this.enabled);
    }

    public int truncatedMask(int bits) {
        int mask = 0;
        for (EdgeType type : this.enabled) {
            if (type.ordinal() < bits) {
                mask |= 1 << type.ordinal();
            }
        }
        return mask;
    }
}
