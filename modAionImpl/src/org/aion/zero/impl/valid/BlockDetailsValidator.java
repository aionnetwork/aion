package org.aion.zero.impl.valid;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.aion.log.LogEnum;
import org.aion.zero.impl.types.Block;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.BlockUtil;
import org.slf4j.Logger;

/**
 * The BlockDetailsValidator is for validating the status of the processed transactions of the new import block,
 * including totalEnergyUsed, rejectedTransactions, receiptRoot, bloomFilter, blockState.
 */
public class BlockDetailsValidator {

    private static boolean isValidEnergyUsed(long totalEnergyUsed, List<AionTxExecSummary> summaries, long blockNumber, Logger log) {
        long energyUsed = 0L;
        for (AionTxExecSummary summary : summaries) {
            energyUsed += summary.getReceipt().getEnergyUsed();
        }

        if (totalEnergyUsed != energyUsed) {
            log.warn(
                    "Invalid block#{}: calculated total transaction energy consumed[{}] mismatched with the energy used in header[{}]",
                    blockNumber,
                    energyUsed,
                    totalEnergyUsed);
            for (AionTxExecSummary summary : summaries) {
                log.warn("Tx[{}], Receipt[{}]", summary.getTransaction(), summary.getReceipt());
            }
            return false;
        } else {
            return true;
        }
    }

    private static boolean hasRejectedTransaction(long blockNumber, List<AionTxExecSummary> summaries, Logger log) {

        boolean hasRejectedTransaction = false;
        for (AionTxExecSummary summary : summaries) {
            if (summary.isRejected()) {
                log.warn(
                        "Invalid block#{}: contained reject transaction: Tx[{}] Receipt[{}]",
                        blockNumber,
                        summary.getTransaction(),
                        summary.getReceipt());
                hasRejectedTransaction = true;
            }
        }

        return hasRejectedTransaction;
    }

    private static boolean isReceiptRootMatched(byte[] receiptRoot, List<AionTxReceipt> receipts, long blockNumber, Logger log) {
        byte[] calculatedReceiptRoot = BlockUtil.calcReceiptsTrie(receipts);

        if (!Arrays.equals(receiptRoot, calculatedReceiptRoot)) {
            log.warn(
                "Invalid block#{}: calculated receipt root[{}] doesn't match with header receipt root[{}]",
                blockNumber,
                ByteUtil.toHexString(calculatedReceiptRoot),
                ByteUtil.toHexString(receiptRoot));
            for (AionTxReceipt receipt : receipts) {
                log.warn("Tx[{}], Receipt[{}]", receipt.getTransaction(), receipt);
            }
            return false;
        } else {
            return true;
        }
    }

    private static boolean isBloomFilterMatched(byte[] logBloom, List<AionTxReceipt> receipts, long blockNumber, Logger log) {
        byte[] calculatedLogBloom = BlockUtil.calcLogBloom(receipts);

        if (!Arrays.equals(logBloom, calculatedLogBloom)) {
            log.warn(
                "Invalid block#{}: calculated logBloom[{}] doesn't match with the header logBloom[{}]",
                    blockNumber,
                    ByteUtil.toHexString(calculatedLogBloom),
                    ByteUtil.toHexString(logBloom));
            return false;
         } else {
            return true;
        }
    }

    /**
     * Validate the block with the transaction execution summaries and receipts.
     * @param block given block to validate it.
     * @param summaries the summaries of the executed transactions
     * @param receipts the receipts of the executed transactions
     * @param skipRejectionTest boolean flag applied to main chain blocks that have already been accepted with rejected transactions
     * @param log the logger instance for printing log
     * @return the boolean value represent the validate result
     */
    public static boolean isValidBlock(Block block, List<AionTxExecSummary> summaries, List<AionTxReceipt> receipts, boolean skipRejectionTest, Logger log) {
        Objects.requireNonNull(block);
        Objects.requireNonNull(summaries);
        Objects.requireNonNull(receipts);
        Objects.requireNonNull(log);

        return (skipRejectionTest ? true : !hasRejectedTransaction(block.getNumber(), summaries, log))
                && isValidEnergyUsed(block.getHeader().getEnergyConsumed(), summaries, block.getNumber(), log)
                && isReceiptRootMatched(block.getReceiptsRoot(), receipts, block.getNumber(), log)
                && isBloomFilterMatched(block.getLogBloom(), receipts, block.getNumber(), log);
    }

    /**
     * Validate the blockHeader transaction trie root and the calculated transaction trie root.
     * @param headerTxTrieRoot the transaction trie root in the block header
     * @param transactionList the transactions of the block desired to validate it
     * @param blockNumber the block number of the block
     * @param log the logger instance for printing log
     * @return the boolean value represent the validate result
     */
    public static boolean isValidTxTrieRoot(byte[] headerTxTrieRoot, List<AionTransaction> transactionList, long blockNumber, Logger log) {
        Objects.requireNonNull(headerTxTrieRoot);
        Objects.requireNonNull(transactionList);
        Objects.requireNonNull(log);

        byte[] calculatedTxTrie =  BlockUtil.calcTxTrieRoot(transactionList);
        if (!Arrays.equals(headerTxTrieRoot, calculatedTxTrie)) {
            if (log.getName().equals(LogEnum.SYNC.name())) {
                log.debug(
                    "Invalid block#{}: calculated tx trie root[{}] doesn't match with the header tx trie root[{}]",
                    blockNumber,
                    ByteUtil.toHexString(calculatedTxTrie),
                    ByteUtil.toHexString(headerTxTrieRoot));
            } else {
                log.warn(
                    "Invalid block#{}: calculated tx trie root[{}] doesn't match with the header tx trie root[{}]",
                    blockNumber,
                    ByteUtil.toHexString(calculatedTxTrie),
                    ByteUtil.toHexString(headerTxTrieRoot));
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * Validate the blockHeader state root and the calculated state root from the kernel repo.
     * @param block the block instance
     * @param worldStateRoot the state root hash of the kernel repo
     * @param log the logger instance for printing log
     * @return the boolean value represent the validate result
     */
    public static boolean isValidStateRoot(Block block , byte[] worldStateRoot, Logger log) {
        Objects.requireNonNull(block);
        Objects.requireNonNull(worldStateRoot);
        Objects.requireNonNull(log);

        if (!Arrays.equals(block.getStateRoot(), worldStateRoot)) {
            log.warn(
                "Invalid block#{}: calculated worldState root[{}] doesn't match with the header state root[{}]",
                block.getNumber(),
                ByteUtil.toHexString(worldStateRoot),
                ByteUtil.toHexString(block.getStateRoot())
                );
            log.warn("Conflict block dump: {}", ByteUtil.toHexString(block.getEncoded()));
            return false;
        } else {
            return true;
        }
    }
}
