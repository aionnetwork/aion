package org.aion.zero.impl.types;

import static org.aion.util.bytes.ByteUtil.longToBytes;
import static org.aion.util.bytes.ByteUtil.merge;
import static org.aion.util.bytes.ByteUtil.oneByteToHexString;
import static org.aion.util.bytes.ByteUtil.toHexString;
import static org.aion.util.time.TimeUtils.longToDateTime;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import org.aion.base.ConstantUtil;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.BlockHeader;
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
public final class A0BlockHeader implements BlockHeader {

    private static final int RPL_BH_SEALTYPE = 0;
    private static final int RLP_BH_NUMBER = 1;
    private static final int RLP_BH_PARENTHASH = 2;
    private static final int RLP_BH_COINBASE = 3;
    private static final int RLP_BH_STATEROOT = 4;
    private static final int RLP_BH_TXTRIE = 5;
    private static final int RLP_BH_RECEIPTTRIE = 6;
    private static final int RLP_BH_LOGSBLOOM = 7;
    private static final int RLP_BH_DIFFICULTY = 8;
    private static final int RLP_BH_EXTRADATA = 9;
    private static final int RLP_BH_NRG_CONSUMED = 10;
    private static final int RLP_BH_NRG_LIMIT = 11;
    private static final int RLP_BH_TIMESTAMP = 12;
    private static final int RLP_BH_NONCE = 13;
    private static final int RLP_BH_SOLUTION = 14;
    private static final BlockSealType sealType = BlockSealType.SEAL_POW_BLOCK;
    private static final byte[] rlpEncodedSealType = RLP.encodeElement(new byte[] {A0BlockHeader.sealType.getSealId()});

    /** The SHA3 256-bit hash of the parent block, in its entirety */
    private final byte[] parentHash;

    /**
     * The 256-bit address to which all fees collected from the successful mining of this block be
     * transferred; formally
     */
    private final AionAddress coinbase;
    /**
     * The SHA3 256-bit hash of the root node of the state trie, after all transactions are executed
     * and finalisations applied
     */
    private final byte[] stateRoot;
    /**
     * The SHA3 256-bit hash of the root node of the trie structure populated with each transaction
     * in the transaction list portion, the trie is populate by [key, val] --> [rlp(index),
     * rlp(tx_recipe)] of the block
     */
    private final byte[] txTrieRoot;
    /**
     * The SHA3 256-bit hash of the root node of the trie structure populated with each transaction
     * recipe in the transaction recipes list portion, the trie is populate by [key, val] -->
     * [rlp(index), rlp(tx_recipe)] of the block
     */
    private final byte[] receiptTrieRoot;

    /**
     * The logsBloom is a 256 bytes array for filtering the transaction fit the querying conditions
     */
    private final byte[] logsBloom;
    /**
     * A scalar value corresponding to the difficulty level of this block. This can be calculated
     * from the previous block’s difficulty level and the timestamp
     */
    private final byte[] difficulty;

    /** A scalar value equal to the reasonable output of Unix's time() at this block's inception */
    private final long timestamp;

    /**
     * A scalar value equal to the number of ancestor blocks. The genesis block has a number of zero
     */
    private final long number;

    /**
     * An arbitrary byte array containing data relevant to this block. With the exception of the
     * genesis block, this must be 32 bytes or fewer
     */
    private final byte[] extraData;

    /** A long value containing energy consumed within this block */
    private final long energyConsumed;

    /** A long value containing energy limit of this block */
    private final long energyLimit;

    /** The hash of block template for mining */
    private byte[] mineHashBytes;

    /**
     * A 256-bit hash which proves that a sufficient amount of computation has been carried out on
     * this block
     */
    private final byte[] nonce;

    /////////////////////////////////////////////////////////////////
    // (1344 in 200-9, 1408 in 210,9)
    private final byte[] solution; // The equihash solution in compressed format

    private byte[] headerHash;

    public static final int NONCE_LENGTH = 32;
    public static final int SOLUTIONSIZE = 1408;

    public A0BlockHeader(A0BlockHeader.Builder builder) {
        this.coinbase = builder.coinbase;
        this.stateRoot = builder.stateRoot;
        this.txTrieRoot = builder.txTrieRoot;
        this.receiptTrieRoot = builder.receiptTrieRoot;
        this.parentHash = builder.parentHash;
        this.logsBloom = builder.logsBloom;
        this.difficulty = builder.difficulty;
        this.number = builder.number;
        this.timestamp = builder.timestamp;
        this.extraData = builder.extraData;
        this.energyConsumed = builder.energyConsumed;
        this.energyLimit = builder.energyLimit;
        // New fields required for Equihash
        this.solution = builder.solution;
        this.nonce = builder.nonce;
    }
    
