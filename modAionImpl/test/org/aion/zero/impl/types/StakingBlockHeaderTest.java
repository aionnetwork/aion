package org.aion.zero.impl.types;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.types.MiningBlockHeader.Builder.EMPTY_BLOOM;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.aion.base.ConstantUtil;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.rlp.RLP;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.junit.Test;

public class StakingBlockHeaderTest {

    private byte[] PARENT_HASH = HashUtil.h256("parentHash".getBytes());
    private byte[] COINBASE = HashUtil.keccak256("coinbase".getBytes());
    private byte[] TRIE_ROOT = HashUtil.h256("trieRoot".getBytes());
    private byte[] RECEIPT_ROOT = HashUtil.h256("receiptRoot".getBytes());
    private byte[] STATE_ROOT = HashUtil.h256("stateRoot".getBytes());

    private byte[] EXTRA_DATA = HashUtil.h256("hello world".getBytes());

    private long NUMBER = 1;
    private byte[] NUMBER_BYTES = ByteUtil.longToBytes(NUMBER);
    private long ENERGY_CONSUMED = 100;
    private long ENERGY_LIMIT = 6700000;

    private byte[] ENERGY_CONSUMED_BYTES = ByteUtil.longToBytes(100);
    private byte[] ENERGY_LIMIT_BYTES = ByteUtil.longToBytes(6700000);

    // randomly selected
    private byte[] SEED =
            ByteUtil.merge(
                    HashUtil.h256(ByteUtil.longToBytes(42)),
                    HashUtil.h256(ByteUtil.longToBytes(43)));
    private byte[] SIGNINGPUBKEY = HashUtil.h256(ByteUtil.longToBytes(142));
    private byte[] SIGNATURE = ByteUtil.merge(SIGNINGPUBKEY, SIGNINGPUBKEY);


    @Test
    public void testBlockHeaderFromSafeBuilder() {
        long time = System.currentTimeMillis() / 1000;

        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        // partial build
        builder.withCoinbase(new AionAddress(COINBASE))
                .withStateRoot(STATE_ROOT)
                .withTxTrieRoot(TRIE_ROOT)
                .withExtraData(EXTRA_DATA)
                .withReceiptTrieRoot(RECEIPT_ROOT)
                .withTimestamp(time)
                .withNumber(NUMBER_BYTES)
                .withEnergyConsumed(ENERGY_CONSUMED_BYTES)
                .withEnergyLimit(ENERGY_LIMIT_BYTES)
                .withParentHash(PARENT_HASH)
                .withSeed(SEED)
                .withSignature(SIGNATURE)
                .withSigningPublicKey(SIGNINGPUBKEY)
                .withDefaultLogsBloom()
                .withDefaultDifficulty();
        StakingBlockHeader header = builder.build();

        assertThat(header.getCoinbase().toByteArray()).isEqualTo(COINBASE);
        assertThat(header.getStateRoot()).isEqualTo(STATE_ROOT);
        assertThat(header.getTxTrieRoot()).isEqualTo(TRIE_ROOT);
        assertThat(header.getExtraData()).isEqualTo(EXTRA_DATA);
        assertThat(header.getReceiptsRoot()).isEqualTo(RECEIPT_ROOT);
        assertThat(header.getTimestamp()).isEqualTo(time);
        assertThat(header.getNumber()).isEqualTo(NUMBER);
        assertThat(header.getEnergyConsumed()).isEqualTo(ENERGY_CONSUMED);
        assertThat(header.getEnergyLimit()).isEqualTo(ENERGY_LIMIT);
        assertThat(header.getSignature()).isEqualTo(SIGNATURE);
        assertThat(header.getSeedOrProof()).isEqualTo(SEED);
        assertThat(header.getSigningPublicKey()).isEqualTo(SIGNINGPUBKEY);
        assertThat(header.getSealType().equals(BlockSealType.SEAL_POW_BLOCK));
    }

