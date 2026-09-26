package dev.rcvmod.rcv.core;

import java.util.Locale;

/**
 * The kinds of connections RCV can compute.
 *
 * <p>The enum order encodes the merge priority (see {@link #priority()}): when two edges share the
 * same {@code (from, to)} pair only the highest priority one is kept. {@link #CIRCUIT} and
 * {@link #COMPARATOR_SIDE} intentionally share priority 2.
 */
public enum EdgeType {
    DIRECT_ACTIVATION(1, "direct_activation"),
    CIRCUIT(2, "circuit"),
    COMPARATOR_SIDE(2, "comparator_side"),
    ANALOG(3, "analog"),
    CHARGE(4, "charge"),
    HALF(5, "half"),
    TRIPWIRE(6, "tripwire"),
    PISTON(7, "piston"),
    DOOR_PAIR(8, "door_pair"),
    RAIL(9, "rail"),
    SHAPE(10, "shape"),
    DISTANCE(11, "distance"),
    NC(12, "nc"),
    PP(13, "pp");

    private final int priority;
    private final String id;

    EdgeType(int priority, String id) {
        this.priority = priority;
        this.id = id;
    }

    public int priority() {
        return this.priority;
    }

    public String id() {
        return this.id;
    }

    public boolean directed() {
        return this != CIRCUIT && this != TRIPWIRE && this != DOOR_PAIR && this != DISTANCE;
    }

    public String translationKey() {
        return "rcv.edge." + this.id;
    }

    public static EdgeType byId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT).replace('-', '_');
        for (EdgeType type : values()) {
            if (type.id.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    public static EdgeType byOrdinal(int ordinal) {
        EdgeType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DIRECT_ACTIVATION;
    }
}
