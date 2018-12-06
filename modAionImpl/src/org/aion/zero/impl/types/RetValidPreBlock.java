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
package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.base.type.AionAddress;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;

public class RetValidPreBlock {

    public final List<AionTransaction> txs;
    public final Map<AionAddress, BigInteger> rewards;
    public final List<AionTxReceipt> receipts;
    public final List<AionTxExecSummary> summaries;

    public RetValidPreBlock(
            List<AionTransaction> txs,
            Map<AionAddress, BigInteger> rewards,
            List<AionTxReceipt> receipts,
            List<AionTxExecSummary> summaries) {
        this.txs = txs;
        this.rewards = rewards;
        this.receipts = receipts;
        this.summaries = summaries;
    }
}
