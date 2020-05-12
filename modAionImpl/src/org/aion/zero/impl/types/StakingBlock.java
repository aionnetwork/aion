package org.aion.zero.impl.types;

import static org.aion.zero.impl.types.StakingBlockHeader.DEFAULT_SIGNATURE;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.rlp.RLP;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;

public class StakingBlock extends AbstractBlock {

    /* Private */
    private StakingBlockHeader header;

    /* Constructors */
    private StakingBlock() {}

    // copy constructor
    public StakingBlock(StakingBlock block) {
        if (block == null) {
            throw new NullPointerException("block is null");
        }

        this.header = StakingBlockHeader.Builder.newInstance().withHeader(block.getHeader()).build();
        this.transactionsList.addAll(block.getTransactionsList());
    }

    /**
     * All construction using this codepath leads from DB queries or creation of new blocks
     *
     * @implNote do not use this construction path for unsafe sources
     */
    public StakingBlock(StakingBlockHeader header, List<AionTransaction> transactionsList) {
        if (header == null) {
            throw new NullPointerException("header is null");
        }

        if (transactionsList == null) {
            throw new NullPointerException("transaction list is null");
        }

        this.header = header;
        this.transactionsList.clear();
        this.transactionsList.addAll(transactionsList);
    }

    @VisibleForTesting
    public StakingBlock(
            byte[] parentHash,
            AionAddress coinbase,
            byte[] logsBloom,
            byte[] difficulty,
            long number,
            long timestamp,
            byte[] extraData,
            byte[] receiptsRoot,
            byte[] transactionsRoot,
            byte[] stateRoot,
            List<AionTransaction> transactionsList,
            long energyConsumed,
            long energyLimit,
            byte[] signature,
            byte[] seed,
            byte[] pubkey) {

        if (parentHash == null
                || coinbase == null
                || logsBloom == null
                || difficulty == null
                || extraData == null
                || receiptsRoot == null
                || transactionsRoot == null
                || stateRoot == null
                || transactionsList == null
                || signature == null
                || seed == null
                || pubkey == null) {
            throw new NullPointerException();
        }

        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();

        try {
            builder.withParentHash(parentHash)
                    .withCoinbase(coinbase)
                    .withLogsBloom(logsBloom)
                    .withDifficulty(difficulty)
                    .withNumber(number)
                    .withTimestamp(timestamp)
                    .withExtraData(extraData)
                    .withReceiptTrieRoot(receiptsRoot)
                    .withTxTrieRoot(transactionsRoot)
                    .withStateRoot(stateRoot)
                    .withEnergyConsumed(energyConsumed)
                    .withEnergyLimit(energyLimit)
                    .withSignature(signature)
                    .withSeed(seed)
                    .withSigningPublicKey(pubkey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.header = builder.build();
        this.transactionsList.clear();
        this.transactionsList.addAll(transactionsList);
    }

    /**
     * Constructor used in genesis creation, note that although genesis does check whether the
     * fields are correct, we emit a checked exception if in some unforseen circumstances we deem
     * the fields as incorrect
     */
    protected StakingBlock(
            byte[] parentHash,
            AionAddress coinbase,
            byte[] logsBloom,
            byte[] difficulty,
            long number,
            long timestamp,
            byte[] extraData,
            long energyLimit,
            byte[] seed) {

        if (parentHash == null) {
            throw new NullPointerException("parentHash is null");
        }

        if (coinbase == null) {

            throw new NullPointerException("coinbase is null");
        }

        if (logsBloom == null) {
            throw new NullPointerException("logBloom is null");
        }

        if (difficulty == null) {
            throw new NullPointerException("difficulty is null");
        }

        if (extraData == null) {
            throw new NullPointerException("extraData is null");
        }

        if (seed == null) {
            throw new NullPointerException("seed is null");
        }

        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.withParentHash(parentHash)
                .withCoinbase(coinbase)
                .withLogsBloom(logsBloom)
                .withDifficulty(difficulty)
                .withNumber(number)
                .withTimestamp(timestamp)
                .withExtraData(extraData)
                .withEnergyLimit(energyLimit)
                .withSeed(seed)
                .withDefaultStateRoot()
                .withDefaultSigningPublicKey()
                .withDefaultSignature()
                .withDefaultReceiptTrieRoot()
                .withDefaultTxTrieRoot();

        this.header = builder.build();
    }

    @Override
    public int size() {
        return getEncoded().length;
    }

    public StakingBlockHeader getHeader() {
        return this.header;
    }

    @Override
    public byte[] getHash() {
        return this.header.getHash();
    }

    @Override
    public byte[] getParentHash() {
        return this.header.getParentHash();
    }

    @Override
    public AionAddress getCoinbase() {
        return this.header.getCoinbase();
    }

    @Override
    public byte[] getStateRoot() {
        return this.header.getStateRoot();
    }

    @Override
    public byte[] getTxTrieRoot() {
        return this.header.getTxTrieRoot();
    }

    @Override
    public byte[] getReceiptsRoot() {
        return this.header.getReceiptsRoot();
    }

    @Override
    public byte[] getLogBloom() {
        return this.header.getLogsBloom();
    }

    @Override
    public byte[] getDifficulty() {
        return this.header.getDifficulty();
    }

    @Override
    public BigInteger getDifficultyBI() {
        return this.header.getDifficultyBI();
    }

    @Override
    public long getTimestamp() {
        return this.header.getTimestamp();
    }

    @Override
    public long getNumber() {
        return this.header.getNumber();
    }

    @Override
    public byte[] getExtraData() {
        return this.header.getExtraData();
    }

    public byte[] getSeed() {
        return this.header.getSeedOrProof();
    }

    @Override
    public List<AionTransaction> getTransactionsList() {
        return transactionsList;
    }

    /**
     * Facilitates the "finalization" of the block, after processing the necessary transactions.
     * This will be called during block creation and is considered the last step conducted by the
     * blockchain before handing it off to miner. This step is necessary to add post-execution
     * states:
     *
     * <p>{@link StakingBlockHeader#txTrieRoot} {@link StakingBlockHeader#receiptTrieRoot} {@link
     * StakingBlockHeader#stateRoot} {@link StakingBlockHeader#logsBloom} {@link this#transactionsList}
     * {@link StakingBlockHeader#energyConsumed}
     *
     * <p>The (as of now) unenforced contract by using this function is that the user should not
     * modify any fields set except for {@link StakingBlockHeader#pubkey} and {@link StakingBlockHeader#signature}
     * after this function is called.
     *
     * @param txs list of transactions input to the block (final)
     * @param txTrieRoot the rootHash of the transaction receipt, should correspond with {@code txs}
     * @param stateRoot the root of the world after transactions are executed
     * @param bloom the concatenated blooms of all logs emitted from transactions
     * @param receiptRoot the rootHash of the receipt trie
     * @param energyUsed the amount of energy consumed in the execution of the block
     */
    @SuppressWarnings("JavadocReference")
    public void updateTransactionAndState(
        List<AionTransaction> txs,
        byte[] txTrieRoot,
        byte[] stateRoot,
        byte[] bloom,
        byte[] receiptRoot,
        long energyUsed) {

        if (txs == null) {
            throw new NullPointerException("transaction list is null");
        }

        if (txTrieRoot == null) {
            throw new NullPointerException("txTrieRoot is null");
        }

        if (stateRoot == null) {
            throw new NullPointerException("stateRoot is null");
        }

        if (bloom == null) {
            throw new NullPointerException("bloom data is null");
        }

        if (receiptRoot == null) {
            throw new NullPointerException("receiptRoot is null");
        }

        header =
            StakingBlockHeader.Builder.newInstance()
                .withHeader(header)
                .withTxTrieRoot(txTrieRoot)
                .withStateRoot(stateRoot)
                .withLogsBloom(bloom)
                .withReceiptTrieRoot(receiptRoot)
                .withEnergyConsumed(energyUsed)
                .build();

        this.transactionsList.clear();
        this.transactionsList.addAll(txs);
    }

    @Override
    public String toString() {
        StringBuilder toStringBuff = new StringBuilder();

        toStringBuff.setLength(0);
        toStringBuff.append(Hex.toHexString(this.getEncoded())).append("\n");
        toStringBuff.append("BlockData [ ");
        toStringBuff.append("hash=").append(ByteUtil.toHexString(this.getHash())).append("\n");
        toStringBuff.append(header.toString());

        if (totalDifficulty != null) {
            toStringBuff.append(" total difficulty=").append(totalDifficulty).append("\n");
        }

        toStringBuff.append("  mainChain=").append(mainChain ? "yes" : "no").append("\n");

        if (!getTransactionsList().isEmpty()) {
            toStringBuff.append("Txs [\n");
            for (AionTransaction tx : getTransactionsList()) {
                toStringBuff.append(tx);
                toStringBuff.append("\n");
            }
            toStringBuff.append("]\n");
        } else {
            toStringBuff.append("Txs []\n");
        }
        toStringBuff.append("]");

        return toStringBuff.toString();
    }

    @Override
    public boolean isGenesis() {
        // we never expect a Staking Block to be genesis
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StakingBlock block = (StakingBlock) o;
        return Arrays.equals(getEncoded(), block.getEncoded());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getEncoded());
    }

    public byte[] getEncoded() {
        byte[] headerRlpEncoded = header.getEncoded();

        List<byte[]> block = getBodyElements();
        block.add(0, headerRlpEncoded);
        byte[][] elements = block.toArray(new byte[block.size()][]);

        return RLP.encodeList(elements);
    }

    @Override
    public String getShortHash() {
        return Hex.toHexString(getHash()).substring(0, 6);
    }

    @Override
    public String getShortDescr() {
        return "#"
            + getNumber()
            + " ("
            + Hex.toHexString(getHash()).substring(0, 6)
            + " <~ "
            + Hex.toHexString(getParentHash()).substring(0, 6)
            + ") Txs:"
            + getTransactionsList().size();
    }

    @Override
    public long getNrgConsumed() {
        return this.header.getEnergyConsumed();
    }

    @Override
    public long getNrgLimit() {
        return this.header.getEnergyLimit();
    }

    public void seal(byte[] sig, byte[] pubKey) {
        if (sig == null) {
            throw new NullPointerException("signature is null");
        }

        if (pubKey == null) {
            throw new NullPointerException("signing public key is null");
        }

        header =
                StakingBlockHeader.Builder.newInstance()
                        .withHeader(header)
                        .withSignature(sig)
                        .withSigningPublicKey(pubKey)
                        .build();
    }

    @VisibleForTesting
    @Override
    public void updateHeader(BlockHeader _header) {
        if (_header == null) {
            throw new NullPointerException();
        }

        header = (StakingBlockHeader) _header;
    }

    @Override
    public void updateHeaderDifficulty(byte[] diff) {
        if (diff == null) {
            throw new NullPointerException("difficulty is null");
        }
        this.header =
            StakingBlockHeader.Builder.newInstance()
                .withHeader(getHeader())
                .withDifficulty(diff)
                .build();
    }

    public boolean isSealed() {
        return !Arrays.equals(this.header.getSigningPublicKey(), ByteUtil.EMPTY_WORD)
                && !Arrays.equals(this.header.getSignature(), DEFAULT_SIGNATURE);
    }
}