    @Test
    public void testBlockHeaderFromUnsafeSource() {
        long time = System.currentTimeMillis() / 1000;

        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        // partial build
        builder.fromUnsafeSource()
                .withStateRoot(STATE_ROOT)
                .withCoinbase(new AionAddress(COINBASE))
                .withTxTrieRoot(TRIE_ROOT)
                .withExtraData(EXTRA_DATA)
                .withReceiptTrieRoot(RECEIPT_ROOT)
                .withTimestamp(time)
                .withNumber(NUMBER_BYTES)
                .withEnergyConsumed(ENERGY_CONSUMED_BYTES)
                .withEnergyLimit(ENERGY_LIMIT_BYTES)
                .withParentHash(PARENT_HASH)
                .withDefaultSignature()
                .withDefaultSigningPublicKey()
                .withDefaultSeed()
                .withDefaultLogsBloom()
                .withDefaultDifficulty();

        StakingBlockHeader header = builder.build();

        assertThat(header.getStateRoot()).isEqualTo(STATE_ROOT);
        assertThat(header.getCoinbase().toByteArray()).isEqualTo(COINBASE);
        assertThat(header.getTxTrieRoot()).isEqualTo(TRIE_ROOT);
        assertThat(header.getExtraData()).isEqualTo(EXTRA_DATA);
        assertThat(header.getReceiptsRoot()).isEqualTo(RECEIPT_ROOT);
        assertThat(header.getTimestamp()).isEqualTo(time);
        assertThat(header.getNumber()).isEqualTo(NUMBER);
        assertThat(header.getEnergyConsumed()).isEqualTo(ENERGY_CONSUMED);
        assertThat(header.getEnergyLimit()).isEqualTo(ENERGY_LIMIT);
        assertThat(header.getSeedOrProof()).isEqualTo(new byte[64]);
        assertThat(header.getSigningPublicKey()).isEqualTo(ByteUtil.EMPTY_WORD);
        assertThat(header.getSignature()).isEqualTo(new byte[64]);
        assertThat(header.getDifficulty()).isEqualTo(ByteUtil.EMPTY_HALFWORD);
        assertThat(header.getSealType().equals(BlockSealType.SEAL_POW_BLOCK));
        assertThat(header.getLogsBloom()).isEqualTo(EMPTY_BLOOM);
    }

    // Test is a self referencing
    @Test
    public void testBlockHeaderFromRLP() {
        long time = System.currentTimeMillis() / 1000;

        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();

        builder.fromUnsafeSource()
                .withCoinbase(new AionAddress(COINBASE))
                .withTxTrieRoot(TRIE_ROOT)
                .withExtraData(EXTRA_DATA)
                .withReceiptTrieRoot(RECEIPT_ROOT)
                .withTimestamp(time)
                .withNumber(NUMBER_BYTES)
                .withEnergyConsumed(ENERGY_CONSUMED_BYTES)
                .withEnergyLimit(ENERGY_LIMIT_BYTES)
                .withParentHash(PARENT_HASH)
                .withSeed(SEED)
                .withSigningPublicKey(SIGNINGPUBKEY)
                .withSignature(SIGNATURE)
                .withDefaultStateRoot()
                .withDefaultLogsBloom()
                .withDefaultDifficulty();
        StakingBlockHeader header = builder.build();
        byte[] encoded = header.getEncoded();

        StakingBlockHeader reconstructed = StakingBlockHeader.Builder.newInstance(true).withRlpEncodedData(encoded).build();
        assertThat(reconstructed.getCoinbase()).isEqualTo(header.getCoinbase());
        assertThat(reconstructed.getTxTrieRoot()).isEqualTo(header.getTxTrieRoot());
        assertThat(reconstructed.getExtraData()).isEqualTo(header.getExtraData());
        assertThat(reconstructed.getReceiptsRoot()).isEqualTo(header.getReceiptsRoot());
        assertThat(reconstructed.getTimestamp()).isEqualTo(header.getTimestamp());
        assertThat(reconstructed.getNumber()).isEqualTo(header.getNumber());
        assertThat(reconstructed.getEnergyConsumed()).isEqualTo(header.getEnergyConsumed());
        assertThat(reconstructed.getEnergyLimit()).isEqualTo(header.getEnergyLimit());
        assertThat(reconstructed.getParentHash()).isEqualTo(header.getParentHash());
        assertThat(reconstructed.getSeedOrProof()).isEqualTo(header.getSeedOrProof());
        assertThat(reconstructed.getSignature()).isEqualTo(header.getSignature());
        assertThat(reconstructed.getSigningPublicKey()).isEqualTo(header.getSigningPublicKey());
        assertThat(reconstructed.getStateRoot()).isEqualTo(header.getStateRoot());

        assertThat(reconstructed.getDifficulty()).isEqualTo(header.getDifficulty());
        assertThat(reconstructed.getSealType() == header.getSealType());
    }

