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
import org.aion.zero.impl.types.BlockHeader.Seal;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
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
            SharedRLPList params = RLP.decode2SharedList(rlp);
            SharedRLPList block = (SharedRLPList) params.get(0);
            SharedRLPList header = (SharedRLPList) block.get(0);
            List<AionTransaction> txs = parseTransactions((SharedRLPList) block.get(1));
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

    public static Block newBlockFromSharedRLPList(SharedRLPList rlpList) {
        // return null when given empty bytes
        if (rlpList == null || rlpList.isEmpty()) {
            return null;
        }

        try {
            SharedRLPList header = (SharedRLPList) rlpList.get(0);
            List<AionTransaction> txs = parseTransactions((SharedRLPList) rlpList.get(1));
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
            genLog.warn("Unable to decode block bytes " + Arrays.toString(SharedRLPList.getRLPDataCopy(rlpList)), e);
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
    public static Block newBlockFromUnsafeSource(SharedRLPList rlpList) {
        // return null when given empty bytes
        if (rlpList == null || rlpList.size() != 2) {
            return null;
        }
        try {
            // parse header
            RLPElement element = rlpList.get(0);
            if (element.isList()) {
                SharedRLPList headerRLP = (SharedRLPList) element;
                byte[] type = headerRLP.get(0).getRLPData();

                if (type[0] == Seal.PROOF_OF_WORK.getSealId()) {
                    MiningBlockHeader miningHeader = MiningBlockHeader.Builder.newInstance(true).withRlpList(headerRLP).build();
                    SharedRLPList transactionsRLP = (SharedRLPList) rlpList.get(1);
                    List<AionTransaction> txs = parseTransactions(transactionsRLP);
                    if (!BlockDetailsValidator.isValidTxTrieRoot(miningHeader.getTxTrieRoot(), txs, miningHeader.getNumber(), syncLog)) {
                        return null;
                    }
                    return new MiningBlock(miningHeader, txs);
                } else if (type[0] == Seal.PROOF_OF_STAKE.getSealId()) {
                    StakingBlockHeader stakingHeader = StakingBlockHeader.Builder.newInstance(true).withRlpList(headerRLP).build();
                    SharedRLPList transactionsRLP = (SharedRLPList) rlpList.get(1);
                    List<AionTransaction> txs = parseTransactions(transactionsRLP);
                    if (!BlockDetailsValidator.isValidTxTrieRoot(stakingHeader.getTxTrieRoot(), txs, stakingHeader.getNumber(), syncLog)) {
                        return null;
                    }
                    return new StakingBlock(stakingHeader, txs);
                } else {
                    return null;
                }
            } else {
                throw new IllegalArgumentException("The first element in the rlpList is not a list");
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
     * @param txList the body of the block; can be an empty byte array when there are no
     *     transactions in the block
     * @return a new instance of a block or {@code null} when given invalid data
     * @implNote Assumes the body data is from an unsafe source.
     */
    public static Block newBlockWithHeaderFromUnsafeSource(BlockHeader header, SharedRLPList txList) {
        // return null when given empty bytes
        if (header == null || txList == null) {
            return null;
        }
        try {
            List<AionTransaction> txs = parseTransactions(txList);
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

    public static byte[] getTxTrieRootFromUnsafeSource(SharedRLPList txList) {
        Objects.requireNonNull(txList);

        try {
            return calcTxTrieRootFromRLP((SharedRLPList) txList.get(0));
        } catch (Exception e) {
            genLog.warn("Unable to decode block body=" + ByteArrayWrapper.wrap(SharedRLPList.getRLPDataCopy(
                (SharedRLPList) txList.get(0))), e);
            return null;
        }
    }

    private static byte[] calcTxTrieRootFromRLP(SharedRLPList txTransactions) {
        Trie txsState = new TrieImpl(null);
        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            if (transactionRaw.isList()) {
                txsState.update(RLP.encodeInt(i), SharedRLPList.getRLPDataCopy(
                    (SharedRLPList) transactionRaw));
            } else {
                throw new IllegalArgumentException("The transaction rlpElement should be a List");
            }
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
    public static BlockHeader newHeaderFromUnsafeSource(SharedRLPList rlpList) {
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
            syncLog.warn("Unable to decode block bytes " + Arrays.toString(SharedRLPList.getRLPDataCopy(rlpList)), e);
            return null;
        }
    }

    /** Decodes the give transactions. */
    private static List<AionTransaction> parseTransactions(SharedRLPList rlpTxs) {
        List<AionTransaction> transactionsList = new ArrayList<>();
        for (RLPElement rlpTx : rlpTxs) {
            transactionsList.add(TxUtil.decodeUsingRlpSharedList((SharedRLPList) rlpTx));
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
