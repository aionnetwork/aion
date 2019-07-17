package org.aion.zero.types;

import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.exceptions.HeaderStructureException;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;

import java.math.BigInteger;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.util.bytes.ByteUtil.*;

/** Represents a PoS block on a chain implementing Unity Consensus. */
public class StakedBlockHeader extends AbstractBlockHeader implements BlockHeader {

    // 0 ~ 12 has been defined in the AbstractClass
    private static final int RLP_BH_SEED = 13, RLP_BH_SIGNATURE = 14, RLP_BH_PUBKEY = 15;

    /*
     * The seed of this block. It should be a verifiable signature of the seed of the previous PoS block.
     */
    private byte[] seed;

    private byte[] pubkey;

    /*
     * A verifiable signature of the encoding of this block (without this field).
     * The signer should be the same signer as the signer of the seed.
     */
    protected byte[] signature;

    public StakedBlockHeader(byte[] encoded) {
        this((RLPList) RLP.decode2(encoded).get(0));
    }

    public StakedBlockHeader(RLPList rlpHeader) {

        constructCommonHeader(rlpHeader);

        signature = rlpHeader.get(RLP_BH_SIGNATURE).getRLPData();
        seed = rlpHeader.get(RLP_BH_SEED).getRLPData();
        pubkey = rlpHeader.get(RLP_BH_PUBKEY).getRLPData();
    }

    /**
     * Copy constructor
     *
     * @param toCopy Block header to copy
     */
    public StakedBlockHeader(StakedBlockHeader toCopy) {

        copyCommonHeader(toCopy);

        signature = new byte[toCopy.getSignature().length];
        System.arraycopy(toCopy.getSignature(), 0, signature, 0, signature.length);

        seed = new byte[toCopy.getSeed().length];
        System.arraycopy(toCopy.getSeed(), 0, seed, 0, seed.length);

        pubkey = new byte[toCopy.getPubKey().length];
        System.arraycopy(toCopy.getSeed(), 0, pubkey, 0, pubkey.length);
    }

    public StakedBlockHeader(
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
            byte[] _seed,
            byte[] _signature,
            byte[] _pubkey) {
        super(
                _sealType,
                _number,
                _parentHash,
                _coinbase,
                null,
                null,
                null,
                _logsBloom,
                _difficulty,
                _extraData,
                _energyConsumed,
                _energyLimit,
                _timestamp);

        seed = _seed;
        signature = _signature;
        pubkey = _pubkey;
    }

    public StakedBlockHeader(
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
            byte[] _seed,
            byte[] _signature,
            byte[] _pubkey) {

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

        seed = _seed;
        signature = _signature;
        pubkey = _pubkey;
    }

    public byte[] getEncodedWithoutSignature() {
        return this.getEncoded(false);
    }

    public byte[] getEncoded(boolean withSignature) {

        byte[] rlpVersion = RLP.encodeElement(new byte[] {sealType});
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

        byte[] rlpSeed = RLP.encodeElement(seed);

        if (withSignature) {
            byte[] rlpSignature = RLP.encodeElement(signature);
            byte[] rlpPubkey = RLP.encodeElement(pubkey);
            return RLP.encodeList(
                    rlpVersion,
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
                    rlpSeed,
                    rlpSignature,
                    rlpPubkey);
        } else {
            return RLP.encodeList(
                    rlpVersion,
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
                    rlpEnergyConsumed,
                    rlpEnergyLimit,
                    rlpSeed);
        }
    }

    public String toStringWithSuffix(final String suffix) {

        return commonDataToStringWithSuffix(suffix)
                + "  seed="
                + toHexString(seed)
                + suffix
                + "  signature="
                + toHexString(signature)
                + suffix
                + "  publicKey="
                + toHexString(pubkey)
                + suffix;
    }

    public byte[] getSeed() {
        return seed;
    }

