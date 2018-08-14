/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

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
