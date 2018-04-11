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
 *
 * Contributors:
 *     Aion foundation.

 ******************************************************************************/
package org.aion.mcf.types;

import java.math.BigInteger;

import org.aion.base.type.Address;
import org.spongycastle.util.BigIntegers;

/**
 * Abstract BlcokHeader.
 */
public abstract class AbstractBlockHeader {

    public static final int NONCE_LENGTH = 8;
    public static final int SOLUTIONSIZE = 1408;


    protected byte version;

    /* The SHA3 256-bit hash of the parent block, in its entirety */
    protected byte[] parentHash;

    /*
     * The 256-bit address to which all fees collected from the successful
     * mining of this block be transferred; formally
     */
    protected Address coinbase;
    /*
     * The SHA3 256-bit hash of the root node of the state trie, after all
     * transactions are executed and finalisations applied
     */
    protected byte[] stateRoot;
    /*
     * The SHA3 256-bit hash of the root node of the trie structure populated
     * with each transaction in the transaction list portion, the trie is
     * populate by [key, val] --> [rlp(index), rlp(tx_recipe)] of the block
     */
    protected byte[] txTrieRoot;
    /*
     * The SHA3 256-bit hash of the root node of the trie structure populated
     * with each transaction recipe in the transaction recipes list portion, the
     * trie is populate by [key, val] --> [rlp(index), rlp(tx_recipe)] of the
     * block
     */
    protected byte[] receiptTrieRoot;

    /* todo: comment it when you know what the fuck it is */
    protected byte[] logsBloom;
    /*
     * A scalar value corresponding to the difficulty level of this block. This
     * can be calculated from the previous block’s difficulty level and the
     * timestamp
     */
    protected byte[] difficulty;

    /*
     * A scalar value equal to the reasonable output of Unix's time() at this
     * block's inception
     */
    protected long timestamp;

    /*
     * A scalar value equal to the number of ancestor blocks. The genesis block
     * has a number of zero
     */
    protected long number;

    /*
     * An arbitrary byte array containing data relevant to this block. With the
     * exception of the genesis block, this must be 32 bytes or fewer
     */
    protected byte[] extraData;
    /*
     * A 256-bit hash which proves that a sufficient amount of computation has
     * been carried out on this block
     */
    protected byte[] nonce;

    /////////////////////////////////////////////////////////////////
    protected byte[] solutionSize; // Size of the equihash solution in bytes
    // (1344 in 200-9, 1408 in 210,9)
    protected byte[] solution; // The equihash solution in compressed format

    /*
    * A long value containing energy consumed within this block
     */
    protected long energyConsumed;

    /*
     * A long value containing energy limit of this block
     */
    protected long energyLimit;

    public byte[] getSolutionSize() {
        return solutionSize;
    }

    public byte[] getSolution() {
        return solution;
    }

    public void setSolutionSize(byte[] solutionSize) {
        this.solutionSize = solutionSize;
    }

    public void setSolution(byte[] solution) {
        this.solution = solution;
    }

    public AbstractBlockHeader() {
    }

    public byte[] getParentHash() {
        return parentHash;
    }

    public Address getCoinbase() {
        return coinbase;
    }

    public void setCoinbase(Address coinbase) {
        this.coinbase = coinbase;
    }

    public byte[] getStateRoot() {
        return this.stateRoot;
    }

    public void setStateRoot(byte[] stateRoot) {
        this.stateRoot = stateRoot;
    }

    public byte[] getTxTrieRoot() {
        return txTrieRoot;
    }

    public void setTxTrieRoot(byte[] txTrieRoot) {
        this.txTrieRoot = txTrieRoot;
    }

    public void setReceiptsRoot(byte[] receiptTrieRoot) {
        this.receiptTrieRoot = receiptTrieRoot;
    }

    public byte[] getReceiptsRoot() {
        return receiptTrieRoot;
    }

    public void setTransactionsRoot(byte[] stateRoot) {
        this.txTrieRoot = stateRoot;
    }

    public byte[] getLogsBloom() {
        return logsBloom;
    }

    public byte[] getDifficulty() {
        return difficulty;
    }

    public BigInteger getDifficultyBI() {
        return new BigInteger(1, difficulty);
    }

    public void setDifficulty(byte[] difficulty) {
        this.difficulty = difficulty;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        this.number = number;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }

    public void setLogsBloom(byte[] logsBloom) {
        this.logsBloom = logsBloom;
    }

    public void setExtraData(byte[] extraData) {
        this.extraData = extraData;
    }

    public boolean isGenesis() {
        return this.number == 0;
    }

    public byte[] getPowBoundary() {
        return BigIntegers.asUnsignedByteArray(32, BigInteger.ONE.shiftLeft(256).divide(getDifficultyBI()));
    }

    public BigInteger getPowBoundaryBI() {
        return BigInteger.ONE.shiftLeft(256).divide(getDifficultyBI());
    }

    public byte getVersion() {
        return this.version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }
}
