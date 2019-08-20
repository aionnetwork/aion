package org.aion.zero.impl.types;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.util.bytes.ByteUtil.longToBytes;
import static org.aion.util.bytes.ByteUtil.oneByteToHexString;
import static org.aion.util.bytes.ByteUtil.toHexString;

import java.math.BigInteger;
import org.aion.crypto.HashUtil;
import org.aion.zero.impl.exceptions.HeaderStructureException;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.json.JSONObject;
import org.spongycastle.util.BigIntegers;

/**
 * aion zero block header class. Specific for the PoW block.
 *
 * @author Ross
 */
public class A0BlockHeader extends AbstractBlockHeader {

    private static final int NONCE_LENGTH = 32;
    private static final int SOLUTIONSIZE = 1408;

    // 0 ~ 12 has been defined in the AbstractClass
    private static final int RLP_BH_NONCE = 13, RLP_BH_SOLUTION = 14;

    /*
     * A 256-bit hash which proves that a sufficient amount of computation has
     * been carried out on this block
     */
    private byte[] nonce;

    /////////////////////////////////////////////////////////////////
    // (1344 in 200-9, 1408 in 210,9)
    private byte[] solution; // The equihash solution in compressed format

    // TODO: Update this
    @Override
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.putOpt("sealType", oneByteToHexString(this.sealType.getSealId()));
        obj.putOpt("number", toHexString(longToBytes(this.number)));
        obj.putOpt("parentHash", toHexString(this.parentHash));
        obj.putOpt("coinBase", toHexString(this.coinbase.toByteArray()));
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

        constructCommonHeader(rlpHeader);

        nonce = rlpHeader.get(RLP_BH_NONCE).getRLPData();

