package dev.rcvmod.rcv.core;

import java.util.Locale;

/** Neighbor-update (NC) coverage; default is OFF (§6.9). */
public enum NcMode {
    OFF,
    ALL;

    public static NcMode byName(String name) {
        return name.toLowerCase(Locale.ROOT).equals("all") ? ALL : OFF;
    }
}
