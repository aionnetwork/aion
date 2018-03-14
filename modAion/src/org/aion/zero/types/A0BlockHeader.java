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
 *     
 ******************************************************************************/

package org.aion.zero.types;

import static org.aion.base.util.ByteUtil.longToBytes;
import static org.aion.base.util.ByteUtil.merge;
import static org.aion.base.util.ByteUtil.toHexString;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import org.aion.base.type.Address;
import org.aion.base.type.IPowBlockHeader;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Utils;
import org.aion.crypto.HashUtil;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.mcf.types.AbstractBlockHeader;
import org.json.JSONObject;

/**
 * aion zero block header class.
 * 
 * @author jay
 */
public class A0BlockHeader extends AbstractBlockHeader implements IPowBlockHeader {

    static final int RPL_BH_PARENTHASH = 0, RPL_BH_COINBASE = 1, RPL_BH_STATEROOT = 2, RPL_BH_TXTRIE = 3,
            RPL_BH_RECEIPTTRIE = 4, RPL_BH_LOGSBLOOM = 5, RPL_BH_DIFFICULTY = 6, RPL_BH_NUMBER = 7,
            RPL_BH_TIMESTAMP = 8, RPL_BH_EXTRADATA = 9, RPL_BH_NONCE = 10, RPL_BH_SOLUTION = 11,
            RPL_BH_NRG_CONSUMED = 12, RPL_BH_NRG_LIMIT = 13;

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.putOpt("parentHash", toHexString(this.parentHash));
        obj.putOpt("coinBase", toHexString(this.coinbase.toBytes()));
        obj.putOpt("stateRoot", toHexString(this.stateRoot));
        obj.putOpt("txTrieRoot", toHexString(this.txTrieRoot));
        obj.putOpt("receiptTrieRoot", toHexString(this.receiptTrieRoot));
        obj.putOpt("logsBloom", toHexString(this.logsBloom));
        obj.putOpt("difficulty", toHexString(this.difficulty));
        obj.putOpt("timestamp", toHexString(longToBytes(this.timestamp)));
        obj.putOpt("number", toHexString(longToBytes(this.number)));
        obj.putOpt("extraData", toHexString(this.extraData));
        obj.putOpt("energyConsumed", toHexString(longToBytes(this.energyConsumed)));
        obj.putOpt("energyLimit", toHexString(longToBytes(this.energyLimit)));

