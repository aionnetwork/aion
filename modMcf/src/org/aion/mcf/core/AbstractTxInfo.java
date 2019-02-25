package org.aion.mcf.core;

import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Abstract transaction info.
 *
 * @param <TXR>
 * @param <TX>
 */
public abstract class AbstractTxInfo<
        TXR extends AbstractTxReceipt<?>, TX extends AbstractTransaction> {

    protected TXR receipt;

    protected byte[] blockHash;

    protected byte[] parentBlockHash;

    protected int index;

    public abstract void setTransaction(TX tx);

    public abstract byte[] getEncoded();

    public abstract TXR getReceipt();

    public abstract byte[] getBlockHash();

    public abstract byte[] getParentBlockHash();

    public abstract void setParentBlockHash(byte[] hash);

    public abstract int getIndex();

    public abstract boolean isPending();
}
