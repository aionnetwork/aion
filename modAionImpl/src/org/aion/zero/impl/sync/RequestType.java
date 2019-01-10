package org.aion.zero.impl.sync;

/**
 * Used for tracking different types of requests made to peers.
 *
 * @author Alexandra Roatis
 */
public enum RequestType {
    STATUS,
    HEADERS,
    BODIES
}
