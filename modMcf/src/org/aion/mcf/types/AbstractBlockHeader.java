package org.aion.mcf.types;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.util.bytes.ByteUtil.longToBytes;
import static org.aion.util.bytes.ByteUtil.merge;
import static org.aion.util.bytes.ByteUtil.oneByteToHexString;
import static org.aion.util.bytes.ByteUtil.toHexString;
import static org.aion.util.time.TimeUtils.longToDateTime;

import java.math.BigInteger;
import java.util.Objects;
import org.aion.crypto.HashUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.exceptions.HeaderStructureException;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.json.JSONObject;

/** Abstract BlockHeader. */
public abstract class AbstractBlockHeader implements BlockHeader {

    private static final int MAX_DIFFICULTY_LENGTH = 16;
    private static final int RLP_BH_SEALTYPE = 0,
        RLP_BH_NUMBER = 1,
        RLP_BH_PARENTHASH = 2,
        RLP_BH_COINBASE = 3,
        RLP_BH_STATEROOT = 4,
        RLP_BH_TXTRIE = 5,
        RLP_BH_RECEIPTTRIE = 6,
        RLP_BH_LOGSBLOOM = 7,
        RLP_BH_DIFFICULTY = 8,
        RLP_BH_EXTRADATA = 9,
        RLP_BH_NRG_CONSUMED = 10,
        RLP_BH_NRG_LIMIT = 11,
        RLP_BH_TIMESTAMP = 12;
    
    //TODO: [Unity] We should probably move this enum somewhere else

    public enum BlockSealType {
        SEAL_NA((byte) 0),
        SEAL_POW_BLOCK((byte) 1),
        SEAL_POS_BLOCK((byte) 2);
        
        byte sealId;
        
        BlockSealType(byte sealId) {
            this.sealId = sealId;
        }

        public byte getSealId() {
            return sealId;
        }
        
        public static BlockSealType byteToSealType(byte id) {
            if (id == SEAL_POW_BLOCK.sealId) {
                return SEAL_POW_BLOCK;
            } else if (id == SEAL_POS_BLOCK.sealId) {
                return SEAL_POS_BLOCK;
            } else {
                return SEAL_NA;
            }
        }
    }


    protected BlockSealType sealType;

    /* The SHA3 256-bit hash of the parent block, in its entirety */
    protected byte[] parentHash;

    /*
     * The 256-bit address to which all fees collected from the successful
     * mining of this block be transferred; formally
     */
    protected AionAddress coinbase;
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

    /* The logsBloom is a 256 bytes array for filtering the transaction fit the querying conditions*/
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
     * A long value containing energy consumed within this block
     */
    protected long energyConsumed;

    /*
     * A long value containing energy limit of this block
     */
    protected long energyLimit;

    protected byte[] mineHashBytes;

    public AbstractBlockHeader() {}

    public AbstractBlockHeader(
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
        long _timestamp) {
        sealType = BlockSealType.byteToSealType(_sealType);
        if (_coinbase == null) {
            throw new IllegalArgumentException("Invalid coinbase!");
        } else {
            coinbase = _coinbase;
        }
        stateRoot = _stateRoot;
        txTrieRoot = _txTrieRoot;
        receiptTrieRoot = _receiptTrieRoot;

        parentHash = _parentHash;
        logsBloom = _logsBloom;
        difficulty = _difficulty;
        number = _number;
        timestamp = _timestamp;
        extraData = _extraData;

        // Fields required for energy based VM
        energyConsumed = _energyConsumed;
        energyLimit = _energyLimit;
    }

    public byte[] getParentHash() {
        return parentHash;
    }

    public AionAddress getCoinbase() {
        return coinbase;
    }

