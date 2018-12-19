/*
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
 */

package org.aion.zero.types;

import static org.aion.base.util.ByteUtil.longToBytes;
import static org.aion.base.util.ByteUtil.merge;
import static org.aion.base.util.ByteUtil.oneByteToHexString;
import static org.aion.base.util.ByteUtil.toHexString;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;

import java.math.BigInteger;
import java.util.Objects;
import org.aion.base.type.AionAddress;
import org.aion.base.type.IPowBlockHeader;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Utils;
import org.aion.crypto.HashUtil;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.exceptions.HeaderStructureException;
import org.json.JSONObject;

/**
 * aion zero block header class.
 *
 * @author Ross
 */
public class A0BlockHeader extends AbstractBlockHeader implements IPowBlockHeader {

    static final int RPL_BH_VERSION = 0,
            RPL_BH_NUMBER = 1,
            RPL_BH_PARENTHASH = 2,
            RPL_BH_COINBASE = 3,
            RPL_BH_STATEROOT = 4,
            RPL_BH_TXTRIE = 5,
            RPL_BH_RECEIPTTRIE = 6,
            RPL_BH_LOGSBLOOM = 7,
            RPL_BH_DIFFICULTY = 8,
            RPL_BH_EXTRADATA = 9,
            RPL_BH_NRG_CONSUMED = 10,
            RPL_BH_NRG_LIMIT = 11,
            RPL_BH_TIMESTAMP = 12,
            RPL_BH_NONCE = 13,
            RPL_BH_SOLUTION = 14;

    private byte[] mineHashBytes;

    // TODO: Update this
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.putOpt("version", oneByteToHexString(this.version));
        obj.putOpt("number", toHexString(longToBytes(this.number)));
        obj.putOpt("parentHash", toHexString(this.parentHash));
        obj.putOpt("coinBase", toHexString(this.coinbase.toBytes()));
        obj.putOpt("stateRoot", toHexString(this.stateRoot));
        obj.putOpt("txTrieRoot", toHexString(this.txTrieRoot));
        obj.putOpt("receiptTrieRoot", toHexString(this.receiptTrieRoot));
        obj.putOpt("logsBloom", toHexString(this.logsBloom));
        obj.putOpt("difficulty", toHexString(this.difficulty));
        obj.putOpt("extraData", toHexString(this.extraData));
        obj.putOpt("energyConsumed", toHexString(longToBytes(this.energyConsumed)));
        obj.putOpt("energyLimit", toHexString(longToBytes(this.energyLimit)));
        obj.putOpt("timestamp", toHexString(longToBytes(this.timestamp)));

