package org.aion.zero.impl.valid;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.aion.base.AionTransaction;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.blockchain.IAionBlockchain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/** Validates beacon hash of transaction */
public class BeaconHashValidator {
    private final IAionBlockchain blockchain;
    private final long fork050Number;
    private static final Logger TX_LOG = LoggerFactory.getLogger(LogEnum.TX.name());

    /**
     * The value to use for fork050Number when calling constructor {@link
     * #BeaconHashValidator(IAionBlockchain, long)} if beacon hash validation is
     * disabled.
     */
    public static final long FORK_050_DISABLED = Long.MAX_VALUE;

    /**
     * Constructor.
     *
     * @param blockchain blockchain
     * @param fork050Number the block number at which hard fork 0.5.0 takes effect.  Use
     *                      {@link #FORK_050_DISABLED} if validation should be disabled;
     *                      i.e. behave as if fork050 never happens and always return true
     *                      when {@link #validateTxForBlock(AionTransaction, Block)} or
     *                      {@link #validateTxForPendingState(AionTransaction)} is called
     */
    public BeaconHashValidator(IAionBlockchain blockchain, long fork050Number) {
        Preconditions.checkNotNull(blockchain, "Blockstore can't be null");
        Preconditions.checkArgument(fork050Number >= 0, "Invalid fork0.5.0 block number: must be >= 0");

        this.blockchain = blockchain;
        this.fork050Number = fork050Number;
    }

    /**
     * Evaluate if a transaction's (possibly null) beacon hash is valid for a particular block.
     *
     * If the block has not reached the fork 0.5.0 block number, or fork 0.5.0 is disabled,
     * the transaction must not provide a beacon hash (i.e. must be null), otherwise it is
     * not valid.
     *
     * If the block has reached the fork 0.5.0 block number, the transaction is valid if
     * either the beacon hash is absent (i.e. is null) or the beacon hash is the hash of a
     * block on the chain that {@link Block#getParentHash()} is on.
     *
     * @param tx transaction to validate
     * @param block block that the transaction is in
     * @return whether beacon hash of transaction is valid
     */
    public boolean validateTxForBlock(AionTransaction tx, Block block) {
        long t0 = System.nanoTime();
        try {
            Preconditions.checkNotNull(tx, "AionTransaction must not be null");
            Preconditions.checkNotNull(block, "Block must not be null");

            byte[] beaconHash = tx.getBeaconHash();
            if (beaconHash == null) {
                return true;
            }

            if (!isAfterFork050(block.getNumber()) && beaconHash != null) {
                return false;
            }

            if (blockchain.isMainChain(block.getParentHash())) {
                boolean isMainChain = blockchain.isMainChain(beaconHash);
                TX_LOG.debug(String.format(
                        "BeaconHashValidator#validateTxForBlock: isMainChain(0x%s) = %b.",
                        ByteUtil.toHexString(beaconHash), isMainChain));
                return isMainChain;
            } else {
                boolean onCorrectSidechain = checkSideChain(beaconHash,
                        blockchain.getBlockByHash(block.getParentHash()));
                TX_LOG.debug(String.format(
                        "BeaconHashValidator#validateTxForBlock: checkSideChain(0x%s, 0x%s) = %b.",
                        ByteUtil.toHexString(beaconHash),
                        ByteUtil.toHexString(block.getParentHash()),
                        onCorrectSidechain));
                return onCorrectSidechain;
            }
        } finally {
            TX_LOG.debug("BeaconHashValidator#validateTxForBlock: tx {} took {} usec",
                    ByteUtil.toHexString(block.getHash()),
                    (System.nanoTime() - t0)/1000);
        }
    }

    /**
     * Evaluate if a transaction's (possibly null) beacon hash is valid for pending state.
     *
     * If the main chain's (best block + 1) has not reached the fork 0.5.0 block number, or
     * fork 0.5.0 is disabled, the transaction must not provide a beacon hash (i.e.
     * must be null), otherwise it is not valid.
     *
     * If the main chain's (best block + 1) has reached the fork 0.5.0 block number, the
     * transaction is valid if either the beacon hash is absent (i.e. is null) or the beacon
     * hash is on the main chain.
     *
     * @param tx transaction to validate
     * @return whether beacon hash of transaction is valid
     */
    public boolean validateTxForPendingState(AionTransaction tx) {
        long t0 = System.nanoTime();
        try {
            Preconditions.checkNotNull(tx, "AionTransaction must not be null");

            byte[] beaconHash = tx.getBeaconHash();
            if (beaconHash == null) {
                return true;
            }

            // the next block number might be larger than (current best + 1), but
            // we will tolerate false negatives
            long minNextBlockNumber = blockchain.getBestBlock().getNumber() + 1;
            if (!isAfterFork050(minNextBlockNumber) && beaconHash != null) {
                return false;
            }

            boolean isMainChain = blockchain.isMainChain(beaconHash);
            TX_LOG.debug(String.format("BeaconHashValidator#validate: isMainChain(0x%s) = %b.",
                    ByteUtil.toHexString(beaconHash), isMainChain));
            return isMainChain;
        } finally {
            TX_LOG.debug("BeaconHashValidator#validateTxForPendingState: took {} usec",
                    (System.nanoTime() - t0)/1000);
        }
    }

    /**
     * @return true if blockNumber >= fork050Number and fork050 not disabled;
     *         false in all other cases
     */
    @VisibleForTesting boolean isAfterFork050(long blockNumber) {
        return blockNumber >= fork050Number && fork050Number != FORK_050_DISABLED;
    }

    private boolean checkSideChain(byte[] beaconHash, Block sideChainHead) {
        Block beaconBlock = blockchain.getBlockByHash(beaconHash);
        if(beaconBlock == null) {
            return false;
        }

        Block cur = sideChainHead;
        long beaconNumber = beaconBlock.getNumber();

        while(null != cur) {
            if(Arrays.equals(beaconHash, cur.getHash())) {
                return true;
            } else if(beaconNumber >= cur.getNumber()) {
                TX_LOG.debug("BeaconHashValidator#checkSideChain: reached level of beacon hash block {}",
                        ByteUtil.toHexString(cur.getHash()));
                return false;
            } else if(blockchain.isMainChain(cur.getHash(), cur.getNumber())) {
                TX_LOG.debug("BeaconHashValidator#checkSideChain: found fork point to mainchain at {}",
                        ByteUtil.toHexString(cur.getHash()));
                return blockchain.isMainChain(beaconHash);
            }

            cur = blockchain.getBlockByHash(cur.getParentHash());
        }

        // should not actually reach this since genesis block is a main chain block;
        // there is either a bug in the code or something broken in the db
        throw new IllegalStateException(
                "BeaconHashValidator#checkSideChain: reached orphaned block without finding mainchain");
    }
}