    /**
     * Returns a new header that is identical to this one, except with a different timestamp
     */    
    public A0BlockHeader updateTimestamp(long newTimestamp) {
        return Builder.newInstance()
                .withHeader(this)
                .withTimestamp(newTimestamp)
                .build();
    }

    public byte[] getSolution() {
        return solution.clone();
    }

    public byte[] getNonce() {
        return nonce.clone();
    }

    /**
     * Get hash of the header bytes to mine a block
     *
     * @return Blake2b digest (32 bytes) of the raw header bytes.
     */
    public byte[] getMineHash() {
        if (mineHashBytes == null) {
            mineHashBytes = HashUtil.h256(getHeaderForMining());
        }
        return mineHashBytes.clone();
    }

    public byte[] getPowBoundary() {
        return BigIntegers.asUnsignedByteArray(
                32, BigInteger.ONE.shiftLeft(256).divide(getDifficultyBI()));
    }

    public BigInteger getPowBoundaryBI() {
        return BigInteger.ONE.shiftLeft(256).divide(getDifficultyBI());
    }

    /** Builder used to introduce blocks into system that come from unsafe sources */
    public static class Builder {
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
        protected byte[] solution;
        protected byte[] nonce;

        /*
         * Builder parameters, not related to header data structure
         */
        boolean isFromUnsafeSource;
        private static byte[] EMPTY_SOLUTION = new byte[SOLUTIONSIZE];
        private static byte[] EMPTY_BLOOM = new byte[BLOOM_BYTE_SIZE];

        public static Builder newInstance(boolean fromUnsafeSource)
        {
            return new Builder(fromUnsafeSource);
        }

        public static Builder newInstance()
        {
            return new Builder(false);
        }

        private Builder(boolean fromUnsafeSource) {
            isFromUnsafeSource = fromUnsafeSource;
        }

        public Builder withParentHash(byte[] parentHash) {
            if (isFromUnsafeSource) {
                if (parentHash == null) {
                    throw new NullPointerException("parentHash cannot be null");
                }

                if (parentHash.length != HASH_BYTE_SIZE)
                    throw new IllegalArgumentException("parentHash must be of length 32");
            }

            this.parentHash = parentHash;
            return this;
        }

        public Builder withCoinbase(AionAddress coinbase) {
            if (isFromUnsafeSource) {
                if (coinbase == null) {
                    throw new NullPointerException("coinbase cannot be null");
                }
            }

            this.coinbase = coinbase;
            return this;
        }

        public Builder withStateRoot(byte[] stateRoot) {
            if (isFromUnsafeSource) {
                if (stateRoot == null) {
                    throw new NullPointerException("stateRoot cannot be null");
                }
                if (stateRoot.length != HASH_BYTE_SIZE)
                    throw new IllegalArgumentException("stateRoot must be of length 32");
            }

            this.stateRoot = stateRoot;
            return this;
        }

        public Builder withTxTrieRoot(byte[] txTrieRoot) {
            if (isFromUnsafeSource) {
                if (txTrieRoot == null) {
                    throw new NullPointerException("txTrieRoot cannot be null");
                }
                if (txTrieRoot.length != HASH_BYTE_SIZE)
                    throw new IllegalArgumentException("txTrieRoot must be of length 32");
            }

            this.txTrieRoot = txTrieRoot;
            return this;
        }

        public Builder withReceiptTrieRoot(byte[] receiptTrieRoot) {
            if (isFromUnsafeSource) {
                if (receiptTrieRoot == null) {
                    throw new NullPointerException("receiptTrieRoot cannot be null");
                }
                if (receiptTrieRoot.length != HASH_BYTE_SIZE)
                    throw new IllegalArgumentException("receiptTrieRoot must be of length 32");
            }

            this.receiptTrieRoot = receiptTrieRoot;
            return this;
        }

        public Builder withLogsBloom(byte[] logsBloom) {
            if (isFromUnsafeSource) {
                if (logsBloom == null) {
                    throw new NullPointerException("logsBloom cannot be null");
                }
                if (logsBloom.length != BLOOM_BYTE_SIZE)
                    throw new IllegalArgumentException("logsBloom must be of length 256");
            }

            this.logsBloom = logsBloom;
            return this;
        }

