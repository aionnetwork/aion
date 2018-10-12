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

package org.aion.gui.util;

import java.math.BigInteger;

public class AionConstants {

    public static final String AION_URL = "http://mainnet.aion.network";
    public final static String CCY = "AION";
    public static final String DEFAULT_NRG = "22000";
    public static final int BLOCK_MINING_TIME_SECONDS = 10;
    public static final Long BLOCK_MINING_TIME_MILLIS = BLOCK_MINING_TIME_SECONDS * 1000L;
    public static final int VALIDATION_BLOCKS_FOR_TRANSACTIONS = 50;
    private static final long AMP = (long) 1E9;
    public static final BigInteger DEFAULT_NRG_PRICE = BigInteger.valueOf(10 * AMP);

    private AionConstants() {
    }
}
