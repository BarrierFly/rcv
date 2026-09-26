package dev.rcvmod.rcv.core;

import java.util.Locale;

/** Shape-update (PP) coverage. */
public enum PpMode {
    /** No PP edges at all. */
    OFF,
    /** Only observers reacting to the block in front (default, §6.10 tier). */
    OBSERVER_ONLY,
    /** All shape updates, including indirect ones for redstone wire. */
    ALL;

    public static PpMode byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return switch (n) {
            case "off", "false", "none" -> OFF;
            case "all", "full" -> ALL;
            default -> OBSERVER_ONLY;
        };
    }
}
