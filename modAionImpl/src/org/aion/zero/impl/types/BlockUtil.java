package org.aion.zero.impl.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.Bloom;
import org.aion.base.ConstantUtil;
import org.aion.base.TxUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.BlockHeader.Seal;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.rlp.SharedRLPList;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.trie.Trie;
import org.aion.zero.impl.trie.TrieImpl;
import org.aion.zero.impl.valid.BlockDetailsValidator;
import org.slf4j.Logger;

/**
 * Utility for creating {@link Block} objects. The static methods in this class should be used
 * instead of instantiating the different block types using their constructors directly.
 *
 * @author Alexandra Roatis
 */
public final class BlockUtil {
    private static final Logger genLog = AionLoggerFactory.getLogger(LogEnum.GEN.name());
    private static final Logger syncLog = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    private BlockUtil() {
        throw new IllegalStateException("This utility class is not meant to be instantiated.");
    }

    /**
     * Decodes the given encoding into a new instance of a block or returns {@code null} if the RLP
     * encoding does not describe a valid block.
     *
     * @param rlp RLP encoded block data
     * @return a new instance of a block or {@code null} if the RLP encoding does not describe a
     *     valid block
     * @implNote Assumes the data is from a safe (internal) source.
     */
    public static Block newBlockFromRlp(byte[] rlp) {
        // return null when given empty bytes
        if (rlp == null || rlp.length == 0) {
            return null;
        }

        // attempt decoding, return null if it fails
        try {
            RLPList params = RLP.decode2(rlp);
            RLPList block = (RLPList) params.get(0);
            RLPList header = (RLPList) block.get(0);
            List<AionTransaction> txs = parseTransactions((RLPList) block.get(1));
            byte[] sealType = header.get(0).getRLPData();
            if (sealType[0] == Seal.PROOF_OF_WORK.getSealId()) {
                MiningBlockHeader miningHeader = MiningBlockHeader.Builder.newInstance().withRlpList(header).build();
                return new MiningBlock(miningHeader, txs);
            } else if (sealType[0] == Seal.PROOF_OF_STAKE.getSealId()) {
                StakingBlockHeader stakingHeader = StakingBlockHeader.Builder.newInstance().withRlpList(header).build();
                return new StakingBlock(stakingHeader, txs);
            } else {
                return null;
            }
        } catch (Exception e) {
            genLog.warn("Unable to decode block bytes " + Arrays.toString(rlp), e);
            return null;
        }
    }

    /**
     * Decodes the given encoding into a new instance of a block or returns {@code null} if the RLP
     * encoding does not describe a valid block.
     *
     * @param rlpList an RLPList instance encoding block data
     * @return a new instance of a block or {@code null} if the RLP encoding does not describe a
     *     valid block
     * @implNote Assumes the data is from an unsafe source.
     */
    public static Block newBlockFromUnsafeSource(RLPList rlpList) {
        // return null when given empty bytes
        if (rlpList == null || rlpList.size() != 2) {
            return null;
        }
        try {
            // parse header
            RLPList headerRLP = (RLPList) rlpList.get(0);
            byte[] type = headerRLP.get(0).getRLPData();
            RLPList transactionsRLP = (RLPList) rlpList.get(1);
            List<AionTransaction> txs = parseTransactions(transactionsRLP);
            if (type[0] == Seal.PROOF_OF_WORK.getSealId()) {
                MiningBlockHeader miningHeader = MiningBlockHeader.Builder.newInstance(true).withRlpList(headerRLP).build();
                if (!BlockDetailsValidator.isValidTxTrieRoot(miningHeader.getTxTrieRoot(), txs, miningHeader.getNumber(), syncLog)) {
                    return null;
                }
                return new MiningBlock(miningHeader, txs);
            } else if (type[0] == Seal.PROOF_OF_STAKE.getSealId()) {
                StakingBlockHeader stakingHeader = StakingBlockHeader.Builder.newInstance(true).withRlpList(headerRLP).build();
                if (!BlockDetailsValidator.isValidTxTrieRoot(stakingHeader.getTxTrieRoot(), txs, stakingHeader.getNumber(), syncLog)) {
                    return null;
                }
                return new StakingBlock(stakingHeader, txs);
            } else {
                return null;
            }
        } catch (Exception e) {
            syncLog.warn("Unable to decode block bytes " + Arrays.toString(rlpList.getRLPData()), e);
            return null;
        }
    }

    public static Block newBlockFromUnsafeSource(SharedRLPList rlpList) {
        // return null when given empty bytes
        if (rlpList == null || rlpList.size() != 2) {
            return null;
        }
        try {
            // parse header
            SharedRLPList headerRLP = (SharedRLPList) rlpList.get(0);
            byte[] type = headerRLP.get(0).getRLPData();
            SharedRLPList transactionsRLP = (SharedRLPList) rlpList.get(1);
            List<AionTransaction> txs = parseTransactions(transactionsRLP);
            if (type[0] == Seal.PROOF_OF_WORK.getSealId()) {
                MiningBlockHeader miningHeader = MiningBlockHeader.Builder.newInstance(true).withRlpList(headerRLP).build();
                if (!BlockDetailsValidator.isValidTxTrieRoot(miningHeader.getTxTrieRoot(), txs, miningHeader.getNumber(), syncLog)) {
                    return null;
                }
                return new MiningBlock(miningHeader, txs);
            } else if (type[0] == Seal.PROOF_OF_STAKE.getSealId()) {
                StakingBlockHeader stakingHeader = StakingBlockHeader.Builder.newInstance(true).withRlpList(headerRLP).build();
                if (!BlockDetailsValidator.isValidTxTrieRoot(stakingHeader.getTxTrieRoot(), txs, stakingHeader.getNumber(), syncLog)) {
                    return null;
                }
                return new StakingBlock(stakingHeader, txs);
            } else {
                return null;
            }
        } catch (Exception e) {
            syncLog.warn("Unable to decode block bytes " + Arrays.toString(SharedRLPList.getRLPDataCopy(rlpList)), e);
            return null;
        }
    }