        solution = rlpHeader.get(RLP_BH_SOLUTION).getRLPData();
    }

    /**
     * Copy constructor
     *
     * @param toCopy Block header to copy
     */
    public A0BlockHeader(A0BlockHeader toCopy) {

        copyCommonHeader(toCopy);

        nonce = new byte[toCopy.getNonce().length];
        System.arraycopy(toCopy.getNonce(), 0, nonce, 0, nonce.length);

        solution = new byte[toCopy.getSolution().length];
        System.arraycopy(toCopy.getSolution(), 0, solution, 0, solution.length);
    }

    public A0BlockHeader(
        byte _sealType,
        long _number,
        byte[] _parentHash,
        AionAddress _coinbase,
        byte[] _logsBloom,
        byte[] _difficulty,
        byte[] _extraData,
        long _energyConsumed,
        long _energyLimit,
        long _timestamp,
        byte[] _nonce,
        byte[] _solution) {
        // TODO: [Unity] This can be set to Mined without being received as a param
        sealType = BlockSealType.byteToSealType(_sealType);
        if (_coinbase == null) {
            throw new IllegalArgumentException("Invalid coinbase!");
        } else {
            coinbase = _coinbase;
        }
        parentHash = _parentHash;
        logsBloom = _logsBloom;
        difficulty = _difficulty;
        number = _number;
        timestamp = _timestamp;
        extraData = _extraData;
        nonce = _nonce;

        // New fields required for Equihash
        solution = _solution;

        // Fields required for energy based VM
        energyConsumed = _energyConsumed;
        energyLimit = _energyLimit;
    }

    public A0BlockHeader(
        byte _sealType,
        long _number,
        byte[] _parentHash,
        AionAddress _coinbase,
        byte[] _stateRoot,
        byte[] _txTrieRoot,
        byte[] _receiptTrieRoot,
        byte[] _logsBloom,
        byte[] _difficulty,
        byte[] _extraData,
        long _energyConsumed,
        long _energyLimit,
        long _timestamp,
        byte[] _nonce,
        byte[] _solution) {

        super(
            _sealType,
            _number,
            _parentHash,
            _coinbase,
            _stateRoot,
            _txTrieRoot,
            _receiptTrieRoot,
            _logsBloom,
            _difficulty,
            _extraData,
            _energyConsumed,
            _energyLimit,
            _timestamp);

        // New fields required for Equihash
        solution = _solution;

        nonce = _nonce;
    }

    byte[] getEncodedWithoutNonce() {
        return getEncoded(false);
    }

    public byte[] getEncoded(boolean withNonce) {

        byte[] rlpSealType = RLP.encodeElement(new byte[] {sealType.getSealId()});
        byte[] rlpNumber = RLP.encodeBigInteger(BigInteger.valueOf(number));
        byte[] rlpParentHash = RLP.encodeElement(parentHash);
        byte[] rlpCoinbase = RLP.encodeElement(coinbase.toByteArray());
        byte[] rlpStateRoot = RLP.encodeElement(stateRoot);

        if (txTrieRoot == null) {
            txTrieRoot = EMPTY_TRIE_HASH;
        }
        byte[] rlpTxTrieRoot = RLP.encodeElement(txTrieRoot);

        if (receiptTrieRoot == null) {
            receiptTrieRoot = EMPTY_TRIE_HASH;
        }
        byte[] rlpReceiptTrieRoot = RLP.encodeElement(receiptTrieRoot);
        byte[] rlpLogsBloom = RLP.encodeElement(logsBloom);
        byte[] rlpDifficulty = RLP.encodeElement(difficulty);
        byte[] rlpExtraData = RLP.encodeElement(extraData);
        byte[] rlpEnergyConsumed = RLP.encodeBigInteger(BigInteger.valueOf(energyConsumed));
        byte[] rlpEnergyLimit = RLP.encodeBigInteger(BigInteger.valueOf(energyLimit));

        byte[] rlpTimestamp = RLP.encodeBigInteger(BigInteger.valueOf(timestamp));

        byte[] rlpSolution = RLP.encodeElement(solution);

        if (withNonce) {
            byte[] rlpNonce = RLP.encodeElement(nonce);
            return RLP.encodeList(
                rlpSealType,
                rlpNumber,
                rlpParentHash,
                rlpCoinbase,
                rlpStateRoot,
                rlpTxTrieRoot,
                rlpReceiptTrieRoot,
                rlpLogsBloom,
                rlpDifficulty,
                rlpExtraData,
                rlpEnergyConsumed,
                rlpEnergyLimit,
                rlpTimestamp,
                rlpNonce,
                rlpSolution);
        } else {
            return RLP.encodeList(
                rlpSealType,
                rlpParentHash,
                rlpCoinbase,
                rlpStateRoot,
                rlpTxTrieRoot,
                rlpReceiptTrieRoot,
                rlpLogsBloom,
                rlpDifficulty,
                rlpNumber,
                rlpTimestamp,
                rlpExtraData,
                rlpSolution,
                rlpEnergyConsumed,
                rlpEnergyLimit);
        }
    }

    public String toStringWithSuffix(final String suffix) {
        return commonDataToStringWithSuffix(suffix)
            + "  nonce="
            + toHexString(nonce)
            + suffix
            + "  solution="
            + toHexString(solution)
            + suffix;
    }

    @Override
    public byte[] getSolution() {
        return solution;
    }

    public void setSolution(byte[] _sl) {
        solution = _sl;
    }

    @Override
    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] _nonce) {
        nonce = _nonce;
    }

    /**
     * Get hash of the header bytes to mine a block
     *
     * @return Blake2b digest (32 bytes) of the raw header bytes.
     */
    @Override
    public byte[] getMineHash() {
        if (mineHashBytes == null) {
            mineHashBytes = HashUtil.h256(getHeaderForMine());
        }
        return mineHashBytes;
    }

    /**
     * Construct a block header from RLP rawData
     *
     * @param rawData the rlpencoded block header data
     * @param isUnsafe construct header from the unsafe data source
     * @return the A0BlockHeader
     */
    public static A0BlockHeader fromRLP(byte[] rawData, boolean isUnsafe) throws Exception {
        return fromRLP((RLPList) RLP.decode2(rawData).get(0), isUnsafe);
    }

    /**
     * Construct a block header from RLP decoded data
     *
     * @param rlpHeader the rlpDecoded block header data
     * @param isUnsafe construct header from the unsafe data source
     * @return the A0BlockHeader
     */
    public static A0BlockHeader fromRLP(RLPList rlpHeader, boolean isUnsafe) throws Exception {
        Builder builder = new Builder();
        if (isUnsafe) {
            builder.fromUnsafeSource();
        }

        return builder.withRlpHeader(rlpHeader)
            .withNonce(rlpHeader.get(RLP_BH_NONCE).getRLPData())
            .withSolution(rlpHeader.get(RLP_BH_SOLUTION).getRLPData())
            .build();
    }

    public byte[] getPowBoundary() {
        return BigIntegers.asUnsignedByteArray(
            32, BigInteger.ONE.shiftLeft(256).divide(getDifficultyBI()));
    }

    public BigInteger getPowBoundaryBI() {
        return BigInteger.ONE.shiftLeft(256).divide(getDifficultyBI());
    }

    /** Builder used to introduce blocks into system that come from unsafe sources */
    public static class Builder extends AbstractBuilder {
        protected byte[] solution;
        protected byte[] nonce;

        /*
         * Builder parameters, not related to header data structure
         */
        private static byte[] EMPTY_SOLUTION = new byte[SOLUTIONSIZE];

        public Builder fromUnsafeSource() {
            return (Builder) super.fromUnsafeSource();
        }

        public Builder withRlpHeader(RLPList rlpHeader) throws HeaderStructureException {
            return (Builder) super.withRlpHeader(rlpHeader);
        }

        public Builder withParentHash(byte[] _parentHash) throws HeaderStructureException {
            return (Builder) super.withParentHash(_parentHash);
        }

        public Builder withCoinbase(AionAddress _coinbase) throws HeaderStructureException {
            return (Builder) super.withCoinbase(_coinbase);
        }

        public Builder withStateRoot(byte[] _stateRoot) throws HeaderStructureException  {
            return (Builder) super.withStateRoot(_stateRoot);
        }

        public Builder withTxTrieRoot(byte[] _txTrieRoot) throws HeaderStructureException {
            return (Builder) super.withTxTrieRoot(_txTrieRoot);
        }

        public Builder withReceiptTrieRoot(byte[] _receiptTrieRoot)
            throws HeaderStructureException {
            return (Builder) super.withReceiptTrieRoot(_receiptTrieRoot);
        }

        public Builder withLogsBloom(byte[] _logsBloom) throws HeaderStructureException {
            return (Builder) super.withLogsBloom(_logsBloom);
        }

        public Builder withDifficulty(byte[] _difficulty) throws HeaderStructureException {
            return (Builder) super.withDifficulty(_difficulty);
        }

        public Builder withTimestamp(long _timestamp) throws HeaderStructureException {
            return (Builder) super.withTimestamp(_timestamp);
        }

        public Builder withTimestamp(byte[] _timestamp) throws HeaderStructureException {
            return (Builder) super.withTimestamp(_timestamp);
        }

        public Builder withNumber(long _number) throws HeaderStructureException {
            return (Builder) super.withNumber(_number);
        }

        public Builder withNumber(byte[] _number) throws HeaderStructureException {
            return (Builder) super.withNumber(_number);
        }

        public Builder withExtraData(byte[] _extraData) throws HeaderStructureException {
            return (Builder) super.withExtraData(_extraData);
        }

        public Builder withEnergyConsumed(byte[] _energyConsumed) throws HeaderStructureException {
            return (Builder) super.withEnergyConsumed(_energyConsumed);
        }

        public Builder withEnergyConsumed(long _energyConsumed) throws HeaderStructureException {
            return (Builder) super.withEnergyConsumed(_energyConsumed);
        }

        public Builder withEnergyLimit(long _energyLimit) throws HeaderStructureException {
            return (Builder) super.withEnergyLimit(_energyLimit);
        }

        public Builder withEnergyLimit(byte[] _energyLimit) throws HeaderStructureException {
            return (Builder) super.withEnergyLimit(_energyLimit);
        }

        Builder withSolution(byte[] _solution) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (_solution == null)
                    throw new HeaderStructureException(
                        "solution", RLP_BH_SOLUTION, "cannot be null");

                if (_solution.length != SOLUTIONSIZE) {
                    throw new HeaderStructureException(
                        "solution", RLP_BH_SOLUTION, "invalid solution length");
                }
            }
            solution = _solution;
            return this;
        }

        Builder withNonce(byte[] _nonce) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (_nonce == null)
                    throw new HeaderStructureException("nonce", RLP_BH_NONCE, "cannot be null");

                if (_nonce.length > NONCE_LENGTH) {
                    throw new HeaderStructureException(
                        "nonce", RLP_BH_NONCE, "cannot be greater than 32 bytes");
                }
            }

            nonce = _nonce;
            return this;
        }

        public A0BlockHeader build() {
            // Formalize the data
            sealType = BlockSealType.SEAL_POW_BLOCK.getSealId();
            parentHash = parentHash == null ? HashUtil.EMPTY_DATA_HASH : parentHash;
            coinbase = coinbase == null ? AddressUtils.ZERO_ADDRESS : coinbase;
            stateRoot = stateRoot == null ? HashUtil.EMPTY_TRIE_HASH : stateRoot;
            txTrieRoot = txTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : txTrieRoot;
            receiptTrieRoot = receiptTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : receiptTrieRoot;
            logsBloom = logsBloom == null ? EMPTY_BLOOM : logsBloom;
            difficulty = difficulty == null ? ByteUtil.EMPTY_HALFWORD : difficulty;
            extraData = extraData == null ? ByteUtil.EMPTY_WORD : extraData;
            nonce = nonce == null ? ByteUtil.EMPTY_WORD : nonce;
            solution = solution == null ? EMPTY_SOLUTION : solution;

            return new A0BlockHeader(
                sealType,
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
        }
    }
}
