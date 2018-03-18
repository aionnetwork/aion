/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server.types;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.util.TypeConverter;
import org.aion.mcf.vm.types.Log;

public class TxRecptLg {

    public String address;

    public String blockHash;

    public String blockNumber;

    public String data;

    public String logIndex;

    public String[] topics;

    public String transactionHash;

    public String transactionIndex;

    // true when the log was removed, due to a chain reorganization. false if its a valid log.
    public boolean removed;

    public <TX extends ITransaction> TxRecptLg(Log logInfo, IBlock b, Integer txIndex, TX tx, int logIdx, boolean isMainchain) {
        this.logIndex = TypeConverter.toJsonHex(logIdx);
        this.blockNumber = b == null ? null : TypeConverter.toJsonHex(b.getNumber());
        this.blockHash = b == null ? null : TypeConverter.toJsonHex(b.getHash());
        this.transactionIndex = (b == null || txIndex == null) ? null : TypeConverter.toJsonHex(txIndex);
        this.transactionHash = TypeConverter.toJsonHex(tx.getHash());
        this.address = (tx == null || tx.getTo() == null) ? "" : TypeConverter.toJsonHex(tx.getTo().toBytes());
        this.data = TypeConverter.toJsonHex(logInfo.getData());
        this.removed = !isMainchain;

        this.topics = new String[logInfo.getTopics().size()];
        for (int i = 0, m = this.topics.length; i < m; i++) {
            this.topics[i] = TypeConverter.toJsonHex(logInfo.getTopics().get(i));
        }
    }
}