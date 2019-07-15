package org.aion.mcf.db;

import org.aion.mcf.blockchain.Block;

/**
 * Abstract POW blockstore.
 *
 */
public abstract class AbstractPowBlockstore implements IBlockStorePow {

    @Override
    public byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash) {
        Block branchBlock = getBlockByHash(branchBlockHash);
        if (branchBlock.getNumber() < blockNumber) {
            throw new IllegalArgumentException(
                    "Requested block number > branch hash number: "
                            + blockNumber
                            + " < "
                            + branchBlock.getNumber());
        }
        while (branchBlock.getNumber() > blockNumber) {
            branchBlock = getBlockByHash(branchBlock.getParentHash());
        }
        return branchBlock.getHash();
    }
}
