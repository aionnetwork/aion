package org.aion.p2p;

import org.aion.base.Constant;

public class P2pConstant {

    public static final int STOP_CONN_AFTER_FAILED_CONN = 8;

    public static final long FAILED_CONN_RETRY_INTERVAL = 3000;

    public static final long BAN_CONN_RETRY_INTERVAL = 30_000;

    public static final int MAX_BODY_SIZE = Constant.MAX_BLK_SIZE * 32;

    public static final int RECV_BUFFER_SIZE = 64 * 1024;

    public static final int SEND_BUFFER_SIZE = 64 * 1024;

}
