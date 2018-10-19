/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 *
 * <p>****************************************************************************
 */
package org.aion.mcf.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.aion.base.type.IBlock;
import org.aion.base.type.IBlockHeader;
import org.aion.rlp.RLP;

/** Abstract Block class. */
public abstract class AbstractBlock<BH extends IBlockHeader, TX extends AbstractTransaction>
        implements IBlock<TX, BH> {

    protected BH header;

    protected List<TX> transactionsList = new CopyOnWriteArrayList<>();

    @Override
    public boolean isEqual(IBlock<TX, BH> block) {
        return Arrays.equals(this.getHash(), block.getHash());
    }

    public abstract String getShortDescr();

    public abstract void parseRLP();

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
    public boolean isParentOf(IBlock<TX, BH> block) {
        return Arrays.equals(this.getHash(), block.getParentHash());
    }

    public byte[] getEncodedBody() {
        List<byte[]> body = getBodyElements();
        byte[][] elements = body.toArray(new byte[body.size()][]);
        return RLP.encodeList(elements);
    }

    public List<byte[]> getBodyElements() {
        parseRLP();
        byte[] transactions = getTransactionsEncoded();
        List<byte[]> body = new ArrayList<>();
        body.add(transactions);
        return body;
    }

    public byte[] getTransactionsEncoded() {

        byte[][] transactionsEncoded = new byte[transactionsList.size()][];
        int i = 0;
        for (TX tx : transactionsList) {
            transactionsEncoded[i] = tx.getEncoded();
            ++i;
        }
        return RLP.encodeList(transactionsEncoded);
    }
}