        return obj;
    }

    public A0BlockHeader(byte[] encoded) {
        this((RLPList) RLP.decode2(encoded).get(0));
    }

    public A0BlockHeader(RLPList rlpHeader) {

        // Version
        byte[] versionBytes = rlpHeader.get(RPL_BH_VERSION).getRLPData();
        this.version = versionBytes.length == 1 ? versionBytes[0] : 1;

        // Number
        byte[] nrBytes = rlpHeader.get(RPL_BH_NUMBER).getRLPData();
        this.number = nrBytes == null ? 0 : (new BigInteger(1, nrBytes)).longValue();

        // ParentHash
        this.parentHash = rlpHeader.get(RPL_BH_PARENTHASH).getRLPData();

        // CoinBase
        byte[] data = rlpHeader.get(RPL_BH_COINBASE).getRLPData();
        this.coinbase =
                (data == null)
                        ? AionAddress.EMPTY_ADDRESS()
                        : AionAddress.wrap(rlpHeader.get(RPL_BH_COINBASE).getRLPData());

        // StateRoot
        this.stateRoot = rlpHeader.get(RPL_BH_STATEROOT).getRLPData();

        // TxTrieRoot
        this.txTrieRoot = rlpHeader.get(RPL_BH_TXTRIE).getRLPData();
        if (this.txTrieRoot == null) {
            this.txTrieRoot = EMPTY_TRIE_HASH;
        }

        // ReceiptTrieRoot
        this.receiptTrieRoot = rlpHeader.get(RPL_BH_RECEIPTTRIE).getRLPData();
        if (this.receiptTrieRoot == null) {
            this.receiptTrieRoot = EMPTY_TRIE_HASH;
        }

        // LogsBloom
        this.logsBloom = rlpHeader.get(RPL_BH_LOGSBLOOM).getRLPData();

        // Difficulty
        this.difficulty = rlpHeader.get(RPL_BH_DIFFICULTY).getRLPData();

        // ExtraData
        this.extraData = rlpHeader.get(RPL_BH_EXTRADATA).getRLPData();

        // Energy Consumed
        byte[] energyConsumedBytes = rlpHeader.get(RPL_BH_NRG_CONSUMED).getRLPData();
        this.energyConsumed =
                energyConsumedBytes == null
                        ? 0
                        : (new BigInteger(1, energyConsumedBytes).longValue());

        // Energy Limit
        byte[] energyLimitBytes = rlpHeader.get(RPL_BH_NRG_LIMIT).getRLPData();
        this.energyLimit =
                energyLimitBytes == null ? 0 : (new BigInteger(1, energyLimitBytes).longValue());

        // Timestamp
        byte[] tsBytes = rlpHeader.get(RPL_BH_TIMESTAMP).getRLPData();

        // TODO: not a huge concern, but how should we handle possible
        // overflows?
        this.timestamp = tsBytes == null ? 0 : (new BigInteger(1, tsBytes)).longValue();

        // Nonce
        this.nonce = rlpHeader.get(RPL_BH_NONCE).getRLPData();

        // Solution
        this.solution = rlpHeader.get(RPL_BH_SOLUTION).getRLPData();
    }

    /**
     * Copy constructor
     *
     * @param toCopy Block header to copy
     */
    public A0BlockHeader(A0BlockHeader toCopy) {

        // Copy version
        this.version = toCopy.getVersion();

        // Copy block number
        this.number = toCopy.getNumber();

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
        System.arraycopy(
                toCopy.getReceiptsRoot(), 0, this.receiptTrieRoot, 0, this.receiptTrieRoot.length);

        // Copy logs bloom
        this.logsBloom = new byte[toCopy.getLogsBloom().length];
        System.arraycopy(toCopy.getLogsBloom(), 0, this.logsBloom, 0, this.logsBloom.length);

        // Copy difficulty
        this.difficulty = new byte[toCopy.getDifficulty().length];
        System.arraycopy(toCopy.getDifficulty(), 0, this.difficulty, 0, this.difficulty.length);

        // Copy extra data
        this.extraData = new byte[toCopy.getExtraData().length];
        System.arraycopy(toCopy.getExtraData(), 0, this.extraData, 0, this.extraData.length);

        // Copy energyConsumed
        this.energyConsumed = toCopy.getEnergyConsumed();

        // Copy energyLimit
        this.energyLimit = toCopy.getEnergyLimit();

        // Copy timestamp
        this.timestamp = toCopy.getTimestamp();

        // Copy nonce
        this.nonce = new byte[toCopy.getNonce().length];
        System.arraycopy(toCopy.getNonce(), 0, this.nonce, 0, this.nonce.length);

        // Copy solution
        this.solution = new byte[toCopy.getSolution().length];
        System.arraycopy(toCopy.getSolution(), 0, this.solution, 0, this.solution.length);
    }

    public A0BlockHeader(
            byte version,
            long number,
            byte[] parentHash,
            Address coinbase,
            byte[] logsBloom,
            byte[] difficulty,
            byte[] extraData,
            long energyConsumed,
            long energyLimit,
            long timestamp,
            byte[] nonce,
            byte[] solution) {
        this.version = version;
        this.coinbase = (AionAddress) coinbase;
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

    public byte[] getEncoded() {
        return this.getEncoded(true); // with nonce
    }

    public byte[] getEncodedWithoutNonce() {
        return this.getEncoded(false);
    }

    public byte[] getEncoded(boolean withNonce) {

        byte[] versionBytes = {this.version};

        byte[] RLPversion = RLP.encodeElement(versionBytes);
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
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
        byte[] extraData = RLP.encodeElement(this.extraData);
        byte[] energyConsumed = RLP.encodeBigInteger(BigInteger.valueOf(this.energyConsumed));
        byte[] energyLimit = RLP.encodeBigInteger(BigInteger.valueOf(this.energyLimit));

        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));

        byte[] solution = RLP.encodeElement(this.solution);

        if (withNonce) {
            byte[] nonce = RLP.encodeElement(this.nonce);
            return RLP.encodeList(
                    RLPversion,
                    number,
                    parentHash,
                    coinbase,
                    stateRoot,
                    txTrieRoot,
                    receiptTrieRoot,
                    logsBloom,
                    difficulty,
                    extraData,
                    energyConsumed,
                    energyLimit,
                    timestamp,
                    nonce,
                    solution);
        } else {
            return RLP.encodeList(
                    RLPversion,
                    parentHash,
                    coinbase,
                    stateRoot,
                    txTrieRoot,
                    receiptTrieRoot,
                    logsBloom,
                    difficulty,
                    number,
                    timestamp,
                    extraData,
                    solution,
                    energyConsumed,
                    energyLimit);
        }
    }

    public String toString() {
        return toStringWithSuffix("\n");
    }

    private String toStringWithSuffix(final String suffix) {
        StringBuilder toStringBuff = new StringBuilder();
        toStringBuff
                .append("  hash=")
                .append(toHexString(getHash()))
                .append("  Length: ")
                .append(getHash().length)
                .append(suffix);
        toStringBuff
                .append("  version=")
                .append(Integer.toHexString(version))
                .append("  Length: ")
                .append(suffix);
        toStringBuff.append("  number=").append(number).append(suffix);
        toStringBuff
                .append("  parentHash=")
                .append(toHexString(parentHash))
                .append("  parentHash: ")
                .append(parentHash.length)
                .append(suffix);
        toStringBuff
                .append("  coinbase=")
                .append(coinbase.toString())
                .append("  coinBase: ")
                .append(coinbase.toBytes().length)
                .append(suffix);
        toStringBuff
                .append("  stateRoot=")
                .append(toHexString(stateRoot))
                .append("  stateRoot: ")
                .append(stateRoot.length)
                .append(suffix);
        toStringBuff
                .append("  txTrieHash=")
                .append(toHexString(txTrieRoot))
                .append("  txTrieRoot: ")
                .append(txTrieRoot.length)
                .append(suffix);
        toStringBuff
                .append("  receiptsTrieHash=")
                .append(toHexString(receiptTrieRoot))
                .append("  receiptTrieRoot: ")
                .append(receiptTrieRoot.length)
                .append(suffix);
        toStringBuff
                .append("  difficulty=")
                .append(toHexString(difficulty))
                .append("  difficulty: ")
                .append(difficulty.length)
                .append(suffix);
        toStringBuff.append("  energyConsumed=").append(energyConsumed).append(suffix);
        toStringBuff.append("  energyLimit=").append(energyLimit).append(suffix);
        toStringBuff.append("  extraData=").append(toHexString(extraData)).append(suffix);
        toStringBuff
                .append("  timestamp=")
                .append(timestamp)
                .append(" (")
                .append(Utils.longToDateTime(timestamp))
                .append(")")
                .append(suffix);
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
     * Set the energyConsumed field in header, this is used during block creation
     *
     * @param energyConsumed total energyConsumed during execution of transactions
     */
    public void setEnergyConsumed(long energyConsumed) {
        this.energyConsumed = energyConsumed;
    }

    /**
     * Return unencoded bytes of the header
     *
     * @param toMine Return header bytes excluding nonce and solution if true; else the entire block
     *     header
     * @return byte array containing raw header bytes
     */
    public byte[] getHeaderBytes(boolean toMine) {
        byte[] hdrBytes;
        if (toMine) {
            hdrBytes =
                    merge(
                            new byte[] {this.version},
                            longToBytes(this.number),
                            this.parentHash,
                            this.coinbase.toBytes(),
                            this.stateRoot,
                            this.txTrieRoot,
                            this.receiptTrieRoot,
                            this.logsBloom,
                            this.difficulty,
                            this.extraData,
                            longToBytes(this.energyConsumed),
                            longToBytes(this.energyLimit),
                            longToBytes(this.timestamp));
        } else {
            hdrBytes =
                    merge(
                            new byte[] {this.version},
                            longToBytes(this.number),
                            this.parentHash,
                            this.coinbase.toBytes(),
                            this.stateRoot,
                            this.txTrieRoot,
                            this.receiptTrieRoot,
                            this.logsBloom,
                            this.difficulty,
                            this.extraData,
                            longToBytes(this.energyConsumed),
                            longToBytes(this.energyLimit),
                            longToBytes(this.timestamp),
                            this.nonce,
                            this.solution);
        }
        return hdrBytes;
    }

    /**
     * Get hash of the header bytes to mine a block
     *
     * @return Blake2b digest (32 bytes) of the raw header bytes.
     */
    public byte[] getMineHash() {
        if (this.mineHashBytes == null) {
            this.mineHashBytes = HashUtil.h256(getHeaderBytes(true));
        }
        return mineHashBytes;
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

        // Version
        byte[] version = rlpHeader.get(RPL_BH_VERSION).getRLPData();
        if (version != null && version.length == 1) builder.withVersion(version[0]);

        // Number
        byte[] nrBytes = rlpHeader.get(RPL_BH_NUMBER).getRLPData();
        if (nrBytes != null) {
            builder.withNumber(nrBytes);
        }

        // Parent Hash
        builder.withParentHash(rlpHeader.get(RPL_BH_PARENTHASH).getRLPData());

        // Coinbase (miner)
        builder.withCoinbase(new AionAddress(rlpHeader.get(RPL_BH_COINBASE).getRLPData()));

        // State root
        builder.withStateRoot(rlpHeader.get(RPL_BH_STATEROOT).getRLPData());

        // TxTrie root
        byte[] txTrieRoot = rlpHeader.get(RPL_BH_TXTRIE).getRLPData();
        if (txTrieRoot != null) {
            builder.withTxTrieRoot(txTrieRoot);
        }

        // Receipt Trie root
        byte[] receiptTrieRoot = rlpHeader.get(RPL_BH_RECEIPTTRIE).getRLPData();
        if (receiptTrieRoot != null) {
            builder.withReceiptTrieRoot(receiptTrieRoot);
        }

        // LogsBloom
        builder.withLogsBloom(rlpHeader.get(RPL_BH_LOGSBLOOM).getRLPData());

        // Difficulty
        builder.withDifficulty(rlpHeader.get(RPL_BH_DIFFICULTY).getRLPData());

        // ExtraData
        builder.withExtraData(rlpHeader.get(RPL_BH_EXTRADATA).getRLPData());

        // Energy Consumed
        byte[] energyConsumedBytes = rlpHeader.get(RPL_BH_NRG_CONSUMED).getRLPData();
        if (energyConsumedBytes != null) {
            builder.withEnergyConsumed(energyConsumedBytes);
        }

        // Energy limit
        byte[] energyLimitBytes = rlpHeader.get(RPL_BH_NRG_LIMIT).getRLPData();
        if (energyLimitBytes != null) {
            builder.withEnergyLimit(energyLimitBytes);
        }

        // Timestamp
        byte[] tsBytes = rlpHeader.get(RPL_BH_TIMESTAMP).getRLPData();
        if (tsBytes != null) {
            builder.withTimestamp(tsBytes);
        }

        // Nonce
        builder.withNonce(rlpHeader.get(RPL_BH_NONCE).getRLPData());

        // Solution
        builder.withSolution(rlpHeader.get(RPL_BH_SOLUTION).getRLPData());

        return builder.build();
    }

    /** Builder used to introduce blocks into system that come from unsafe sources */
    public static class Builder {

        /*
         * Some constants for fallbacks, these are not rigorously defined this;
         * TODO: define these with explanations in the future
         */
        protected Address EMPTY_ADDRESS = AionAddress.EMPTY_ADDRESS();

        protected byte version;
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

        public Builder withVersion(byte version) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (version < 1) {
                    throw new HeaderStructureException(
                            "version", RPL_BH_VERSION, "must be greater than 0");
                }
            }

            this.version = version;
            return this;
        }

        public Builder withParentHash(byte[] parentHash) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (parentHash == null)
                    throw new HeaderStructureException(
                            "parentHash", RPL_BH_PARENTHASH, "cannot be null");

                if (parentHash.length != 32)
                    throw new HeaderStructureException(
                            "parentHash", RPL_BH_PARENTHASH, "must be of length 32");
            }

            this.parentHash = parentHash;
            return this;
        }

        public Builder withCoinbase(Address coinbase) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (coinbase == null)
                    throw new HeaderStructureException(
                            "coinbase", RPL_BH_COINBASE, "cannot be null");
            }

            this.coinbase = coinbase;
            return this;
        }

        public Builder withStateRoot(byte[] stateRoot) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (stateRoot == null)
                    throw new HeaderStructureException(
                            "stateRoot", RPL_BH_STATEROOT, "cannot be null");

                if (stateRoot.length != 32)
                    throw new HeaderStructureException(
                            "stateRoot", RPL_BH_STATEROOT, "must be of length 32");
            }

            this.stateRoot = stateRoot;
            return this;
        }

        public Builder withTxTrieRoot(byte[] txTrieRoot) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (txTrieRoot == null)
                    throw new HeaderStructureException(
                            "txTrieRoot", RPL_BH_TXTRIE, "cannot be null");

                if (txTrieRoot.length != 32)
                    throw new HeaderStructureException(
                            "txTrieRoot", RPL_BH_TXTRIE, "must be of length 32");
            }

            this.txTrieRoot = txTrieRoot;
            return this;
        }

        public Builder withReceiptTrieRoot(byte[] receiptTrieRoot) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (receiptTrieRoot == null)
                    throw new HeaderStructureException(
                            "receiptTrieRoot", RPL_BH_RECEIPTTRIE, "cannot be null");

                if (receiptTrieRoot.length != 32)
                    throw new HeaderStructureException(
                            "receiptTrieRoot", RPL_BH_RECEIPTTRIE, "must be of length 32");
            }

            this.receiptTrieRoot = receiptTrieRoot;
            return this;
        }

        public Builder withLogsBloom(byte[] logsBloom) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (logsBloom == null)
                    throw new HeaderStructureException(
                            "logsBloom", RPL_BH_LOGSBLOOM, "cannot be null");

                if (logsBloom.length != 256)
                    throw new HeaderStructureException(
                            "logsBloom", RPL_BH_LOGSBLOOM, "logsBloom must be of length 256");
            }

            this.logsBloom = logsBloom;
            return this;
        }

        public Builder withDifficulty(byte[] difficulty) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(difficulty);
                if (difficulty.length > 16)
                    throw new HeaderStructureException(
                            "difficulty", RPL_BH_DIFFICULTY, "cannot be greater than 16 bytes");
            }
            this.difficulty = difficulty;
            return this;
        }

        public Builder withDifficulty(BigInteger difficulty) throws HeaderStructureException {
            return withDifficulty(ByteUtil.bigIntegerToBytes(difficulty));
        }

        public Builder withTimestamp(long timestamp) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (timestamp < 0)
                    throw new HeaderStructureException(
                            "timestamp", RPL_BH_TIMESTAMP, "must be positive value");
            }

            this.timestamp = timestamp;
            return this;
        }

        public Builder withTimestamp(byte[] timestamp) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(timestamp);
                if (timestamp.length > 8)
                    throw new HeaderStructureException(
                            "timestamp", RPL_BH_TIMESTAMP, "cannot be greater than 8 bytes");
            }
            return withTimestamp(ByteUtil.byteArrayToLong(timestamp));
        }

        public Builder withNumber(long number) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (number < 0) {
                    throw new HeaderStructureException("number", RPL_BH_NUMBER, "must be positive");
                }
            }

            this.number = number;
            return this;
        }

        public Builder withNumber(byte[] number) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (number == null)
                    throw new HeaderStructureException("number", RPL_BH_NUMBER, "cannot be null");
            }
            return withNumber(ByteUtil.byteArrayToLong(number));
        }

        public Builder withExtraData(byte[] extraData) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (extraData == null)
                    throw new HeaderStructureException(
                            "extraData", RPL_BH_EXTRADATA, "cannot be null");

                if (extraData.length > 32) {
                    throw new HeaderStructureException(
                            "extraData", RPL_BH_EXTRADATA, "cannot be greater than 32 bytes");
                }
            }

            this.extraData = extraData;
            return this;
        }

        public Builder withEnergyConsumed(long energyConsumed) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (energyConsumed < 0) {
                    throw new HeaderStructureException(
                            "energyConsumed", RPL_BH_NRG_CONSUMED, "must be positive value");
                }
            }

            this.energyConsumed = energyConsumed;
            return this;
        }

        public Builder withEnergyConsumed(byte[] energyConsumed) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (energyConsumed == null)
                    throw new HeaderStructureException(
                            "energyConsumed", RPL_BH_NRG_CONSUMED, "cannot be null");

                if (energyConsumed.length > 8)
                    throw new HeaderStructureException(
                            "energyConsumed",
                            RPL_BH_NRG_CONSUMED,
                            "cannot be greater than 8 bytes");
            }

            return withEnergyConsumed(ByteUtil.byteArrayToLong(energyConsumed));
        }

        public Builder withEnergyLimit(long energyLimit) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (energyLimit < 0) {
                    throw new HeaderStructureException(
                            "energyLimitException",
                            RPL_BH_NRG_LIMIT,
                            "energyLimit must be positive value");
                }
            }

            this.energyLimit = energyLimit;
            return this;
        }

        public Builder withEnergyLimit(byte[] energyLimit) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (energyLimit == null)
                    throw new HeaderStructureException(
                            "energyLimit", RPL_BH_NRG_LIMIT, "cannot be null");

                if (energyLimit.length > 8)
                    throw new HeaderStructureException(
                            "energyLimit",
                            RPL_BH_NRG_LIMIT,
                            "energyLimit cannot be greater than 8 bytes");
            }
            return withEnergyLimit(ByteUtil.byteArrayToLong(energyLimit));
        }

        /*
         * TODO: solution size may change with updates
         */
        public Builder withSolution(byte[] solution) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (solution == null)
                    throw new HeaderStructureException(
                            "solution", RPL_BH_SOLUTION, "cannot be null");

                if (solution.length != 1408) {
                    throw new HeaderStructureException(
                            "solution", RPL_BH_SOLUTION, "invalid solution length");
                }
            }
            this.solution = solution;
            return this;
        }

        public Builder withNonce(byte[] nonce) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (nonce == null)
                    throw new HeaderStructureException("nonce", RPL_BH_NONCE, "cannot be null");

                if (nonce.length > 32) {
                    throw new HeaderStructureException(
                            "nonce", RPL_BH_NONCE, "cannot be greater than 32 bytes");
                }
            }

            this.nonce = nonce;
            return this;
        }

        public A0BlockHeader build() {

            this.version = this.version == 0 ? 1 : this.version;
            this.parentHash = this.parentHash == null ? HashUtil.EMPTY_DATA_HASH : this.parentHash;
            this.coinbase = this.coinbase == null ? AionAddress.ZERO_ADDRESS() : this.coinbase;
            this.stateRoot = this.stateRoot == null ? HashUtil.EMPTY_TRIE_HASH : this.stateRoot;
            this.txTrieRoot = this.txTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : this.txTrieRoot;
            this.receiptTrieRoot =
                    this.receiptTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : this.receiptTrieRoot;
            this.logsBloom = this.logsBloom == null ? EMPTY_BLOOM : this.logsBloom;
            this.difficulty = this.difficulty == null ? ByteUtil.EMPTY_HALFWORD : this.difficulty;
            this.extraData = this.extraData == null ? ByteUtil.EMPTY_WORD : this.extraData;
            this.nonce = this.nonce == null ? ByteUtil.EMPTY_WORD : this.nonce;
            this.solution = this.solution == null ? EMPTY_SOLUTION : this.solution;

            A0BlockHeader header =
                    new A0BlockHeader(
                            this.version,
                            this.number,
                            this.parentHash,
                            this.coinbase,
                            this.logsBloom,
                            this.difficulty,
                            this.extraData,
                            this.energyConsumed,
                            this.energyLimit,
                            this.timestamp,
                            this.nonce,
                            this.solution);
            header.setReceiptsRoot(this.receiptTrieRoot);
            header.setStateRoot(this.stateRoot);
            header.txTrieRoot = this.txTrieRoot;
            return header;
        }
    }
}