        public Builder withDifficulty(byte[] difficulty) {
            if (isFromUnsafeSource) {
                if (difficulty == null) {
                    throw new NullPointerException("difficulty cannot be null");
                }
                if (difficulty.length > MAX_DIFFICULTY_LENGTH)
                    throw new IllegalArgumentException(
                            "difficulty cannot be greater than 16 bytes");
            }
            this.difficulty = difficulty;
            return this;
        }

        public Builder withTimestamp(long timestamp) {
            if (isFromUnsafeSource) {
                if (timestamp < 0)
                    throw new IllegalArgumentException(
                        "timestamp must be positive value");
            }

            this.timestamp = timestamp;
            return this;
        }

        protected Builder withTimestamp(byte[] timestamp) {
            if (isFromUnsafeSource) {
                if (timestamp == null) {
                    throw new NullPointerException("timestamp cannot be null");
                }
                if (timestamp.length > Long.BYTES)
                    throw new IllegalArgumentException("timestamp cannot be greater than 8 bytes");
            }
            return withTimestamp(ByteUtil.byteArrayToLong(timestamp));
        }

        public Builder withNumber(long _number) {
            if (isFromUnsafeSource) {
                if (_number < 0) {
                    throw new IllegalArgumentException("number must be positive");
                }
            }

            number = _number;
            return this;
        }

        protected Builder withNumber(byte[] number) {
            if (isFromUnsafeSource) {
                if (number == null) {
                    throw new NullPointerException("number can not be null");
                }
                if (number.length > Long.BYTES) {
                    throw new IllegalAccessError("number length cannot be greater than 8 bytes");
                }
            }

            return withNumber(ByteUtil.byteArrayToLong(number));
        }

