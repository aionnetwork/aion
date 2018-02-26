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
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.types;

import java.math.BigInteger;
import java.util.Arrays;

import org.aion.mcf.blockchain.AbstractPendingTx;

/**
 * aion pending transaction class.
 *
 */
public class AionPendingTx extends AbstractPendingTx<AionTransaction> {

    public AionPendingTx(byte[] bs) {
        super(bs);
    }

    public AionPendingTx(AionTransaction transaction) {
        super(transaction, 0);
    }

    public AionPendingTx(AionTransaction tx, long bn) {
        super(tx, bn);
    }

    protected void parse(byte[] bytes) {
        byte[] numberBytes = new byte[bytes[0]];
        byte[] txBytes = new byte[bytes.length - 1 - numberBytes.length];

        System.arraycopy(bytes, 1, numberBytes, 0, numberBytes.length);
        System.arraycopy(bytes, 1 + numberBytes.length, txBytes, 0, txBytes.length);

        this.blockNumber = new BigInteger(numberBytes).longValue();
        this.transaction = new AionTransaction(txBytes);
    }

    /**
     * Two pending transaction are equal if equal their sender + nonce
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AionPendingTx)) {
            return false;
        }

        AionPendingTx that = (AionPendingTx) o;

        return getSender().equals(that.getSender())
                && Arrays.equals(transaction.getNonce(), that.getTransaction().getNonce());
    }

}