    public void setCoinbase(AionAddress _coinbase) {
        coinbase = _coinbase;
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public void setStateRoot(byte[] _stateRoot) {
        stateRoot = _stateRoot;
    }

    public byte[] getTxTrieRoot() {
        return txTrieRoot;
    }

    public void setTxTrieRoot(byte[] _txTrieRoot) {
        txTrieRoot = _txTrieRoot;
    }

    public void setReceiptsRoot(byte[] _receiptTrieRoot) {
        receiptTrieRoot = _receiptTrieRoot;
    }

    public byte[] getReceiptsRoot() {
        return receiptTrieRoot;
    }

    public void setTransactionsRoot(byte[] _stateRoot) {
        txTrieRoot = _stateRoot;
    }

    public byte[] getLogsBloom() {
        return logsBloom;
    }

    public byte[] getDifficulty() {
        return difficulty;
    }

    /**
     * @implNote when the difficulty data field exceed the system limit(16 bytes), this method will
     *     return BigInteger.ZERO for the letting the validate() in the AionDifficultyRule return
     *     false. The difficulty in the PoW blockchain should be always a positive value.
     * @see org.aion.zero.impl.valid.AionDifficultyRule.validate;
     * @return the difficulty as the BigInteger format.
     */
    @SuppressWarnings("JavadocReference")
    public BigInteger getDifficultyBI() {
        if (difficulty == null || difficulty.length > MAX_DIFFICULTY_LENGTH) {
            AionLoggerFactory.getLogger("CONS").error("Invalid difficulty length!");
            return BigInteger.ZERO;
        }
        return new BigInteger(1, difficulty);
    }

    public void setDifficulty(byte[] _difficulty) {
        difficulty = _difficulty;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long _timestamp) {
        timestamp = _timestamp;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long _number) {
        number = _number;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public void setLogsBloom(byte[] _logsBloom) {
        logsBloom = _logsBloom;
    }

    public void setExtraData(byte[] _extraData) {
        extraData = _extraData;
    }

    public boolean isGenesis() {
        return number == 0;
    }

    public BlockSealType getSealType() {
        return sealType;
    }

    public void setSealType(BlockSealType _sealType) {
        sealType = _sealType;
    }

    public long getEnergyConsumed() {
        return energyConsumed;
    }

    public long getEnergyLimit() {
        return energyLimit;
    }

    /**
     * For the stratum RPC getHeaderByBlockNumber
     *
     * @return a JSONObject represent part of the block header.
     */
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.putOpt(
            "version",
            oneByteToHexString(sealType.sealId)); // TODO: require to check the pool protocol specs.
        obj.putOpt("number", toHexString(longToBytes(number)));
        obj.putOpt("parentHash", toHexString(parentHash));
        obj.putOpt("coinBase", toHexString(coinbase.toByteArray()));
        obj.putOpt("stateRoot", toHexString(stateRoot));
        obj.putOpt("txTrieRoot", toHexString(txTrieRoot));
        obj.putOpt("receiptTrieRoot", toHexString(receiptTrieRoot));
        obj.putOpt("logsBloom", toHexString(logsBloom));
        obj.putOpt("difficulty", toHexString(difficulty));
        obj.putOpt("extraData", toHexString(extraData));
        obj.putOpt("energyConsumed", toHexString(longToBytes(energyConsumed)));
        obj.putOpt("energyLimit", toHexString(longToBytes(energyLimit)));
        obj.putOpt("timestamp", toHexString(longToBytes(timestamp)));

        return obj;
    }

    protected void constructCommonHeader(RLPList header) {
        byte[] _sealType = header.get(RLP_BH_SEALTYPE).getRLPData();
        if (_sealType.length != 1) {
            throw new IllegalArgumentException("The length of the seal type is not correct!");
        }
        sealType = BlockSealType.byteToSealType(_sealType[0]);

        byte[] nrBytes = header.get(RLP_BH_NUMBER).getRLPData();
        number = nrBytes == null ? 0 : (new BigInteger(1, nrBytes)).longValue();
        if (number < 0) {
            throw new IllegalArgumentException("Invalid block number data!");
        }

        parentHash = header.get(RLP_BH_PARENTHASH).getRLPData();

        byte[] data = header.get(RLP_BH_COINBASE).getRLPData();
        if (data == null || data.length != AionAddress.LENGTH) {
            throw new IllegalArgumentException("Invalid coinbase data!");
        }
        coinbase = new AionAddress(data);

        stateRoot = header.get(RLP_BH_STATEROOT).getRLPData();

        txTrieRoot = header.get(RLP_BH_TXTRIE).getRLPData();
        if (txTrieRoot == null) {
            txTrieRoot = EMPTY_TRIE_HASH;
        }

        receiptTrieRoot = header.get(RLP_BH_RECEIPTTRIE).getRLPData();
        if (receiptTrieRoot == null) {
            receiptTrieRoot = EMPTY_TRIE_HASH;
        }

        logsBloom = header.get(RLP_BH_LOGSBLOOM).getRLPData();

        difficulty = header.get(RLP_BH_DIFFICULTY).getRLPData();

        extraData = header.get(RLP_BH_EXTRADATA).getRLPData();

        byte[] energyConsumedBytes = header.get(RLP_BH_NRG_CONSUMED).getRLPData();
        energyConsumed =
            energyConsumedBytes == null
                ? 0
                : (new BigInteger(1, energyConsumedBytes).longValue());
        if (energyConsumed < 0) {
            throw new IllegalArgumentException("Invalid energyConsumed data!");
        }

        byte[] energyLimitBytes = header.get(RLP_BH_NRG_LIMIT).getRLPData();
        energyLimit =
            energyLimitBytes == null ? 0 : (new BigInteger(1, energyLimitBytes).longValue());
        if (energyLimit < 0) {
            throw new IllegalArgumentException("Invalid energyLimit data!");
        }

        byte[] tsBytes = header.get(RLP_BH_TIMESTAMP).getRLPData();
        timestamp = tsBytes == null ? 0 : (new BigInteger(1, tsBytes)).longValue();
        if (timestamp < 0) {
            throw new IllegalArgumentException("Invalid timestamp data!");
        }
    }

    protected void copyCommonHeader(AbstractBlockHeader header) {
        sealType = header.getSealType();

        number = header.getNumber();

        parentHash = new byte[header.getParentHash().length];
        System.arraycopy(header.getParentHash(), 0, parentHash, 0, parentHash.length);

        if (header.coinbase == null) {
            throw new IllegalArgumentException("Invalid coinbase data!");
        } else {
            coinbase = header.coinbase;
        }

        stateRoot = new byte[header.getStateRoot().length];
        System.arraycopy(header.getStateRoot(), 0, stateRoot, 0, stateRoot.length);

        txTrieRoot = new byte[header.getTxTrieRoot().length];
        System.arraycopy(header.getTxTrieRoot(), 0, txTrieRoot, 0, txTrieRoot.length);

        receiptTrieRoot = new byte[header.getReceiptsRoot().length];
        System.arraycopy(header.getReceiptsRoot(), 0, receiptTrieRoot, 0, receiptTrieRoot.length);

        logsBloom = new byte[header.getLogsBloom().length];
        System.arraycopy(header.getLogsBloom(), 0, logsBloom, 0, logsBloom.length);

        difficulty = new byte[header.getDifficulty().length];
        System.arraycopy(header.getDifficulty(), 0, difficulty, 0, difficulty.length);

        extraData = new byte[header.getExtraData().length];
        System.arraycopy(header.getExtraData(), 0, extraData, 0, extraData.length);

        energyConsumed = header.getEnergyConsumed();

        energyLimit = header.getEnergyLimit();

        timestamp = header.getTimestamp();
    }

    protected String commonDataToStringWithSuffix(final String suffix) {
        return "  hash="
            + toHexString(getHash())
            + "  Length: "
            + getHash().length
            + suffix
            + "  sealType="
            + Integer.toHexString(sealType.sealId)
            + "  Length: "
            + suffix
            + "  number="
            + number
            + suffix
            + "  parentHash="
            + toHexString(parentHash)
            + "  parentHash: "
            + parentHash.length
            + suffix
            + "  coinbase="
            + coinbase.toString()
            + "  coinBase: "
            + coinbase.toByteArray().length
            + suffix
            + "  stateRoot="
            + toHexString(stateRoot)
            + "  stateRoot: "
            + stateRoot.length
            + suffix
            + "  txTrieHash="
            + toHexString(txTrieRoot)
            + "  txTrieRoot: "
            + txTrieRoot.length
            + suffix
            + "  receiptsTrieHash="
            + toHexString(receiptTrieRoot)
            + "  receiptTrieRoot: "
            + receiptTrieRoot.length
            + suffix
            + "  difficulty="
            + toHexString(difficulty)
            + "  difficulty: "
            + difficulty.length
            + suffix
            + "  energyConsumed="
            + energyConsumed
            + suffix
            + "  energyLimit="
            + energyLimit
            + suffix
            + "  extraData="
            + toHexString(extraData)
            + suffix
            + "  timestamp="
            + timestamp
            + " ("
            + longToDateTime(timestamp)
            + ")"
            + suffix;
    }

    /**
     * Set the energyConsumed field in header, this is used during block creation
     *
     * @param _energyConsumed total energyConsumed during execution of transactions
     */
    public void setEnergyConsumed(long _energyConsumed) {
        energyConsumed = _energyConsumed;
    }

    protected byte[] getHeaderForMine() {
        return merge(
            new byte[] {sealType.sealId},
            longToBytes(number),
            parentHash,
            coinbase.toByteArray(),
            stateRoot,
            txTrieRoot,
            receiptTrieRoot,
            logsBloom,
            difficulty,
            extraData,
            longToBytes(energyConsumed),
            longToBytes(energyLimit),
            longToBytes(timestamp));
    }

    public static class AbstractBuilder {

        protected byte sealType;
        protected byte[] parentHash;
        protected AionAddress coinbase;
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

        /*
         * Builder parameters, not related to header data structure
         */
        protected boolean isFromUnsafeSource = false;
        protected static byte[] EMPTY_BLOOM = new byte[256];

        /**
         * Indicates that the data is from an unsafe source
         *
         * @return {@code builder} same instance of builder
         */
        protected AbstractBuilder fromUnsafeSource() {
            isFromUnsafeSource = true;
            return this;
        }

        protected AbstractBuilder withSealType(byte _sealType) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_sealType < 1) {
                    throw new HeaderStructureException(
                        "version", RLP_BH_SEALTYPE, "must be greater than 0");
                }
            }

