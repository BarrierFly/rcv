package dev.rcvmod.rcv.core;

import java.util.Locale;

/**
 * The kinds of connections RCV can compute.
 *
 * <p>The enum order encodes the merge priority (see {@link #priority()}): when two edges share the
 * same {@code (from, to)} pair only the highest priority one is kept. {@link #CIRCUIT},
 * {@link #COMPARATOR_SIDE} and {@link #REPEATER_SIDE} intentionally share priority 2.
 *
 * <p>Constants are only ever appended at the end: {@link #byOrdinal} and
 * {@link TypeMask#truncatedMask} encode types by ordinal, so inserting in the middle would shift
 * every existing ordinal. The priority <em>values</em> are independent of that order and were
 * renumbered once ({@code DUST_TRAPDOOR} took the free slot 12, pushing NC/PP to 13/14) so that
 * the new type outranks PP - with {@code PP=ALL} the trap-door -> wire pair already carries a PP
 * edge, and same-pair merging would otherwise drop the new type entirely. The relative order of
 * the pre-existing priorities is unchanged, so no existing merge result changes.
 */
public enum EdgeType {
    DIRECT_ACTIVATION(1, "direct_activation"),
    CIRCUIT(2, "circuit"),
    COMPARATOR_SIDE(2, "comparator_side"),
    REPEATER_SIDE(2, "repeater_side"),
    ANALOG(3, "analog"),
    CHARGE(4, "charge"),
    HALF(5, "half"),
    TRIPWIRE(6, "tripwire"),
    PISTON(7, "piston"),
    DOOR_PAIR(8, "door_pair"),
    RAIL(9, "rail"),
    SHAPE(10, "shape"),
    DISTANCE(11, "distance"),
    NC(13, "nc"),
    PP(14, "pp"),
    DUST_TRAPDOOR(12, "dust_trapdoor");

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
