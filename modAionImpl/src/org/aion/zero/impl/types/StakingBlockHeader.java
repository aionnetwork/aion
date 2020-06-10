package org.aion.zero.impl.types;

import com.google.common.annotations.VisibleForTesting;
import org.aion.base.ConstantUtil;
import org.aion.crypto.HashUtil;
import org.aion.crypto.vrf.VRF_Ed25519;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.rlp.SharedRLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;

import java.math.BigInteger;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.json.JSONObject;

import static org.aion.util.bytes.ByteUtil.*;
import static org.aion.util.time.TimeUtils.longToDateTime;

/** Represents a PoS block on a chain implementing Unity Consensus. */
public class StakingBlockHeader  implements BlockHeader {

    // Not used in the BlockHeader but been defined in rlpEncoded data.
    //private static final int RPL_BH_SEALTYPE = 0;

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
    private static final int RLP_BH_SEED_OR_PROOF = 13;
    private static final int RLP_BH_SIGNATURE = 14;
    private static final int RLP_BH_SIGNING_PUBLICKEY = 15;
    private static final Seal sealType = Seal.PROOF_OF_STAKE;
    private static final byte[] rlpEncodedSealType = RLP.encodeElement(new byte[] {StakingBlockHeader.sealType.getSealId()});

    /** The SHA3 256-bit hash of the parent block, in its entirety */
    private final ByteArrayWrapper parentHash;

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
    private final ByteArrayWrapper txTrieRoot;
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

    /** The hash of block template for signing */
    private byte[] mineHashBytes;
    /**
     * The seed of this block. It should be a verifiable signature of the seed/proof of the previous PoS
     * block.
     */
    private final byte[] seedOrProof;

    private final byte[] signingPublicKey;

    /**
     * A verifiable signature of the encoding of this block (without this field). The signer should
     * be the same signer as the signer of the seed.
     */
    private final byte[] signature;

    private ByteArrayWrapper headerHash;

    public static final int SIG_LENGTH = 64;
    public static final int SEED_LENGTH = 64;
    public static final int PROOF_LENGTH = VRF_Ed25519.PROOF_BYTES;
    public static final int PUBKEY_LENGTH = 32;
    
    public static final byte[] GENESIS_SEED = new byte[SEED_LENGTH];
    public static final byte[] DEFAULT_SIGNATURE = new byte[SIG_LENGTH];
    public static final byte[] DEFAULT_PROOF = new byte[PROOF_LENGTH];
    /**
     * private constructor. use builder to construct the header class.
     */
    public StakingBlockHeader(StakingBlockHeader.Builder builder) {
        this.coinbase = builder.coinbase;
        this.stateRoot = builder.stateRoot;
        this.txTrieRoot = ByteArrayWrapper.wrap(builder.txTrieRoot);
        this.receiptTrieRoot = builder.receiptTrieRoot;
        this.parentHash = ByteArrayWrapper.wrap(builder.parentHash);
        this.logsBloom = builder.logsBloom;
        this.difficulty = builder.difficulty;
        this.number = builder.number;
        this.timestamp = builder.timestamp;
        this.extraData = builder.extraData;
        this.energyConsumed = builder.energyConsumed;
        this.energyLimit = builder.energyLimit;
        this.seedOrProof = builder.seedOrProof;
        this.signature = builder.signature;
        this.signingPublicKey = builder.pubkey;
    }

