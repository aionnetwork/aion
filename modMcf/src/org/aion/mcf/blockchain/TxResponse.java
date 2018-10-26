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

package org.aion.mcf.blockchain;

public enum TxResponse {
    SUCCESS(0, false),
    INVALID_TX(1, true),
    INVALID_TX_NRG_PRICE(2, true),
    INVALID_FROM(3, true),
    INVALID_ACCOUNT(4, true),
    ALREADY_CACHED(5, false),
    CACHED_NONCE(6, false),
    CACHED_POOLMAX(7, false),
    REPAID(8, false),
    ALREADY_SEALED(9, false),
    REPAYTX_POOL_EXCEPTION(10, true),
    REPAYTX_LOWPRICE(11, true),
    DROPPED(12, true),
    EXCEPTION(13, true);

    private int val;
    private boolean fail;

    TxResponse(int val, boolean fail) {
        this.val = val;
        this.fail = fail;
    }

    public int getVal() {
        return val;
    }

    public boolean isFail() {
        return fail;
    }
}