    /**
     * Assembles a new block instance given its header and body. Returns {@code null} when given
     * invalid data.
     *
     * @param header the block header
     * @param bodyBytes the body of the block; can be an empty byte array when there are no
     *     transactions in the block
     * @return a new instance of a block or {@code null} when given invalid data
     * @implNote Assumes the body data is from an unsafe source.
     */
    public static Block newBlockWithHeaderFromUnsafeSource(BlockHeader header, byte[] bodyBytes) {
        // return null when given empty bytes
        if (header == null || bodyBytes == null) {
            return null;
        }
        try {
            RLPList items = (RLPList) RLP.decode2(bodyBytes).get(0);
            RLPList transactions = (RLPList) items.get(0);
            List<AionTransaction> txs = parseTransactions(transactions);
            if (!BlockDetailsValidator.isValidTxTrieRoot(header.getTxTrieRoot(), txs, header.getNumber(), syncLog)) {
                return null;
            }
            if (header.getSealType() == Seal.PROOF_OF_WORK) {
                return new MiningBlock((MiningBlockHeader) header, txs);
            } else if (header.getSealType() == Seal.PROOF_OF_STAKE) {
                return new StakingBlock((StakingBlockHeader) header, txs);
            } else {
                return null;
            }
        } catch (Exception e) {
            syncLog.warn("Unable to decode block with header " + header, e);
            return null;
        }
    }

    public static byte[] getTxTrieRootFromUnsafeSource(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes);

        try {
            RLPList items = (RLPList) RLP.decode2(bodyBytes).get(0);
            RLPList transactions = (RLPList) items.get(0);
            return calcTxTrieRootFromRLP(transactions);
        } catch (Exception e) {
            genLog.warn("Unable to decode block body=" + ByteArrayWrapper.wrap(bodyBytes), e);
            return null;
        }
    }

    private static byte[] calcTxTrieRootFromRLP(RLPList txTransactions) {
        Trie txsState = new TrieImpl(null);
        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            txsState.update(RLP.encodeInt(i), transactionRaw.getRLPData());
        }
        return txsState.getRootHash();
    }

    /**
     * Decodes the given encoding into a new instance of a block header or returns {@code null} if
     * the RLP encoding does not describe a valid block header.
     *
     * @param rlpList an RLPList instance encoding block header data
     * @return a new instance of a block header or {@code null} if the RLP encoding does not
     *     describe a valid block header
     * @implNote Assumes the data is from an unsafe source.
     */
    public static BlockHeader newHeaderFromUnsafeSource(RLPList rlpList) {
        // return null when given empty bytes
        if (rlpList == null) {
            return null;
        }

        // attempt decoding, return null if it fails
        try {
            byte[] sealType = rlpList.get(0).getRLPData();
            if (sealType[0] == Seal.PROOF_OF_WORK.getSealId()) {
                return MiningBlockHeader.Builder.newInstance(true).withRlpList(rlpList).build();
            } else if (sealType[0] == Seal.PROOF_OF_STAKE.getSealId()) {
                return StakingBlockHeader.Builder.newInstance(true).withRlpList(rlpList).build();
            } else {
                return null;
            }
        } catch (Exception e) {
            syncLog.warn("Unable to decode block bytes " + Arrays.toString(rlpList.getRLPData()), e);
            return null;
        }
    }

    /** Decodes the give transactions. */
    private static List<AionTransaction> parseTransactions(RLPList txTransactions) {
        List<AionTransaction> transactionsList = new ArrayList<>();
        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            transactionsList.add(TxUtil.decode(transactionRaw.getRLPData()));
        }
        return transactionsList;
    }

    private static List<AionTransaction> parseTransactions(SharedRLPList txTransactions) {
        List<AionTransaction> transactionsList = new ArrayList<>();
        for (RLPElement transactionRaw : txTransactions) {
            transactionsList.add(TxUtil.decode2(transactionRaw.getRLPData()));
        }
        return transactionsList;
    }

    public static byte[] calcTxTrieRoot(List<AionTransaction> transactions) {
        Objects.requireNonNull(transactions);

        if (transactions.isEmpty()) {
            return ConstantUtil.EMPTY_TRIE_HASH;
        }

        Trie txsState = new TrieImpl(null);

        for (int i = 0; i < transactions.size(); i++) {
            byte[] txEncoding = transactions.get(i).getEncoded();
            if (txEncoding != null) {
                txsState.update(RLP.encodeInt(i), txEncoding);
            } else {
                return ConstantUtil.EMPTY_TRIE_HASH;
            }
        }
        return txsState.getRootHash();
    }

    public static byte[] calcReceiptsTrie(List<AionTxReceipt> receipts) {
        Objects.requireNonNull(receipts);

        if (receipts.isEmpty()) {
            return ConstantUtil.EMPTY_TRIE_HASH;
        }

        Trie receiptsTrie = new TrieImpl(null);
        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie.update(RLP.encodeInt(i), receipts.get(i).getReceiptTrieEncoded());
        }
        return receiptsTrie.getRootHash();
    }

    public static byte[] calcLogBloom(List<AionTxReceipt> receipts) {
        Objects.requireNonNull(receipts);

        if (receipts.isEmpty()) {
            return new byte[Bloom.SIZE];
        }

        Bloom retBloomFilter = new Bloom();
        for (AionTxReceipt receipt : receipts) {
            retBloomFilter.or(receipt.getBloomFilter());
        }

        return retBloomFilter.getBloomFilterBytes();
    }
}
