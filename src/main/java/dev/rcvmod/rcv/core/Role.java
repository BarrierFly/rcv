package dev.rcvmod.rcv.core;

/** Logical roles a block can play in the redstone graph (§5.1). */
public enum Role {
    SOURCE,
    ANALOG_SOURCE,
    TRANSMITTER,
    CONSUMER,
    CONDUCTOR,
    MOVED
}