            sealType = _sealType;
            return this;
        }

        protected AbstractBuilder withParentHash(byte[] _parentHash)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_parentHash == null)
                    throw new HeaderStructureException(
                        "parentHash", RLP_BH_PARENTHASH, "cannot be null");

                if (_parentHash.length != 32)
                    throw new HeaderStructureException(
                        "parentHash", RLP_BH_PARENTHASH, "must be of length 32");
            }

            parentHash = _parentHash;
            return this;
        }

        protected AbstractBuilder withCoinbase(AionAddress _coinbase)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_coinbase == null)
                    throw new HeaderStructureException(
                        "coinbase", RLP_BH_COINBASE, "cannot be null");
            }

            coinbase = _coinbase;
            return this;
        }

        protected AbstractBuilder withStateRoot(byte[] _stateRoot) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_stateRoot == null)
                    throw new HeaderStructureException(
                        "stateRoot", RLP_BH_STATEROOT, "cannot be null");

                if (_stateRoot.length != 32)
                    throw new HeaderStructureException(
                        "stateRoot", RLP_BH_STATEROOT, "must be of length 32");
            }

            stateRoot = _stateRoot;
            return this;
        }

        protected AbstractBuilder withTxTrieRoot(byte[] _txTrieRoot)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (_txTrieRoot == null)
                    throw new HeaderStructureException(
                        "txTrieRoot", RLP_BH_TXTRIE, "cannot be null");

                if (_txTrieRoot.length != 32)
                    throw new HeaderStructureException(
                        "txTrieRoot", RLP_BH_TXTRIE, "must be of length 32");
            }

            txTrieRoot = _txTrieRoot;
            return this;
        }

        protected AbstractBuilder withReceiptTrieRoot(byte[] _receiptTrieRoot)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (_receiptTrieRoot == null)
                    throw new HeaderStructureException(
                        "receiptTrieRoot", RLP_BH_RECEIPTTRIE, "cannot be null");

                if (_receiptTrieRoot.length != 32)
                    throw new HeaderStructureException(
                        "receiptTrieRoot", RLP_BH_RECEIPTTRIE, "must be of length 32");
            }

            receiptTrieRoot = _receiptTrieRoot;
            return this;
        }

        protected AbstractBuilder withLogsBloom(byte[] _logsBloom) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (_logsBloom == null)
                    throw new HeaderStructureException(
                        "logsBloom", RLP_BH_LOGSBLOOM, "cannot be null");

                if (_logsBloom.length != 256)
                    throw new HeaderStructureException(
                        "logsBloom", RLP_BH_LOGSBLOOM, "logsBloom must be of length 256");
            }

            logsBloom = _logsBloom;
            return this;
        }

        protected AbstractBuilder withDifficulty(byte[] _difficulty)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(_difficulty);
                if (_difficulty.length > 16)
                    throw new HeaderStructureException(
                        "difficulty", RLP_BH_DIFFICULTY, "cannot be greater than 16 bytes");
            }
            difficulty = _difficulty;
            return this;
        }

        protected AbstractBuilder withTimestamp(long _timestamp) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_timestamp < 0)
                    throw new HeaderStructureException(
                        "timestamp", RLP_BH_TIMESTAMP, "must be positive value");
            }

            timestamp = _timestamp;
            return this;
        }

        protected AbstractBuilder withTimestamp(byte[] _timestamp) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                Objects.requireNonNull(_timestamp);
                if (_timestamp.length > 8)
                    throw new HeaderStructureException(
                        "timestamp", RLP_BH_TIMESTAMP, "cannot be greater than 8 bytes");
            }
            return withTimestamp(ByteUtil.byteArrayToLong(_timestamp));
        }

        protected AbstractBuilder withNumber(long _number) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_number < 0) {
                    throw new HeaderStructureException("number", RLP_BH_NUMBER, "must be positive");
                }
            }

            number = _number;
            return this;
        }

        protected AbstractBuilder withNumber(byte[] _number) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_number == null)
                    throw new HeaderStructureException("number", RLP_BH_NUMBER, "cannot be null");
            }
            return withNumber(ByteUtil.byteArrayToLong(_number));
        }

        protected AbstractBuilder withExtraData(byte[] _extraData) throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (_extraData == null)
                    throw new HeaderStructureException(
                        "extraData", RLP_BH_EXTRADATA, "cannot be null");

                if (_extraData.length > 32) {
                    throw new HeaderStructureException(
                        "extraData", RLP_BH_EXTRADATA, "cannot be greater than 32 bytes");
                }
            }

            extraData = _extraData;
            return this;
        }

        protected AbstractBuilder withEnergyConsumed(long _energyConsumed)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_energyConsumed < 0) {
                    throw new HeaderStructureException(
                        "energyConsumed", RLP_BH_NRG_CONSUMED, "must be positive value");
                }
            }

            energyConsumed = _energyConsumed;
            return this;
        }

        protected AbstractBuilder withEnergyConsumed(byte[] _energyConsumed)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_energyConsumed == null)
                    throw new HeaderStructureException(
                        "energyConsumed", RLP_BH_NRG_CONSUMED, "cannot be null");

                if (_energyConsumed.length > 8)
                    throw new HeaderStructureException(
                        "energyConsumed",
                        RLP_BH_NRG_CONSUMED,
                        "cannot be greater than 8 bytes");
            }

            return withEnergyConsumed(ByteUtil.byteArrayToLong(_energyConsumed));
        }

        protected AbstractBuilder withEnergyLimit(long _energyLimit) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_energyLimit < 0) {
                    throw new HeaderStructureException(
                        "energyLimitException",
                        RLP_BH_NRG_LIMIT,
                        "energyLimit must be positive value");
                }
            }

            energyLimit = _energyLimit;
            return this;
        }

        protected AbstractBuilder withEnergyLimit(byte[] _energyLimit)
            throws HeaderStructureException {
            if (isFromUnsafeSource) {

                if (_energyLimit == null)
                    throw new HeaderStructureException(
                        "energyLimit", RLP_BH_NRG_LIMIT, "cannot be null");

                if (_energyLimit.length > 8)
                    throw new HeaderStructureException(
                        "energyLimit",
                        RLP_BH_NRG_LIMIT,
                        "energyLimit cannot be greater than 8 bytes");
            }
            return withEnergyLimit(ByteUtil.byteArrayToLong(_energyLimit));
        }

        public AbstractBuilder withRlpHeader(RLPList rlpHeader) throws HeaderStructureException {
            byte[] sealType = rlpHeader.get(RLP_BH_SEALTYPE).getRLPData();
            if (sealType != null && sealType.length == 1) {
                withSealType(sealType[0]);
            }

            byte[] nrBytes = rlpHeader.get(RLP_BH_NUMBER).getRLPData();
            if (nrBytes != null) {
                withNumber(nrBytes);
            }

            withParentHash(rlpHeader.get(RLP_BH_PARENTHASH).getRLPData());

            withCoinbase(new AionAddress(rlpHeader.get(RLP_BH_COINBASE).getRLPData()));

            withStateRoot(rlpHeader.get(RLP_BH_STATEROOT).getRLPData());

            byte[] txTrieRoot = rlpHeader.get(RLP_BH_TXTRIE).getRLPData();
            if (txTrieRoot != null) {
                withTxTrieRoot(txTrieRoot);
            }

            byte[] receiptTrieRoot = rlpHeader.get(RLP_BH_RECEIPTTRIE).getRLPData();
            if (receiptTrieRoot != null) {
                withReceiptTrieRoot(receiptTrieRoot);
            }

            withLogsBloom(rlpHeader.get(RLP_BH_LOGSBLOOM).getRLPData());

            withDifficulty(rlpHeader.get(RLP_BH_DIFFICULTY).getRLPData());

            withExtraData(rlpHeader.get(RLP_BH_EXTRADATA).getRLPData());

            byte[] energyConsumedBytes = rlpHeader.get(RLP_BH_NRG_CONSUMED).getRLPData();
            if (energyConsumedBytes != null) {
                withEnergyConsumed(energyConsumedBytes);
            }

            byte[] energyLimitBytes = rlpHeader.get(RLP_BH_NRG_LIMIT).getRLPData();
            if (energyLimitBytes != null) {
                withEnergyLimit(energyLimitBytes);
            }

            byte[] tsBytes = rlpHeader.get(RLP_BH_TIMESTAMP).getRLPData();
            if (tsBytes != null) {
                withTimestamp(tsBytes);
            }

            return this;
        }
    }

    public byte[] getHash() {
        return HashUtil.h256(getEncoded());
    }

    public byte[] getEncoded() {
        return getEncoded(true); // with Nonce or Signature
    }

    public abstract byte[] getEncoded(boolean withNonceOrSignature);

    public String toString() {
        return toStringWithSuffix("\n");
    }

    public String toFlatString() {
        return toStringWithSuffix("");
    }

    public abstract String toStringWithSuffix(final String suffix);
}