    @Override
    public byte[] getEncoded() {
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        byte[] parentHash = RLP.encodeElement(this.parentHash.toBytes());
        byte[] coinbase = RLP.encodeElement(this.coinbase.toByteArray());
        byte[] stateRoot = RLP.encodeElement(this.stateRoot);
        byte[] txTrieRoot = RLP.encodeElement(this.txTrieRoot.toBytes());
        byte[] receiptTrieRoot = RLP.encodeElement(this.receiptTrieRoot);
        byte[] logsBloom = RLP.encodeElement(this.logsBloom);
        byte[] difficulty = RLP.encodeElement(this.difficulty);
        byte[] extraData = RLP.encodeElement(this.extraData);
        byte[] energyConsumed = RLP.encodeBigInteger(BigInteger.valueOf(this.energyConsumed));
        byte[] energyLimit = RLP.encodeBigInteger(BigInteger.valueOf(this.energyLimit));
        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));
        byte[] seedOrProof = RLP.encodeElement(this.seedOrProof);
        byte[] signature = RLP.encodeElement(this.signature);
        byte[] signingPublicKey = RLP.encodeElement(this.signingPublicKey);
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
                seedOrProof,
                signature,
                signingPublicKey);
    }


    public byte[] getSeedOrProof() {
        return seedOrProof.clone();
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    public byte[] getSigningPublicKey() {
        return signingPublicKey.clone();
    }

    /**
     * Get hash of the header bytes to mine a block
     *
     * @return Blake2b digest (32 bytes) of the raw header bytes.
     */
    public byte[] getMineHash() {
        if (mineHashBytes == null) {
            mineHashBytes = HashUtil.h256(merge(getHeaderForMining(), seedOrProof));
        }
        return mineHashBytes.clone();
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
        protected byte[] seedOrProof;
        protected byte[] signature;
        protected byte[] pubkey;

        /*
         * Builder parameters, not related to header data structure
         */
        boolean isFromUnsafeSource;
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
        /**
         * Indicates that the data is from an unsafe source
         *
         * @return {@code builder} same instance of builder
         */
        Builder fromUnsafeSource() {
            isFromUnsafeSource = true;
            return this;
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

        public Builder withTimestamp(byte[] timestamp) {
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

        Builder withNumber(byte[] number) {
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

        public Builder withSeed(byte[] seed) {
            if (isFromUnsafeSource) {
                if (seed == null) {
                    throw new NullPointerException("seed cannot be null");
                }

                if (seed.length != SEED_LENGTH) {
                    throw new IllegalArgumentException("invalid seed length");
                }
            }

            if (seedOrProof != null) {
                throw new IllegalStateException("the seed has been assigned");
            }

            this.seedOrProof = seed;
            return this;
        }

        public Builder withProof(byte[] proof) {
            if (isFromUnsafeSource) {
                if (proof == null) {
                    throw new NullPointerException("proof cannot be null");
                }

                if (proof.length != PROOF_LENGTH) {
                    throw new IllegalArgumentException("invalid proof length");
                }
            }

            if (seedOrProof != null) {
                throw new IllegalStateException("the proof has been assigned");
            }

            this.seedOrProof = proof;
            return this;
        }


        public Builder withSignature(byte[] signature) {
            if (isFromUnsafeSource) {
                if (signature == null) {
                    throw new NullPointerException("signature cannot be null");
                }

                if (signature.length != SIG_LENGTH) {
                    throw new IllegalArgumentException("invalid signature length");
                }
            }

            this.signature = signature;
            return this;
        }

        public Builder withSigningPublicKey(byte[] publicKey) {
            if (isFromUnsafeSource) {
                if (publicKey == null) {
                    throw new NullPointerException("signingPublicKey cannot be null");
                }

                if (publicKey.length != PUBKEY_LENGTH) {
                    throw new IllegalArgumentException("invalid signingPublicKey length");
                }
            }
            this.pubkey = publicKey;
            return this;
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
            byte[] data = rlpHeader.get(RLP_BH_SEED_OR_PROOF).getRLPData();
            if (data == null) {
                throw new IllegalArgumentException("the seed or proof data is missing");
            } else if (data.length == SEED_LENGTH) {
                withSeed(rlpHeader.get(RLP_BH_SEED_OR_PROOF).getRLPData());
            } else if (data.length == PROOF_LENGTH) {
                withProof(rlpHeader.get(RLP_BH_SEED_OR_PROOF).getRLPData());
            } else {
                throw new IllegalArgumentException("incorrect seed or proof length");
            }

            withSignature(rlpHeader.get(RLP_BH_SIGNATURE).getRLPData());
            withSigningPublicKey(rlpHeader.get(RLP_BH_SIGNING_PUBLICKEY).getRLPData());

            return this;
        }

        public Builder withRlpList(SharedRLPList rlpHeader) {
            if (rlpHeader == null) {
                throw new NullPointerException("rlpHeader can not be null");
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
            byte[] data = rlpHeader.get(RLP_BH_SEED_OR_PROOF).getRLPData();
            if (data == null) {
                throw new IllegalArgumentException("the seed or proof data is missing");
            } else if (data.length == SEED_LENGTH) {
                withSeed(rlpHeader.get(RLP_BH_SEED_OR_PROOF).getRLPData());
            } else if (data.length == PROOF_LENGTH) {
                withProof(rlpHeader.get(RLP_BH_SEED_OR_PROOF).getRLPData());
            } else {
                throw new IllegalArgumentException("incorrect seed or proof length");
            }

            withSignature(rlpHeader.get(RLP_BH_SIGNATURE).getRLPData());
            withSigningPublicKey(rlpHeader.get(RLP_BH_SIGNING_PUBLICKEY).getRLPData());

            return this;
        }

        public StakingBlockHeader build() {
            // Formalize the data
            if (parentHash == null) {
                throw new NullPointerException("the header parentHash is null");
            }

            if (coinbase == null) {
                throw new NullPointerException("the header coinbasae is null");
            }

            if (stateRoot == null) {
                throw new NullPointerException("the header stateRoot is null");
            }

            if (txTrieRoot == null) {
                throw new NullPointerException("the header txTrieRoot is null");
            }

            if (receiptTrieRoot == null) {
                throw new NullPointerException("the header receiptTrieRoot is null");
            }

            if (logsBloom == null) {
                throw new NullPointerException("the header logBloom is null");
            }

            if (difficulty == null) {
                throw new NullPointerException("the header difficulty is null");
            }

            if (extraData == null) {
                throw new NullPointerException("the header extraData is null");
            }

            if (seedOrProof == null) {
                throw new NullPointerException("the header seed or proof is null");
            }

            if (signature == null) {
                throw new NullPointerException("the header signature is null");
            }

            if (pubkey == null) {
                throw new NullPointerException("the header signing publicKey is null");
            }

            if (energyLimit < 0L) {
                throw new IllegalArgumentException("the header energyLimit can not lower than 0");
            }

            if (energyConsumed < 0L) {
                throw new IllegalArgumentException("the header energyConsumed can not lower than 0");
            }

            return new StakingBlockHeader(this);
        }

        public Builder withHeader(StakingBlockHeader header) {
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
            seedOrProof = header.getSeedOrProof();
            signature = header.getSignature();
            pubkey = header.getSigningPublicKey();

            return this;
        }

        public Builder withDefaultStateRoot() {
            stateRoot = ConstantUtil.EMPTY_TRIE_HASH;
            return this;
        }

        public Builder withDefaultReceiptTrieRoot() {
            receiptTrieRoot = ConstantUtil.EMPTY_TRIE_HASH;
            return this;
        }

        public Builder withDefaultTxTrieRoot() {
            txTrieRoot = ConstantUtil.EMPTY_TRIE_HASH;
            return this;
        }

        public Builder withDefaultLogsBloom() {
            logsBloom = EMPTY_BLOOM;
            return this;
        }

        public Builder withDefaultDifficulty() {
            difficulty = ByteUtil.EMPTY_HALFWORD;
            return this;
        }

        public Builder withDefaultParentHash() {
            parentHash = ByteUtil.EMPTY_WORD;
            return this;
        }

        public Builder withDefaultCoinbase() {
            coinbase = AddressUtils.ZERO_ADDRESS;
            return this;
        }

        public Builder withDefaultExtraData() {
            extraData = ByteUtil.EMPTY_WORD;
            return  this;
        }

        public Builder withDefaultSignature() {
            signature = DEFAULT_SIGNATURE;
            return  this;
        }

        public Builder withDefaultSigningPublicKey() {
            pubkey = ByteUtil.EMPTY_WORD;
            return  this;
        }

        public Builder withDefaultSeed() {
            seedOrProof = new byte[SEED_LENGTH];
            return  this;
        }

        public Builder withDefaultProof() {
            seedOrProof = DEFAULT_PROOF;
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
        obj.putOpt("parentHash", parentHash.toString());
        obj.putOpt("coinBase", toHexString(coinbase.toByteArray()));
        obj.putOpt("stateRoot", toHexString(stateRoot));
        obj.putOpt("txTrieRoot", txTrieRoot.toString());
        obj.putOpt("receiptTrieRoot", toHexString(receiptTrieRoot));
        obj.putOpt("logsBloom", toHexString(logsBloom));
        obj.putOpt("difficulty", toHexString(difficulty));
        obj.putOpt("extraData", toHexString(extraData));
        obj.putOpt("energyConsumed", toHexString(longToBytes(energyConsumed)));
        obj.putOpt("energyLimit", toHexString(longToBytes(energyLimit)));
        obj.putOpt("timestamp", toHexString(longToBytes(timestamp)));
        if (seedOrProof.length == SEED_LENGTH) {
            obj.putOpt("seed", toHexString(seedOrProof));
        } else if (seedOrProof.length == PROOF_LENGTH) {
            obj.putOpt("proof", toHexString(seedOrProof));
        } else {
            obj.putOpt("seedOrProofError", toHexString(seedOrProof));
        }

        return obj;
    }

    @Override
    public byte[] getParentHash() {
        return parentHash.toBytes();
    }

    @Override
    public ByteArrayWrapper getParentHashWrapper() {
        return parentHash;
    }

    public AionAddress getCoinbase() {
        return coinbase;
    }

    public byte[] getStateRoot() {
        return stateRoot.clone();
    }

    @Override
    public byte[] getTxTrieRoot() {
        return txTrieRoot.toBytes();
    }

    @Override
    public ByteArrayWrapper getTxTrieRootWrapper() {
        return txTrieRoot;
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

    public byte[] getExtraData() {
        return extraData.clone();
    }

    @Override
    public boolean isGenesis() {
        return number == 0;
    }

    @Override
    public Seal getSealType() {
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
            parentHash.toBytes(),
            coinbase.toByteArray(),
            stateRoot,
            txTrieRoot.toBytes(),
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
            headerHash = ByteArrayWrapper.wrap(HashUtil.h256(getEncoded()));
        }

        return headerHash.toBytes();
    }

    @Override
    public ByteArrayWrapper getHashWrapper() {
        if (headerHash == null) {
            headerHash = ByteArrayWrapper.wrap(HashUtil.h256(getEncoded()));
        }

        return headerHash;
    }

    @Override
    public String toString() {
        return "  hash="
            + getHashWrapper()
            + "  Length: "
            + getHashWrapper().length()
            + "\n"
            + "  sealType="
            + Integer.toHexString(sealType.getSealId())
            + "  Length: "
            + "\n"
            + "  number="
            + number
            + "\n"
            + "  parentHash="
            + parentHash
            + "  parentHash: "
            + parentHash.length()
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
            + txTrieRoot
            + "  txTrieRoot: "
            + txTrieRoot.length()
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
            + formatSeedOrProof(seedOrProof)
            + "  signature="
            + toHexString(signature)
            + "\n"
            + "  signingPublicKey="
            + toHexString(signingPublicKey)
            + "\n";
    }

    private static String formatSeedOrProof(byte[] seedOrProof) {
        String prefix;
        if (seedOrProof.length == SEED_LENGTH) {
            prefix = "  seed=";
        } else if (seedOrProof.length == PROOF_LENGTH) {
            prefix = "  proof=";
        } else {
            prefix = " invalid seed or proof=";
        }

        return prefix + toHexString(seedOrProof) + "\n";
    }
}
