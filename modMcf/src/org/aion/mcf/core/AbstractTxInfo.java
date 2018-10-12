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
 ******************************************************************************/
package org.aion.mcf.core;

import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Abstract transaction info.
 */

public abstract class AbstractTxInfo<TXR extends AbstractTxReceipt<?>, TX extends AbstractTransaction> {

    protected TXR receipt;

    protected byte[] blockHash;

    protected byte[] parentBlockHash;

    protected int index;

    abstract public void setTransaction(TX tx);

    abstract public byte[] getEncoded();

    abstract public TXR getReceipt();

    abstract public byte[] getBlockHash();

    abstract public byte[] getParentBlockHash();

    abstract public void setParentBlockHash(byte[] hash);

    abstract public int getIndex();

    abstract public boolean isPending();
}
