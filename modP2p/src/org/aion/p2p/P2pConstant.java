package org.aion.p2p;

public class P2pConstant {

    public static final int //
            STOP_CONN_AFTER_FAILED_CONN = 8, //
            FAILED_CONN_RETRY_INTERVAL = 3000, //
            BAN_CONN_RETRY_INTERVAL = 30_000, //
            MAX_BODY_SIZE = 2 * 1024 * 1024 * 16, //
            RECV_BUFFER_SIZE = 8192 * 1024, //
            SEND_BUFFER_SIZE = 8192 * 1024, //

            // max p2p in package capped at 2.
            READ_MAX_RATE = 2,

            // max p2p in package capped for tx broadcast.
            READ_MAX_RATE_TXBC = 20;
}
