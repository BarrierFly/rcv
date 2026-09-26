package dev.rcvmod.rcv.core;

import java.util.Locale;

/** {@code IN} walks upstream (reverse BFS), {@code OUT} walks downstream. */
public enum QueryMode {
    IN,
    OUT;

    public static QueryMode byName(String name) {
        return name.toLowerCase(Locale.ROOT).startsWith("i") ? IN : OUT;
    }
}
