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
            REQUEST_SIZE = 24,
            LARGE_REQUEST_SIZE = 40,

            /**
             * The number of blocks overlapping with the current chain requested at import when the
             * local best block is far from the top block in the peer's chain.
             */
            FAR_OVERLAPPING_BLOCKS = 3,

            /**
             * The number of blocks overlapping with the current chain requested at import when the
             * local best block is close to the top block in the peer's chain.
             */
            CLOSE_OVERLAPPING_BLOCKS = 15,
            STEP_COUNT = 6,
            MAX_NORMAL_PEERS = 16,
            COEFFICIENT_NORMAL_PEERS = 2,

            // NOTE: the 3 values below are interdependent
            // do not change one without considering the impact to the others
            BACKWARD_SYNC_STEP = REQUEST_SIZE * STEP_COUNT - 1;
}