        return obj;
    }

    /**
     * The total amount of energy consumed by the block, this value cannot
     * exceed the available energy (energyLimit)
     */
    public long energyConsumed;

    /**
     * The total available energy available for the block.
     */
    public long energyLimit;

    public byte[] solution;

    public A0BlockHeader(byte[] encoded) {
        this((RLPList) RLP.decode2(encoded).get(0));
    }

    public A0BlockHeader(RLPList rlpHeader) {

        this.parentHash = rlpHeader.get(RPL_BH_PARENTHASH).getRLPData();

        byte[] data = rlpHeader.get(RPL_BH_COINBASE).getRLPData();
        this.coinbase = (data == null) ? Address.EMPTY_ADDRESS()
                : Address.wrap(rlpHeader.get(RPL_BH_COINBASE).getRLPData());

        this.stateRoot = rlpHeader.get(RPL_BH_STATEROOT).getRLPData();

        this.txTrieRoot = rlpHeader.get(RPL_BH_TXTRIE).getRLPData();
        if (this.txTrieRoot == null) {
            this.txTrieRoot = EMPTY_TRIE_HASH;
        }

        this.receiptTrieRoot = rlpHeader.get(RPL_BH_RECEIPTTRIE).getRLPData();
        if (this.receiptTrieRoot == null) {
            this.receiptTrieRoot = EMPTY_TRIE_HASH;
        }

        this.logsBloom = rlpHeader.get(RPL_BH_LOGSBLOOM).getRLPData();
        this.difficulty = rlpHeader.get(RPL_BH_DIFFICULTY).getRLPData();

        byte[] nrBytes = rlpHeader.get(RPL_BH_NUMBER).getRLPData();
        byte[] tsBytes = rlpHeader.get(RPL_BH_TIMESTAMP).getRLPData();

        // TODO: not a huge concern, but how should we handle possible
        // overflows?
        this.number = nrBytes == null ? 0 : (new BigInteger(1, nrBytes)).longValue();
        this.timestamp = tsBytes == null ? 0 : (new BigInteger(1, tsBytes)).longValue();

        this.extraData = rlpHeader.get(RPL_BH_EXTRADATA).getRLPData();
        this.nonce = rlpHeader.get(RPL_BH_NONCE).getRLPData();
        this.solution = rlpHeader.get(RPL_BH_SOLUTION).getRLPData();

        byte[] energyConsumedBytes = rlpHeader.get(RPL_BH_NRG_CONSUMED).getRLPData();
        byte[] energyLimitBytes = rlpHeader.get(RPL_BH_NRG_LIMIT).getRLPData();

        this.energyConsumed = energyConsumedBytes == null ? 0 : (new BigInteger(1, energyConsumedBytes).longValue());
        this.energyLimit = energyLimitBytes == null ? 0 : (new BigInteger(1, energyLimitBytes).longValue());
    }

    /**
     * Copy constructor
     *
     * @param toCopy
     *            Block header to copy
     */
    public A0BlockHeader(A0BlockHeader toCopy) {

        // Copy elements in parentHash
        this.parentHash = new byte[toCopy.getParentHash().length];
        System.arraycopy(toCopy.getParentHash(), 0, this.parentHash, 0, this.parentHash.length);

        // Copy elements in coinbase
        this.coinbase = toCopy.coinbase.clone();

        // Copy stateroot
        this.stateRoot = new byte[toCopy.getStateRoot().length];
        System.arraycopy(toCopy.getStateRoot(), 0, this.stateRoot, 0, this.stateRoot.length);

        // Copy txTrieRoot
        this.txTrieRoot = new byte[toCopy.getTxTrieRoot().length];
        System.arraycopy(toCopy.getTxTrieRoot(), 0, this.txTrieRoot, 0, this.txTrieRoot.length);

        // Copy receiptTreeRoot
        this.receiptTrieRoot = new byte[toCopy.getReceiptsRoot().length];
        System.arraycopy(toCopy.getReceiptsRoot(), 0, this.receiptTrieRoot, 0, this.receiptTrieRoot.length);

        // Copy logs bloom
        this.logsBloom = new byte[toCopy.getLogsBloom().length];
        System.arraycopy(toCopy.getLogsBloom(), 0, this.logsBloom, 0, this.logsBloom.length);

        // Copy difficulty
        this.difficulty = new byte[toCopy.getDifficulty().length];
        System.arraycopy(toCopy.getDifficulty(), 0, this.difficulty, 0, this.difficulty.length);

        // Copy block number
        this.number = toCopy.getNumber();

        // Copy timestamp
        this.timestamp = toCopy.getTimestamp();

        // Copy extra data
        this.extraData = new byte[toCopy.getExtraData().length];
        System.arraycopy(toCopy.getExtraData(), 0, this.extraData, 0, this.extraData.length);

        // Copy nonce
        this.nonce = new byte[toCopy.getNonce().length];
        System.arraycopy(toCopy.getNonce(), 0, this.nonce, 0, this.nonce.length);

        // Copy solution
        this.solution = new byte[toCopy.getSolution().length];
        System.arraycopy(toCopy.getSolution(), 0, this.solution, 0, this.solution.length);

        // Copy energyConsumed
        this.energyConsumed = toCopy.getEnergyConsumed();

        // Copy energyLimit
        this.energyLimit = toCopy.getEnergyLimit();
    }

    protected A0BlockHeader(byte[] parentHash, Address coinbase, byte[] logsBloom, byte[] difficulty, long number,
            long timestamp, byte[] extraData, byte[] nonce, byte[] solution, long energyConsumed, long energyLimit) {
        this.coinbase = coinbase;
        this.parentHash = parentHash;
        this.logsBloom = logsBloom;
        this.difficulty = difficulty;
        this.number = number;
        this.timestamp = timestamp;
        this.extraData = extraData;
        this.nonce = nonce;

        // New fields required for Equihash
        this.solution = solution;

        // Fields required for energy based VM
        this.energyConsumed = energyConsumed;
        this.energyLimit = energyLimit;
    }

    public byte[] getHash() {
        return HashUtil.h256(getEncoded());
    }

    /*
     * Get hash of the static portion of the header; used to identify block templates submitted by the pool.
     * Also to be used in BHv2 implementation
     */
    public byte[] getStaticHash() {
        //Buffer size = header size (496) - timestamp (8)
        ByteBuffer b = ByteBuffer.allocate(488);

        b.put(this.parentHash, 0, this.parentHash.length);
        b.put(this.coinbase.toBytes(), 0, 32);
        b.put(this.stateRoot, 0, this.stateRoot.length);
        b.put(this.txTrieRoot, 0, this.txTrieRoot.length);
        b.put(this.receiptTrieRoot, 0, this.receiptTrieRoot.length);
        b.put(this.logsBloom, 0, this.logsBloom.length);
        b.put(this.difficulty,0,this.difficulty.length);
        b.putLong(this.number);
        b.put(this.extraData, 0, this.extraData.length);
        b.putLong(this.energyConsumed);
        b.putLong(this.energyLimit);

        return HashUtil.h256(b.array());
    }

    public byte[] getEncoded() {
        return this.getEncoded(true); // with nonce
    }

    public byte[] getEncodedWithoutNonce() {
        return this.getEncoded(false);
    }

    public byte[] getEncoded(boolean withNonce) {

        byte[] parentHash = RLP.encodeElement(this.parentHash);
        byte[] coinbase = RLP.encodeElement(this.coinbase.toBytes());
        byte[] stateRoot = RLP.encodeElement(this.stateRoot);

        if (txTrieRoot == null) {
            this.txTrieRoot = EMPTY_TRIE_HASH;
        }
        byte[] txTrieRoot = RLP.encodeElement(this.txTrieRoot);

        if (receiptTrieRoot == null) {
            this.receiptTrieRoot = EMPTY_TRIE_HASH;
        }
        byte[] receiptTrieRoot = RLP.encodeElement(this.receiptTrieRoot);
        byte[] logsBloom = RLP.encodeElement(this.logsBloom);
        byte[] difficulty = RLP.encodeElement(this.difficulty);
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));

        byte[] extraData = RLP.encodeElement(this.extraData);
        byte[] solution = RLP.encodeElement(this.solution);

        byte[] energyConsumed = RLP.encodeBigInteger(BigInteger.valueOf(this.energyConsumed));
        byte[] energyLimit = RLP.encodeBigInteger(BigInteger.valueOf(this.energyLimit));

        if (withNonce) {
            byte[] nonce = RLP.encodeElement(this.nonce);
            return RLP.encodeList(parentHash, coinbase, stateRoot, txTrieRoot, receiptTrieRoot, logsBloom, difficulty,
                    number, timestamp, extraData, nonce, solution, energyConsumed, energyLimit);
        } else {
            return RLP.encodeList(parentHash, coinbase, stateRoot, txTrieRoot, receiptTrieRoot, logsBloom, difficulty,
                    number, timestamp, extraData, solution, energyConsumed, energyLimit);
        }
    }

    public String toString() {
        return toStringWithSuffix("\n");
    }

    private String toStringWithSuffix(final String suffix) {
        StringBuilder toStringBuff = new StringBuilder();
        toStringBuff.append("  hash=").append(toHexString(getHash())).append("  Length: ").append(getHash().length)
                .append(suffix);
        toStringBuff.append("  parentHash=").append(toHexString(parentHash)).append("  parentHash: ")
                .append(parentHash.length).append(suffix);
        toStringBuff.append("  coinbase=").append(coinbase.toString()).append("  coinBase: ")
                .append(coinbase.toBytes().length).append(suffix);
        toStringBuff.append("  stateRoot=").append(toHexString(stateRoot)).append("  stateRoot: ")
                .append(stateRoot.length).append(suffix);
        toStringBuff.append("  txTrieHash=").append(toHexString(txTrieRoot)).append("  txTrieRoot: ")
                .append(txTrieRoot.length).append(suffix);
        toStringBuff.append("  receiptsTrieHash=").append(toHexString(receiptTrieRoot)).append("  receiptTrieRoot: ")
                .append(receiptTrieRoot.length).append(suffix);
        toStringBuff.append("  difficulty=").append(toHexString(difficulty)).append("  difficulty: ")
                .append(difficulty.length).append(suffix);
        toStringBuff.append("  number=").append(number).append(suffix);
        toStringBuff.append("  timestamp=").append(timestamp).append(" (").append(Utils.longToDateTime(timestamp))
                .append(")").append(suffix);
        toStringBuff.append("  energyConsumed=").append(energyConsumed).append(suffix);
        toStringBuff.append("  energyLimit=").append(energyLimit).append(suffix);
        toStringBuff.append("  extraData=").append(toHexString(extraData)).append(suffix);
        toStringBuff.append("  nonce=").append(toHexString(nonce)).append(suffix);
        toStringBuff.append("  solution=").append(toHexString(solution)).append(suffix);
        return toStringBuff.toString();
    }

    public String toFlatString() {
        return toStringWithSuffix("");
    }

    public byte[] getSolution() {
        return this.solution;
    }

    public void setSolution(byte[] _sl) {
        this.solution = _sl;
    }

    public long getEnergyConsumed() {
        return this.energyConsumed;
    }

    public long getEnergyLimit() {
        return this.energyLimit;
    }

    /**
     * Set the energyConsumed field in header, this is used during block
     * creation
     * {@link org.aion.zero.impl.AionBlockchainImpl#createNewBlock(AionBlock, List)}
     * to append post-execution state parameters (of which energyConsumed is a
     * part of)
     *
     * @param energyConsumed
     *            total energyConsumed during execution of transactions
     */
    public void setEnergyConsumed(long energyConsumed) {
        this.energyConsumed = energyConsumed;
    }

    /**
     * Return unencoded bytes of the header
     *
     * @param mine
     *            If true returns an a byte array excluding the mixHash, nonce
     *            and solutions, else includes all 3.
     * @return
     */
    public byte[] getHeaderBytes(boolean mine) {

        if (mine) {
            return merge(this.parentHash, this.coinbase.toBytes(), this.stateRoot, this.txTrieRoot,
                    this.receiptTrieRoot, this.logsBloom, this.difficulty, longToBytes(this.timestamp),
                    longToBytes(this.number), this.extraData, longToBytes(this.energyConsumed),
                    longToBytes(this.energyLimit));
        } else {
            return merge(this.parentHash, this.coinbase.toBytes(), this.stateRoot, this.txTrieRoot,
                    this.receiptTrieRoot, this.logsBloom, this.difficulty, longToBytes(this.timestamp),
                    longToBytes(this.number), this.extraData, this.nonce, this.solution,
                    longToBytes(this.energyConsumed), longToBytes(this.energyLimit));
        }
    }

    public static A0BlockHeader fromRLP(byte[] rawData, boolean isUnsafe) throws Exception {
        return fromRLP((RLPList) RLP.decode2(rawData).get(0), isUnsafe);
    }

    /**
     * Construct a block header from RLP
     *
     * @param rlpHeader
     * @param isUnsafe
     * @return
     */
    public static A0BlockHeader fromRLP(RLPList rlpHeader, boolean isUnsafe) throws Exception {
        Builder builder = new Builder();
        if (isUnsafe) {
            builder.fromUnsafeSource();
        }

        builder.withParentHash(rlpHeader.get(RPL_BH_PARENTHASH).getRLPData());
        builder.withCoinbase(new Address(rlpHeader.get(RPL_BH_COINBASE).getRLPData()));
        builder.withStateRoot(rlpHeader.get(RPL_BH_STATEROOT).getRLPData());

        byte[] txTrieRoot = rlpHeader.get(RPL_BH_TXTRIE).getRLPData();
        if (txTrieRoot != null) {
            builder.withTxTrieRoot(txTrieRoot);
        }

        byte[] receiptTrieRoot = rlpHeader.get(RPL_BH_RECEIPTTRIE).getRLPData();
        if (receiptTrieRoot != null) {
            builder.withReceiptTrieRoot(receiptTrieRoot);
        }

        builder.withLogsBloom(rlpHeader.get(RPL_BH_LOGSBLOOM).getRLPData());
        builder.withDifficulty(rlpHeader.get(RPL_BH_DIFFICULTY).getRLPData());

        byte[] nrBytes = rlpHeader.get(RPL_BH_NUMBER).getRLPData();
        byte[] tsBytes = rlpHeader.get(RPL_BH_TIMESTAMP).getRLPData();

        if (nrBytes != null) {
            builder.withNumber(nrBytes);
        }

        if (tsBytes != null) {
            builder.withTimestamp(tsBytes);
        }

        builder.withExtraData(rlpHeader.get(RPL_BH_EXTRADATA).getRLPData());
        builder.withNonce(rlpHeader.get(RPL_BH_NONCE).getRLPData());
        builder.withSolution(rlpHeader.get(RPL_BH_SOLUTION).getRLPData());

        byte[] energyConsumedBytes = rlpHeader.get(RPL_BH_NRG_CONSUMED).getRLPData();
        byte[] energyLimitBytes = rlpHeader.get(RPL_BH_NRG_LIMIT).getRLPData();

        if (energyConsumedBytes != null) {
            builder.withEnergyConsumed(energyConsumedBytes);
        }

        if (energyLimitBytes != null) {
            builder.withEnergyLimit(energyLimitBytes);
        }
        return builder.build();
    }

    /**
     * Builder used to introduce blocks into system that come from unsafe
     * sources, such as {@link org.aion.zero.impl.sync.msg.ResBlocksHeaders},
     * and from API
     * <p>
     * In the future we may switch to something like this, just so that we have
     * safe fallbacks (non-nulls) in the event of unknown fields.
     */
    public static class Builder {

        /*
         * Some constants for fallbacks, these are not rigorously defined this;
         * TODO: define these with explanations in the future
         */
        protected Address EMPTY_ADDRESS = Address.EMPTY_ADDRESS();
        protected byte[] EMPTY_BYTE_ARRAY = new byte[32];

        protected byte[] parentHash;
        protected Address coinbase;
        protected byte[] stateRoot;
        protected byte[] txTrieRoot;
        protected byte[] receiptTrieRoot;
        protected byte[] logsBloom;
        protected byte[] difficulty;
        protected long timestamp;
        protected long number;
        protected byte[] extraData;
        protected long energyConsumed;
        protected long energyLimit;
        protected byte[] solution;
        protected byte[] nonce;

        /*
         * Builder parameters, not related to header data structure
         */
        protected boolean isFromUnsafeSource = false;
        private static byte[] EMPTY_SOLUTION = new byte[1408];
        private static byte[] EMPTY_BLOOM = new byte[256];

        /**
         * Indicates that the data is from an unsafe source
         *
         * @return {@code builder} same instance of builder
         */
        public Builder fromUnsafeSource() {
            isFromUnsafeSource = true;
            return this;
        }

        public Builder withParentHash(byte[] parentHash) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(parentHash);
                if (parentHash.length != 32) {
                    throw new IllegalArgumentException("parentHash must be of length 32");
                }
            }

            this.parentHash = parentHash;
            return this;
        }

        public Builder withCoinbase(Address coinbase) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(coinbase);
            }

            this.coinbase = coinbase;
            return this;
        }

        public Builder withStateRoot(byte[] stateRoot) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(stateRoot);
                if (stateRoot.length != 32) {
                    throw new IllegalArgumentException("stateRoot must be of length 32");
                }
            }

            this.stateRoot = stateRoot;
            return this;
        }

        public Builder withTxTrieRoot(byte[] txTrieRoot) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(txTrieRoot);
                if (txTrieRoot.length != 32) {
                    throw new IllegalArgumentException("txTrieRoot must be of length 32");
                }
            }

            this.txTrieRoot = txTrieRoot;
            return this;
        }

        public Builder withReceiptTrieRoot(byte[] receiptTrieRoot) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(receiptTrieRoot);
                if (receiptTrieRoot.length != 32) {
                    throw new IllegalArgumentException("receiptTrieRoot must be of length 32");
                }
            }

            this.receiptTrieRoot = receiptTrieRoot;
            return this;
        }

        public Builder withLogsBloom(byte[] logsBloom) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(logsBloom);
                if (logsBloom.length != 256) {
                    throw new IllegalArgumentException("logsBloom must be of length 256");
                }
            }

            this.logsBloom = logsBloom;
            return this;
        }

        public Builder withDifficulty(byte[] difficulty) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(difficulty);
                if (difficulty.length > 32) {
                    throw new IllegalArgumentException("difficulty cannot be greater than 2 ** 256");
                }

                if (difficulty.length == 32 && difficulty[0] < 0x0) {
                    throw new IllegalArgumentException("difficulty cannot be greater than 2 ** 256");
                }

                if (difficulty.length == 32 && difficulty[0] == 1) {
                    for (int i = 1; i < difficulty.length; i++) {
                        if (difficulty[i] != 0x0) {
                            throw new IllegalArgumentException("difficulty cannot be greater than 2 ** 256");
                        }
                    }
                }
            }
            this.difficulty = difficulty;
            return this;
        }

        public Builder withDifficulty(BigInteger difficulty) {
            return withDifficulty(ByteUtil.bigIntegerToBytes(difficulty));
        }

        public Builder withTimestamp(long timestamp) {
            if (isFromUnsafeSource) {
                if (timestamp < 0) {
                    throw new IllegalArgumentException("timestamp must be positive value");
                }
            }

            this.timestamp = timestamp;
            return this;
        }

        public Builder withTimestamp(byte[] timestamp) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(timestamp);
            }
            return withTimestamp(ByteUtil.byteArrayToLong(timestamp));
        }

        public Builder withNumber(long number) {
            if (isFromUnsafeSource) {
                if (number < 0) {
                    throw new IllegalArgumentException("number must be positive value");
                }
            }

            this.number = number;
            return this;
        }

        public Builder withNumber(byte[] number) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(number);
            }
            return withNumber(ByteUtil.byteArrayToLong(number));
        }

        public Builder withExtraData(byte[] extraData) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(extraData);
                if (extraData.length > 32) {
                    throw new IllegalArgumentException("extraData is limited to 32 byte maximum");
                }
            }

            this.extraData = extraData;
            return this;
        }

        public Builder withEnergyConsumed(long energyConsumed) {
            if (isFromUnsafeSource) {
                if (energyConsumed < 0) {
                    throw new IllegalArgumentException("energyConsumed must be positive value");
                }
            }

            this.energyConsumed = energyConsumed;
            return this;
        }

        public Builder withEnergyConsumed(byte[] energyConsumed) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(energyConsumed);
            }

            return withEnergyConsumed(ByteUtil.byteArrayToLong(energyConsumed));
        }

        public Builder withEnergyLimit(long energyLimit) {
            if (isFromUnsafeSource) {
                if (energyLimit < 0) {
                    throw new IllegalArgumentException("energyLimit must be positive value");
                }
            }

            this.energyLimit = energyLimit;
            return this;
        }

        public Builder withEnergyLimit(byte[] energyLimit) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(energyLimit);
            }
            return withEnergyLimit(ByteUtil.byteArrayToLong(energyLimit));
        }

        /*
         * TODO: solution size may change with updates
         */
        public Builder withSolution(byte[] solution) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(solution);
                if (solution.length != 1408) {
                    throw new IllegalArgumentException("invalid solution size");
                }
            }

            this.solution = solution;
            return this;
        }

        public Builder withNonce(byte[] nonce) {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(nonce);
            }

            this.nonce = nonce;
            return this;
        }

        public A0BlockHeader build() {
            this.parentHash = this.parentHash == null ? HashUtil.EMPTY_DATA_HASH : this.parentHash;
            this.coinbase = this.coinbase == null ? Address.ZERO_ADDRESS() : this.coinbase; // the
                                                                                            // coinbase
                                                                                            // is
                                                                                            // fixed
                                                                                            // 32
                                                                                            // bytes
                                                                                            // array
                                                                                            // in
                                                                                            // the
                                                                                            // BlockHeader
            this.stateRoot = this.stateRoot == null ? HashUtil.EMPTY_TRIE_HASH : this.stateRoot;
            this.txTrieRoot = this.txTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : this.txTrieRoot;
            this.receiptTrieRoot = this.receiptTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : this.receiptTrieRoot;
            this.logsBloom = this.logsBloom == null ? EMPTY_BLOOM : this.logsBloom;
            this.difficulty = this.difficulty == null ? EMPTY_BYTE_ARRAY : this.difficulty;
            this.extraData = this.extraData == null ? EMPTY_BYTE_ARRAY : this.extraData;
            this.nonce = this.nonce == null ? EMPTY_BYTE_ARRAY : this.nonce;
            this.solution = this.solution == null ? EMPTY_SOLUTION : this.solution;

            A0BlockHeader header = new A0BlockHeader(this.parentHash, this.coinbase, this.logsBloom, this.difficulty,
                    this.number, this.timestamp, this.extraData, this.nonce, this.solution, this.energyConsumed,
                    this.energyLimit);
            header.setReceiptsRoot(this.receiptTrieRoot);
            header.setStateRoot(this.stateRoot);
            header.txTrieRoot = this.txTrieRoot;
            return header;
        }
    }
}
