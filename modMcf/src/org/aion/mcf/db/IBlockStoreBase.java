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
package org.aion.mcf.db;

import java.util.List;
import org.aion.base.type.IBlock;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * BlockStore interface base.
 */
public interface IBlockStoreBase<BLK extends IBlock<?, ?>, BH extends AbstractBlockHeader> {

    byte[] getBlockHashByNumber(long blockNumber);

    byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash);

    BLK getChainBlockByNumber(long blockNumber);

    BLK getBlockByHash(byte[] hash);

    boolean isBlockExist(byte[] hash);

    List<byte[]> getListHashesEndWith(byte[] hash, long qty);

    List<BH> getListHeadersEndWith(byte[] hash, long qty);

    List<BLK> getListBlocksEndWith(byte[] hash, long qty);

    BLK getBestBlock();

    long getMaxNumber();

    void flush();

    void reBranch(BLK forkBlock);

    void revert(long previousLevel);

    void pruneAndCorrect();

    void load();

    void close();
}