        public Builder withExtraData(byte[] extraData) {
            if (isFromUnsafeSource) {
                if (extraData == null) {
                    throw new NullPointerException("extraData cannot be null");
                }
                if (extraData.length > HASH_BYTE_SIZE) {
                    throw new IllegalArgumentException("extraData invalid length");
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

        Builder withEnergyConsumed(byte[] _energyConsumed) {
            if (isFromUnsafeSource) {
                if (_energyConsumed == null) {
                    throw new NullPointerException("energyConsumed cannot be null");
                }
                if (_energyConsumed.length > Long.BYTES)
                    throw new IllegalArgumentException(
                            "energyConsumed cannot be greater than 8 bytes");
            }

            return withEnergyConsumed(ByteUtil.byteArrayToLong(_energyConsumed));
        }

        public Builder withEnergyLimit(long energyLimit) {
            if (isFromUnsafeSource) {
                if (energyLimit < 0) {
                    throw new IllegalArgumentException(
                        "energyLimitException energyLimit must be positive value");
                }
            }

            this.energyLimit = energyLimit;
            return this;
        }

        protected Builder withEnergyLimit(byte[] energyLimit) {
            if (isFromUnsafeSource) {
                if (energyLimit == null) {
                    throw new NullPointerException(
                        "energyLimit cannot be null");
                }

                if (energyLimit.length > Long.BYTES)
                    throw new IllegalArgumentException(
                        "energyLimit energyLimit cannot be greater than 8 bytes");
            }
            return withEnergyLimit(ByteUtil.byteArrayToLong(energyLimit));
        }

        /**
         * Construct a block header from RLP encoded data
         *
         * @param rlpEncoded the rlpEncoded block header data
         * @return the Builder
         */
        @VisibleForTesting
        public Builder withRlpEncodedData(byte[] rlpEncoded) {
            if (rlpEncoded == null) {
                throw new NullPointerException("rlpEncoded data can not be null");
            }

            return withRlpList((RLPList) RLP.decode2(rlpEncoded).get(0));
        }

        /**
         * Construct a block header from RLP decoded data
         *
         * @param rlpHeader the rlpDecoded block header data
         * @return the Builder
         */
        public Builder withRlpList(RLPList rlpHeader) {
            if (rlpHeader == null) {
                throw new NullPointerException("rlpHeader can not be null");
            }

            if (isFromUnsafeSource) {
                byte[] sealType = rlpHeader.get(RPL_BH_SEALTYPE).getRLPData();

                if (sealType == null || sealType.length != 1) {
                    throw new IllegalArgumentException("Invalid Sealtype data length");
                }

                if (sealType[0] != BlockSealType.SEAL_POW_BLOCK.getSealId()) {
                    throw new IllegalArgumentException("Invalid Seal type");
                }
            }

            withNumber(rlpHeader.get(RLP_BH_NUMBER).getRLPData());
            withParentHash(rlpHeader.get(RLP_BH_PARENTHASH).getRLPData());
            withCoinbase(new AionAddress(rlpHeader.get(RLP_BH_COINBASE).getRLPData()));
            withStateRoot(rlpHeader.get(RLP_BH_STATEROOT).getRLPData());
            withTxTrieRoot(rlpHeader.get(RLP_BH_TXTRIE).getRLPData());
            withReceiptTrieRoot(rlpHeader.get(RLP_BH_RECEIPTTRIE).getRLPData());
            withLogsBloom(rlpHeader.get(RLP_BH_LOGSBLOOM).getRLPData());
            withDifficulty(rlpHeader.get(RLP_BH_DIFFICULTY).getRLPData());
            withExtraData(rlpHeader.get(RLP_BH_EXTRADATA).getRLPData());
            withEnergyConsumed(rlpHeader.get(RLP_BH_NRG_CONSUMED).getRLPData());
            withEnergyLimit(rlpHeader.get(RLP_BH_NRG_LIMIT).getRLPData());
            withTimestamp(rlpHeader.get(RLP_BH_TIMESTAMP).getRLPData());
            withNonce(rlpHeader.get(RLP_BH_NONCE).getRLPData());
            withSolution(rlpHeader.get(RLP_BH_SOLUTION).getRLPData());

            return this;
        }

        public Builder withSolution(byte[] solution) {
            if (isFromUnsafeSource) {

                if (solution == null) throw new NullPointerException("solution cannot be null");

                if (solution.length != SOLUTIONSIZE) {
                    throw new IllegalArgumentException("solution invalid solution length");
                }
            }
            this.solution = solution;
            return this;
        }

        public Builder withNonce(byte[] _nonce) {
            if (isFromUnsafeSource) {

                if (_nonce == null) throw new NullPointerException("nonce cannot be null");

                if (_nonce.length != NONCE_LENGTH) {
                    throw new IllegalArgumentException("nonce cannot be greater than 32 bytes");
                }
            }

            nonce = _nonce;
            return this;
        }

        public A0BlockHeader build() {
            // Formalize the data
            parentHash = parentHash == null ? HashUtil.EMPTY_DATA_HASH : parentHash;
            coinbase = coinbase == null ? AddressUtils.ZERO_ADDRESS : coinbase;
            stateRoot = stateRoot == null ? ConstantUtil.EMPTY_TRIE_HASH : stateRoot;
            txTrieRoot = txTrieRoot == null ? ConstantUtil.EMPTY_TRIE_HASH : txTrieRoot;
            receiptTrieRoot = receiptTrieRoot == null ? ConstantUtil.EMPTY_TRIE_HASH : receiptTrieRoot;
            logsBloom = logsBloom == null ? EMPTY_BLOOM : logsBloom;
            difficulty = difficulty == null ? ByteUtil.EMPTY_HALFWORD : difficulty;
            extraData = extraData == null ? ByteUtil.EMPTY_WORD : extraData;
            nonce = nonce == null ? ByteUtil.EMPTY_WORD : nonce;
            solution = solution == null ? EMPTY_SOLUTION : solution;

            return new A0BlockHeader(this);
        }

        public Builder withHeader(A0BlockHeader header) {
            if (header == null) {
                throw new NullPointerException();
            }

            number = header.getNumber();
            parentHash = header.getParentHash();
            coinbase = header.getCoinbase();
            stateRoot = header.getStateRoot();
            txTrieRoot = header.getTxTrieRoot();
            receiptTrieRoot = header.getReceiptsRoot();
            logsBloom = header.getLogsBloom();
            difficulty = header.getDifficulty();
            extraData = header.getExtraData();
            energyConsumed = header.getEnergyConsumed();
            energyLimit = header.getEnergyLimit();
            timestamp = header.getTimestamp();
            nonce = header.getNonce();
            solution = header.getSolution();

            return this;
        }
    }

    /**
     * For the stratum RPC getHeaderByBlockNumber
     *
     * @return a JSONObject represent part of the block header.
     */
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.putOpt("version", oneByteToHexString(sealType.getSealId())); // Legacy object for the pool
        obj.putOpt("sealType", oneByteToHexString(sealType.getSealId()));
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

    @Override
    public byte[] getParentHash() {
        return parentHash.clone();
    }

    public AionAddress getCoinbase() {
        return coinbase;
    }

    public byte[] getStateRoot() {
        return stateRoot.clone();
    }

    public byte[] getTxTrieRoot() {
        return txTrieRoot.clone();
    }

    public byte[] getReceiptsRoot() {
        return receiptTrieRoot.clone();
    }

    public byte[] getLogsBloom() {
        return logsBloom.clone();
    }

    public byte[] getDifficulty() {
        return difficulty.clone();
    }

    /**
     * @implNote when the difficulty data field exceed the system limit(16 bytes), this method will
     *     return BigInteger.ZERO for the letting the validate() in the AionDifficultyRule return
     *     false. The difficulty in the PoW blockchain should be always a positive value.
     * @see org.aion.zero.impl.valid.AionDifficultyRule.validate;
     * @return the difficulty as the BigInteger format.
     */
    @SuppressWarnings("JavadocReference")
    @Override
    public BigInteger getDifficultyBI() {
        return new BigInteger(1, difficulty);
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public long getNumber() {
        return number;
    }

    @Override
    public byte[] getExtraData() {
        return extraData.clone();
    }

    @Override
    public boolean isGenesis() {
        return number == 0;
    }

    @Override
    public BlockSealType getSealType() {
        return sealType;
    }

    @Override
    public long getEnergyConsumed() {
        return energyConsumed;
    }

    @Override
    public long getEnergyLimit() {
        return energyLimit;
    }

    private byte[] getHeaderForMining() {
        return merge(
            new byte[] {sealType.getSealId()},
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

    @Override
    public byte[] getHash() {
        if (headerHash == null) {
            headerHash = HashUtil.h256(getEncoded());
        }

        return headerHash.clone();
    }

    @Override
    public byte[] getEncoded() {
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        byte[] parentHash = RLP.encodeElement(this.parentHash);
        byte[] coinbase = RLP.encodeElement(this.coinbase.toByteArray());
        byte[] stateRoot = RLP.encodeElement(this.stateRoot);
        byte[] txTrieRoot = RLP.encodeElement(this.txTrieRoot);
        byte[] receiptTrieRoot = RLP.encodeElement(this.receiptTrieRoot);
        byte[] logsBloom = RLP.encodeElement(this.logsBloom);
        byte[] difficulty = RLP.encodeElement(this.difficulty);
        byte[] extraData = RLP.encodeElement(this.extraData);
        byte[] energyConsumed = RLP.encodeBigInteger(BigInteger.valueOf(this.energyConsumed));
        byte[] energyLimit = RLP.encodeBigInteger(BigInteger.valueOf(this.energyLimit));
        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));
        byte[] solution = RLP.encodeElement(this.solution);
        byte[] nonce = RLP.encodeElement(this.nonce);

        return RLP.encodeList(
                rlpEncodedSealType,
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

    @Override
    public String toString() {
        return "  hash="
                + toHexString(getHash())
                + "  Length: "
                + getHash().length
                + "\n"
                + "  sealType="
                + Integer.toHexString(sealType.getSealId())
                + "  Length: "
                + "\n"
                + "  number="
                + number
                + "\n"
                + "  parentHash="
                + toHexString(parentHash)
                + "  parentHash: "
                + parentHash.length
                + "\n"
                + "  coinbase="
                + coinbase.toString()
                + "  coinBase: "
                + coinbase.toByteArray().length
                + "\n"
                + "  stateRoot="
                + toHexString(stateRoot)
                + "  stateRoot: "
                + stateRoot.length
                + "\n"
                + "  txTrieHash="
                + toHexString(txTrieRoot)
                + "  txTrieRoot: "
                + txTrieRoot.length
                + "\n"
                + "  receiptsTrieHash="
                + toHexString(receiptTrieRoot)
                + "  receiptTrieRoot: "
                + receiptTrieRoot.length
                + "\n"
                + "  difficulty="
                + toHexString(difficulty)
                + "  difficulty: "
                + difficulty.length
                + "\n"
                + "  energyConsumed="
                + energyConsumed
                + "\n"
                + "  energyLimit="
                + energyLimit
                + "\n"
                + "  extraData="
                + toHexString(extraData)
                + "\n"
                + "  timestamp="
                + timestamp
                + " ("
                + longToDateTime(timestamp)
                + ")"
                + "\n"
                + "  nonce="
                + toHexString(nonce)
                + "\n"
                + "  solution="
                + toHexString(solution)
                + "\n";
    }
}
