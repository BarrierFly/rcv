package dev.rcvmod.rcv.core;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** User-facing override for the dust-trapdoor gate. */
public enum DustTrapdoorMode {
    /** Decide from the environment; see {@link DustTrapdoorEra}. */
    AUTO,
    /** Force the pre-1.20 behaviour, i.e. draw {@link EdgeType#DUST_TRAPDOOR} edges. */
    ON,
    /** Force the 1.20+ behaviour, i.e. never draw them. */
    OFF;

    /** Lenient parse: anything unrecognised (including a missing key) falls back to {@link #AUTO}. */
    public static DustTrapdoorMode parse(@Nullable String value) {
        if (value != null) {
            for (DustTrapdoorMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value.trim().toUpperCase(Locale.ROOT))) {
                    return mode;
                }
            }
        }
        return AUTO;
    }
}