    @Test
    public void testBlockHeaderFromRLPwithProof() {
        long time = System.currentTimeMillis() / 1000;

        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();

        builder.fromUnsafeSource()
            .withCoinbase(new AionAddress(COINBASE))
            .withTxTrieRoot(TRIE_ROOT)
            .withExtraData(EXTRA_DATA)
            .withReceiptTrieRoot(RECEIPT_ROOT)
            .withTimestamp(time)
            .withNumber(NUMBER_BYTES)
            .withEnergyConsumed(ENERGY_CONSUMED_BYTES)
            .withEnergyLimit(ENERGY_LIMIT_BYTES)
            .withParentHash(PARENT_HASH)
            .withProof(StakingBlockHeader.DEFAULT_PROOF)
            .withSigningPublicKey(SIGNINGPUBKEY)
            .withSignature(SIGNATURE)
            .withDefaultStateRoot()
            .withDefaultLogsBloom()
            .withDefaultDifficulty();
        StakingBlockHeader header = builder.build();
        byte[] encoded = header.getEncoded();

        StakingBlockHeader reconstructed = StakingBlockHeader.Builder.newInstance(true).withRlpEncodedData(encoded).build();
        assertThat(reconstructed.getCoinbase()).isEqualTo(header.getCoinbase());
        assertThat(reconstructed.getTxTrieRoot()).isEqualTo(header.getTxTrieRoot());
        assertThat(reconstructed.getExtraData()).isEqualTo(header.getExtraData());
        assertThat(reconstructed.getReceiptsRoot()).isEqualTo(header.getReceiptsRoot());
        assertThat(reconstructed.getTimestamp()).isEqualTo(header.getTimestamp());
        assertThat(reconstructed.getNumber()).isEqualTo(header.getNumber());
        assertThat(reconstructed.getEnergyConsumed()).isEqualTo(header.getEnergyConsumed());
        assertThat(reconstructed.getEnergyLimit()).isEqualTo(header.getEnergyLimit());
        assertThat(reconstructed.getParentHash()).isEqualTo(header.getParentHash());
        assertThat(reconstructed.getSeedOrProof()).isEqualTo(header.getSeedOrProof());
        assertThat(reconstructed.getSignature()).isEqualTo(header.getSignature());
        assertThat(reconstructed.getSigningPublicKey()).isEqualTo(header.getSigningPublicKey());
        assertThat(reconstructed.getStateRoot()).isEqualTo(header.getStateRoot());

        assertThat(reconstructed.getDifficulty()).isEqualTo(header.getDifficulty());
        assertThat(reconstructed.getSealType() == header.getSealType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBlockHeaderFromRLPwithInvalidSeedProofLength() {
        long time = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

        byte[] number = RLP.encodeBigInteger(new BigInteger(1, NUMBER_BYTES));
        byte[] parentHash = RLP.encodeElement(PARENT_HASH);
        byte[] coinbase = RLP.encodeElement(new AionAddress(COINBASE).toByteArray());
        byte[] stateRoot = RLP.encodeElement(ConstantUtil.EMPTY_TRIE_HASH);
        byte[] txTrieRoot = RLP.encodeElement(TRIE_ROOT);
        byte[] receiptTrieRoot = RLP.encodeElement(RECEIPT_ROOT);
        byte[] logsBloom = RLP.encodeElement(EMPTY_BLOOM);
        byte[] difficulty = RLP.encodeElement(ByteUtil.EMPTY_HALFWORD);
        byte[] extraData = RLP.encodeElement(EXTRA_DATA);
        byte[] energyConsumed = RLP.encodeBigInteger(new BigInteger(1, ENERGY_CONSUMED_BYTES));
        byte[] energyLimit = RLP.encodeBigInteger(new BigInteger(1, ENERGY_LIMIT_BYTES));
        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(time));
        byte[] seedOrProof = RLP.encodeElement(new byte[StakingBlockHeader.PROOF_LENGTH + 1]);
        byte[] signature = RLP.encodeElement(SIGNATURE);
        byte[] signingPublicKey = RLP.encodeElement(SIGNINGPUBKEY);
        byte[] sealType = RLP.encodeElement(new byte[] {BlockSealType.SEAL_POS_BLOCK.getSealId()});

        byte[] rlpEncoded =  RLP.encodeList(
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
            seedOrProof,
            signature,
            signingPublicKey);

        StakingBlockHeader reconstructed = StakingBlockHeader.Builder.newInstance(true).withRlpEncodedData(rlpEncoded).build();
    }

    // verification tests, test that no properties are being violated
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSeedLength() {
        byte[] invalidSeedLength = new byte[StakingBlockHeader.SEED_LENGTH - 1];
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSeed(invalidSeedLength);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSeedLength2() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSeed(StakingBlockHeader.DEFAULT_PROOF);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateSeedProofAssign() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSeed(StakingBlockHeader.GENESIS_SEED);
        builder.withSeed(StakingBlockHeader.GENESIS_SEED);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateSeedProofAssign2() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSeed(StakingBlockHeader.GENESIS_SEED);
        builder.withProof(StakingBlockHeader.DEFAULT_PROOF);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateSeedProofAssign3() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withProof(StakingBlockHeader.DEFAULT_PROOF);
        builder.withSeed(StakingBlockHeader.GENESIS_SEED);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateSeedProofAssign4() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withProof(StakingBlockHeader.DEFAULT_PROOF);
        builder.withProof(StakingBlockHeader.DEFAULT_PROOF);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidProofLength() {
        byte[] invalidSeedLength = new byte[StakingBlockHeader.PROOF_LENGTH - 1];
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withProof(invalidSeedLength);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidProofLength2() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withProof(StakingBlockHeader.GENESIS_SEED);
    }

    @Test(expected = NullPointerException.class)
    public void testInvalidSeedNull() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSeed(null);
    }

    @Test(expected = NullPointerException.class)
    public void testInvalidProofNull() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withProof(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSignatureLength() {
        byte[] invalidSignature = new byte[65];
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSignature(invalidSignature);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSignatureLength2() {
        byte[] invalidSignature = new byte[63];
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSignature(invalidSignature);
    }

    @Test(expected = NullPointerException.class)
    public void testInvalidSignatureNull() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSignature(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPubkeyLength() {
        byte[] invalidPubkey = new byte[33];
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSigningPublicKey(invalidPubkey);
    }

    @Test(expected = NullPointerException.class)
    public void testInvalidPubkeyNull() {
        StakingBlockHeader.Builder builder = StakingBlockHeader.Builder.newInstance();
        builder.fromUnsafeSource();
        builder.withSigningPublicKey(null);
    }
}
