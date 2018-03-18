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
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.aion.mcf.trie.Trie;
import org.aion.mcf.trie.TrieImpl;
import org.aion.mcf.types.AbstractBlock;
import org.slf4j.Logger;

/**
 *
 */
public class AionBlock extends AbstractBlock<A0BlockHeader, AionTransaction> implements IAionBlock {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.toString());

    /* Private */
    private byte[] rlpEncoded;
    private volatile boolean parsed = false;

    private Trie txsState;

    /* Constructors */
    private AionBlock() {
    }

    // copy constructor
    public AionBlock(AionBlock block) {
        this.header = new A0BlockHeader(block.getHeader());
        for (AionTransaction tx : block.getTransactionsList()) {
            this.transactionsList.add(tx.clone());
        }
        this.parsed = true;
    }

    public AionBlock(byte[] rawData) {
        LOG.debug("new from [" + Hex.toHexString(rawData) + "]");
        this.rlpEncoded = rawData;
    }

    public AionBlock(A0BlockHeader header, List<AionTransaction> transactionsList) {
        this(header.getParentHash(), header.getCoinbase(), header.getLogsBloom(), header.getDifficulty(),
                header.getNumber(), header.getTimestamp(), header.getExtraData(), header.getNonce(),
                header.getReceiptsRoot(), header.getTxTrieRoot(), header.getStateRoot(), transactionsList,
                header.getSolution(), header.getEnergyConsumed(), header.getEnergyLimit());
    }

    public AionBlock(byte[] parentHash, Address coinbase, byte[] logsBloom, byte[] difficulty, long number,
            long timestamp, byte[] extraData, byte[] nonce, byte[] receiptsRoot, byte[] transactionsRoot,
            byte[] stateRoot, List<AionTransaction> transactionsList, byte[] solutions, long energyConsumed,
            long energyLimit) {

        A0BlockHeader.Builder builder = new A0BlockHeader.Builder();
        builder.withParentHash(parentHash).withCoinbase(coinbase).withLogsBloom(logsBloom).withDifficulty(difficulty)
                .withNumber(number).withTimestamp(timestamp).withExtraData(extraData).withNonce(nonce)
                .withReceiptTrieRoot(receiptsRoot).withTxTrieRoot(transactionsRoot).withStateRoot(stateRoot)
                .withSolution(solutions).withEnergyConsumed(energyConsumed).withEnergyLimit(energyLimit);
        this.header = builder.build();
        this.transactionsList = transactionsList == null ? new CopyOnWriteArrayList<>() : transactionsList;
        this.parsed = true;
    }

    protected AionBlock(byte[] parentHash, Address coinbase, byte[] logsBloom, byte[] difficulty, long number,
            long timestamp, byte[] extraData, byte[] nonce, long energyLimit) {
        A0BlockHeader.Builder builder = new A0BlockHeader.Builder();
        builder.withParentHash(parentHash).withCoinbase(coinbase).withLogsBloom(logsBloom).withDifficulty(difficulty)
                .withNumber(number).withTimestamp(timestamp).withExtraData(extraData).withNonce(nonce)
                .withEnergyLimit(energyLimit);
        this.header = builder.build();
        this.parsed = true;
    }

    public void parseRLP() {
        if (this.parsed) {
            return;
        }

        synchronized (this) {
            if (this.parsed)
                return;
            
            RLPList params = RLP.decode2(rlpEncoded);
            RLPList block = (RLPList) params.get(0);

            // Parse Header
            RLPList header = (RLPList) block.get(0);
            this.header = new A0BlockHeader(header);

            // Parse Transactions
            RLPList txTransactions = (RLPList) block.get(1);
            this.parseTxs(this.header.getTxTrieRoot(), txTransactions);

            this.parsed = true;
        }
    }

    public int size() {
        return rlpEncoded.length;
    }

    public A0BlockHeader getHeader() {
        parseRLP();
        return this.header;
    }

    public byte[] getHash() {
        parseRLP();
        return this.header.getHash();
    }

    public byte[] getParentHash() {
        parseRLP();
        return this.header.getParentHash();
    }

    public Address getCoinbase() {
        parseRLP();
        return this.header.getCoinbase();
    }

    @Override
    public byte[] getStateRoot() {
        parseRLP();
        return this.header.getStateRoot();
    }

    @Override
    public void setStateRoot(byte[] stateRoot) {
        parseRLP();
        this.header.setStateRoot(stateRoot);
    }

    public byte[] getTxTrieRoot() {
        parseRLP();
        return this.header.getTxTrieRoot();
    }

    public byte[] getReceiptsRoot() {
        parseRLP();
        return this.header.getReceiptsRoot();
    }

    public byte[] getLogBloom() {
        parseRLP();
        return this.header.getLogsBloom();
    }

    @Override
    public byte[] getDifficulty() {
        parseRLP();
        return this.header.getDifficulty();
    }

    public BigInteger getDifficultyBI() {
        parseRLP();
        return this.header.getDifficultyBI();
    }

    public BigInteger getCumulativeDifficulty() {
        // TODO: currently returning incorrect total difficulty
        parseRLP();
        return new BigInteger(1, this.header.getDifficulty());
    }

    public long getTimestamp() {
        parseRLP();
        return this.header.getTimestamp();
    }

    @Override
    public long getNumber() {
        parseRLP();
        return this.header.getNumber();
    }

    public byte[] getExtraData() {
        parseRLP();
        return this.header.getExtraData();
    }

    public byte[] getNonce() {
        parseRLP();
        return this.header.getNonce();
    }

    public void setNonce(byte[] nonce) {
        this.header.setNonce(nonce);
        rlpEncoded = null;
    }

    public void setExtraData(byte[] data) {
        this.header.setExtraData(data);
        rlpEncoded = null;
    }

    public List<AionTransaction> getTransactionsList() {
        parseRLP();
        return transactionsList;
    }

    /**
     * Facilitates the "finalization" of the block, after processing the
     * necessary transactions. This will be called during block creation and is
     * considered the last step conducted by the blockchain before handing it
     * off to miner. This step is necessary to add post-execution states:
     *
     * {@link A0BlockHeader#txTrieRoot} {@link A0BlockHeader#receiptTrieRoot}
     * {@link A0BlockHeader#stateRoot} {@link A0BlockHeader#logsBloom}
     * {@link this#transactionsList} {@link A0BlockHeader#energyConsumed}
     *
     * The (as of now) unenforced contract by using this function is that the
     * user should not modify any fields set except for
     * {@link A0BlockHeader#solution} after this function is called.
     *
     * @param txs
     *            list of transactions input to the block (final)
     * @param txTrieRoot
     *            the rootHash of the transaction receipt, should correspond
     *            with {@code txs}
     * @param stateRoot
     *            the root of the world after transactions are executed
     * @param bloom
     *            the concatenated blooms of all logs emitted from transactions
     * @param receiptRoot
     *            the rootHash of the receipt trie
     * @param energyUsed
     *            the amount of energy consumed in the execution of the block
     */
    public void seal(List<AionTransaction> txs, byte[] txTrieRoot, byte[] stateRoot, byte[] bloom, byte[] receiptRoot,
            long energyUsed) {
        this.getHeader().setTxTrieRoot(txTrieRoot);
        this.getHeader().setStateRoot(stateRoot);
        this.getHeader().setLogsBloom(bloom);
        this.getHeader().setReceiptsRoot(receiptRoot);
        this.getHeader().setEnergyConsumed(energyUsed);

        this.transactionsList = txs;
        this.txsState = null; // wipe the txsState after setting
    }

    @Override
    public String toString() {
        StringBuilder toStringBuff = new StringBuilder();
        parseRLP();

        toStringBuff.setLength(0);
        toStringBuff.append(Hex.toHexString(this.getEncoded())).append("\n");
        toStringBuff.append("BlockData [ ");
        toStringBuff.append("hash=").append(ByteUtil.toHexString(this.getHash())).append("\n");
        toStringBuff.append(header.toString());

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

    public String toFlatString() {
        StringBuilder toStringBuff = new StringBuilder();
        parseRLP();

        toStringBuff.setLength(0);
        toStringBuff.append("BlockData [");
        toStringBuff.append("hash=").append(ByteUtil.toHexString(this.getHash()));
        toStringBuff.append(header.toFlatString());

        for (AionTransaction tx : getTransactionsList()) {
            toStringBuff.append("\n");
            toStringBuff.append(tx.toString());
        }

        toStringBuff.append("]");
        return toStringBuff.toString();
    }

    private void parseTxs(RLPList txTransactions) {

        this.txsState = new TrieImpl(null);
        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            this.transactionsList.add(new AionTransaction(transactionRaw.getRLPData()));
            this.txsState.update(RLP.encodeInt(i), transactionRaw.getRLPData());
        }
    }

    private boolean parseTxs(byte[] expectedRoot, RLPList txTransactions) {

        parseTxs(txTransactions);
        String calculatedRoot = Hex.toHexString(txsState.getRootHash());
        if (!calculatedRoot.equals(Hex.toHexString(expectedRoot))) {
            LOG.debug("Transactions trie root validation failed for block #{}", this.header.getNumber());
            return false;
        }

        return true;
    }

    public boolean isGenesis() {
        return this.header.isGenesis();
    }

    public boolean isEqual(AionBlock block) {
        return Arrays.equals(this.getHash(), block.getHash());
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] header = this.header.getEncoded();

            List<byte[]> block = getBodyElements();
            block.add(0, header);
            byte[][] elements = block.toArray(new byte[block.size()][]);

            this.rlpEncoded = RLP.encodeList(elements);
        }
        return rlpEncoded;
    }

    public byte[] getEncodedWithoutNonce() {
        parseRLP();
        return this.header.getEncodedWithoutNonce();
    }

    @Override
    public String getShortHash() {
        parseRLP();
        return Hex.toHexString(getHash()).substring(0, 6);
    }

    @Override
    public String getShortDescr() {
        return "#" + getNumber() + " (" + Hex.toHexString(getHash()).substring(0, 6) + " <~ "
                + Hex.toHexString(getParentHash()).substring(0, 6) + ") Txs:" + getTransactionsList().size();
    }

    @Override
    public long getNrgConsumed() {
        parseRLP();
        return this.header.getEnergyConsumed();
    }

    @Override
    public long getNrgLimit() {
        parseRLP();
        return this.header.getEnergyLimit();
    }

    public static AionBlock createBlockFromNetwork(A0BlockHeader header, byte[] body) {
        if (header == null || body == null)
            return null;

        AionBlock block = new AionBlock();
        block.header = header;
        block.parsed = true;

        RLPList items = (RLPList) RLP.decode2(body).get(0);
        RLPList transactions = (RLPList) items.get(0);

        if (!block.parseTxs(header.getTxTrieRoot(), transactions)) {
            return null;
        }

        return block;
    }
}
