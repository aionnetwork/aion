package org.aion.generic;

public enum BlockPropagationStatus {
    DROPPED, // block was invalid, drop no propagation
    PROPAGATED, // block was propagated, but was not connected
    CONNECTED, // block was ONLY connected, not propagated
    PROP_CONNECTED // block propagated and connected
}
