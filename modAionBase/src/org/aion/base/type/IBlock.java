/*
 * @Copyright Nuco Inc. 2016
 * @Author jin@nuco.io *
 */
package org.aion.base.type;

import java.util.List;

/** @author jin */
public interface IBlock<TX extends ITransaction, BH extends IBlockHeader> {

    long getNumber();

    byte[] getParentHash();

    byte[] getHash();

    byte[] getEncoded();

    String getShortHash();

    boolean isEqual(IBlock<TX, BH> block);

    String getShortDescr();

    List<TX> getTransactionsList();

    BH getHeader();

    /**
     * Newly added with the refactory of API for libNc, both chains should have implemented this
     *
     * @return
     */
    byte[] getReceiptsRoot();

    long getTimestamp();
}
