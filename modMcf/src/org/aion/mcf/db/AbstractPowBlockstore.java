package org.aion.mcf.db;

import org.aion.mcf.types.AbstractBlock;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * Abstract POW blockstore.
 *
 * @param <BLK>
 * @param <BH>
 */
public abstract class AbstractPowBlockstore<
                BLK extends AbstractBlock<?, ?>, BH extends AbstractBlockHeader>
        implements IBlockStorePow<BLK, BH> {

    @Override
    public byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash) {
        BLK branchBlock = getBlockByHash(branchBlockHash);
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