    public void setSeed(byte[] _seed) {
        seed = _seed;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] _signature) {
        signature = _signature;
    }

    public byte[] getPubKey() {
        return pubkey;
    }

    public void setPubKey(byte[] _pubkey) {
        pubkey = _pubkey;
    }

    /**
     * Get hash of the header bytes to mine a block
     *
     * @return Blake2b digest (32 bytes) of the raw header bytes.
     */
    public byte[] getMineHash() {
        if (mineHashBytes == null) {
            mineHashBytes = HashUtil.h256(merge(getHeaderForMine(), seed));
        }
        return mineHashBytes;
    }

    public static StakedBlockHeader fromRLP(byte[] rawData, boolean isUnsafe) throws Exception {
        return fromRLP((RLPList) RLP.decode2(rawData).get(0), isUnsafe);
    }

    /**
     * Construct a block header from RLP decoded data
     *
     * @param rlpHeader the rlpDecoded block header data
     * @param isUnsafe construct header from the unsafe data source
     * @return the StakedBlockHeader
     */
    public static StakedBlockHeader fromRLP(RLPList rlpHeader, boolean isUnsafe) throws Exception {
        Builder builder = new Builder();
        if (isUnsafe) {
            builder.fromUnsafeSource();
        }

        return builder.withRlpCommonHeader(rlpHeader)
                .withSeed(rlpHeader.get(RLP_BH_SEED).getRLPData())
                .withSignature(rlpHeader.get(RLP_BH_SIGNATURE).getRLPData())
                .withPubKey(rlpHeader.get(RLP_BH_PUBKEY).getRLPData())
                .build();
    }

    /** Builder used to introduce blocks into system that come from unsafe sources */
    public static class Builder extends AbstractBuilder {
        protected byte[] seed;
        protected byte[] signature;
        protected byte[] pubkey;

        /*
         * Builder parameters, not related to header data structure
         */
        private static int SIG_LENGTH = 64;
        private static int SEED_LENGTH = 64;
        private static int PUBKEY_LENGTH = 32;

        public Builder fromUnsafeSource() {
            return (Builder) super.fromUnsafeSource();
        }

        Builder withRlpCommonHeader(RLPList rlpHeader) throws HeaderStructureException {
            return (Builder) super.withRlpHeader(rlpHeader);
        }

        public Builder withSealType(byte _sealType) throws HeaderStructureException {
            return (Builder) super.withSealType(_sealType);
        }

        public Builder withParentHash(byte[] _parentHash) throws HeaderStructureException {
            return (Builder) super.withParentHash(_parentHash);
        }

        public Builder withCoinbase(AionAddress _coinbase) throws HeaderStructureException {
            return (Builder) super.withCoinbase(_coinbase);
        }

        public Builder withStateRoot(byte[] _stateRoot) throws HeaderStructureException {
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

        public Builder withEnergyLimit(byte[] _energyLimit) throws HeaderStructureException {
            return (Builder) super.withEnergyLimit(_energyLimit);
        }

        public Builder withEnergyLimit(long _energyLimit) throws HeaderStructureException {
            return (Builder) super.withEnergyLimit(_energyLimit);
        }

        public Builder withSeed(byte[] _seed) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_seed == null)
                    throw new HeaderStructureException("seed", RLP_BH_SEED, "cannot be null");

                if (_seed.length != SEED_LENGTH) {
                    throw new HeaderStructureException("seed", RLP_BH_SEED, "invalid seed length");
                }
            }
            seed = _seed;
            return this;
        }

        public Builder withSignature(byte[] _signature) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_signature == null)
                    throw new HeaderStructureException(
                            "signature", RLP_BH_SIGNATURE, "cannot be null");

                // TODO: Maybe let this be empty so that stakers can use it to create pre-signed
                // blocks?
                if (_signature.length != SIG_LENGTH) {
                    throw new HeaderStructureException(
                            "signature", RLP_BH_SIGNATURE, "invalid signature length");
                }
            }

            signature = _signature;
            return this;
        }

        public Builder withPubKey(byte[] _pubkey) throws HeaderStructureException {
            if (isFromUnsafeSource) {
                if (_pubkey == null)
                    throw new HeaderStructureException("pubkey", RLP_BH_PUBKEY, "cannot be null");

                if (_pubkey.length != PUBKEY_LENGTH) {
                    throw new HeaderStructureException(
                            "pubkey", RLP_BH_PUBKEY, "invalid seed length");
                }
            }
            pubkey = _pubkey;
            return this;
        }

        public StakedBlockHeader build() {

            // Formalize the data
            sealType = (byte) BlockSealType.SEAL_POS_BLOCK.ordinal();
            parentHash = parentHash == null ? HashUtil.EMPTY_DATA_HASH : parentHash;
            coinbase = coinbase == null ? AddressUtils.ZERO_ADDRESS : coinbase;
            stateRoot = stateRoot == null ? HashUtil.EMPTY_TRIE_HASH : stateRoot;
            txTrieRoot = txTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : txTrieRoot;
            receiptTrieRoot = receiptTrieRoot == null ? HashUtil.EMPTY_TRIE_HASH : receiptTrieRoot;
            logsBloom = logsBloom == null ? EMPTY_BLOOM : logsBloom;
            difficulty = difficulty == null ? ByteUtil.EMPTY_HALFWORD : difficulty;
            extraData = extraData == null ? ByteUtil.EMPTY_WORD : extraData;
            seed = seed == null ? new byte[SEED_LENGTH] : seed;
            signature = signature == null ? new byte[SIG_LENGTH] : signature;
            pubkey = pubkey == null ? new byte[PUBKEY_LENGTH] : pubkey;

            return new StakedBlockHeader(
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
                    seed,
                    signature,
                    pubkey);
        }
    }
}
