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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
/*
 * @Copyright Nuco Inc. 2016
 * @Author jin@nuco.io * 
 */
package org.aion.base.type;

import java.util.List;

/**
 *
 * @author jin
 */
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
     * Newly added with the refactory of API for libNc, both chains should have
     * implemented this
     * 
     * @return
     */
    byte[] getReceiptsRoot();

    long getTimestamp();

    Address getCoinbase();

    byte[] getStateRoot();

    void setStateRoot(byte[] stateRoot);

    byte[] getTxTrieRoot();

    byte[] getLogBloom();

    long getNrgConsumed();

    long getNrgLimit();
}
