package org.aion.p2p;

public class P2pConstant {

    public static final int //

    STOP_CONN_AFTER_FAILED_CONN = 8, //

    FAILED_CONN_RETRY_INTERVAL = 3000, //

    BAN_CONN_RETRY_INTERVAL = 30_000, //

    MAX_BODY_SIZE = 2 * 1024 * 1024 * 32, //

    RECV_BUFFER_SIZE = 8192 * 1024, //

    SEND_BUFFER_SIZE = 8192 * 1024, //

    // max p2p in package capped at 1.
    READ_MAX_RATE = 1,

    // max p2p in package capped for tx broadcast.
    READ_MAX_RATE_TXBC = 20,

    // write queue timeout
    WRITE_MSG_TIMEOUT = 5000,

    BACKWARD_SYNC_STEP = 128;
}
