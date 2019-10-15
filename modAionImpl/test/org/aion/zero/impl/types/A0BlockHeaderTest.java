package org.aion.zero.impl.types;

import static com.google.common.truth.Truth.assertThat;

import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.junit.Test;

public class A0BlockHeaderTest {

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
    private byte[] NONCE_BYTES = HashUtil.h256(ByteUtil.longToBytes(42));

    @Test
    public void testBlockHeaderFromSafeBuilder() {
        long time = System.currentTimeMillis() / 1000;

        A0BlockHeader.Builder builder = A0BlockHeader.Builder.newInstance();
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
                .withNonce(NONCE_BYTES)
                .withDefaultLogsBloom()
                .withDefaultDifficulty()
                .withDefaultSolution();

        A0BlockHeader header = builder.build();

        assertThat(header.getCoinbase().toByteArray()).isEqualTo(COINBASE);
        assertThat(header.getStateRoot()).isEqualTo(STATE_ROOT);
        assertThat(header.getTxTrieRoot()).isEqualTo(TRIE_ROOT);
        assertThat(header.getExtraData()).isEqualTo(EXTRA_DATA);
        assertThat(header.getReceiptsRoot()).isEqualTo(RECEIPT_ROOT);
        assertThat(header.getTimestamp()).isEqualTo(time);
        assertThat(header.getNumber()).isEqualTo(NUMBER);
        assertThat(header.getEnergyConsumed()).isEqualTo(ENERGY_CONSUMED);
        assertThat(header.getEnergyLimit()).isEqualTo(ENERGY_LIMIT);
        assertThat(header.getSolution()).isEqualTo(new byte[1408]);
        assertThat(header.getNonce()).isEqualTo(NONCE_BYTES);
        assertThat(header.getSealType().equals(BlockSealType.SEAL_POW_BLOCK));
    }

    @Test
    public void testBlockHeaderFromUnsafeSource() {
        long time = System.currentTimeMillis() / 1000;

        A0BlockHeader.Builder builder = A0BlockHeader.Builder.newInstance(true);
        // partial build
        builder.withStateRoot(STATE_ROOT)
                .withCoinbase(new AionAddress(COINBASE))
                .withTxTrieRoot(TRIE_ROOT)
                .withExtraData(EXTRA_DATA)
                .withReceiptTrieRoot(RECEIPT_ROOT)
                .withTimestamp(time)
                .withNumber(NUMBER_BYTES)
                .withEnergyConsumed(ENERGY_CONSUMED_BYTES)
                .withEnergyLimit(ENERGY_LIMIT_BYTES)
                .withParentHash(PARENT_HASH)
                .withDefaultLogsBloom()
                .withDefaultDifficulty()
                .withDefaultNonce()
                .withDefaultSolution();

        A0BlockHeader header = builder.build();

        assertThat(header.getStateRoot()).isEqualTo(STATE_ROOT);
        assertThat(header.getCoinbase().toByteArray()).isEqualTo(COINBASE);
        assertThat(header.getTxTrieRoot()).isEqualTo(TRIE_ROOT);
        assertThat(header.getExtraData()).isEqualTo(EXTRA_DATA);
        assertThat(header.getReceiptsRoot()).isEqualTo(RECEIPT_ROOT);
        assertThat(header.getTimestamp()).isEqualTo(time);
        assertThat(header.getNumber()).isEqualTo(NUMBER);
        assertThat(header.getEnergyConsumed()).isEqualTo(ENERGY_CONSUMED);
        assertThat(header.getEnergyLimit()).isEqualTo(ENERGY_LIMIT);
        assertThat(header.getSolution()).isEqualTo(new byte[1408]);
        assertThat(header.getNonce()).isEqualTo(ByteUtil.EMPTY_WORD);
        assertThat(header.getDifficulty()).isEqualTo(ByteUtil.EMPTY_HALFWORD);
        assertThat(header.getSealType().equals(BlockSealType.SEAL_POW_BLOCK));
    }

    // Test is a self referencing
    @Test
    public void testBlockHeaderFromRLP() {
        long time = System.currentTimeMillis() / 1000;

        A0BlockHeader.Builder builder = A0BlockHeader.Builder.newInstance(true);

        builder.withCoinbase(new AionAddress(COINBASE))
                .withTxTrieRoot(TRIE_ROOT)
                .withExtraData(EXTRA_DATA)
                .withReceiptTrieRoot(RECEIPT_ROOT)
                .withTimestamp(time)
                .withNumber(NUMBER_BYTES)
                .withEnergyConsumed(ENERGY_CONSUMED_BYTES)
                .withEnergyLimit(ENERGY_LIMIT_BYTES)
                .withParentHash(PARENT_HASH)
                .withNonce(NONCE_BYTES)
                .withDefaultLogsBloom()
                .withDefaultDifficulty()
                .withDefaultSolution()
                .withDefaultStateRoot();

        A0BlockHeader header = builder.build();
        byte[] encoded = header.getEncoded();

        A0BlockHeader reconstructed = A0BlockHeader.Builder.newInstance(true).withRlpEncodedData(encoded).build();
        assertThat(reconstructed.getCoinbase()).isEqualTo(header.getCoinbase());
        assertThat(reconstructed.getTxTrieRoot()).isEqualTo(header.getTxTrieRoot());
        assertThat(reconstructed.getExtraData()).isEqualTo(header.getExtraData());
        assertThat(reconstructed.getReceiptsRoot()).isEqualTo(header.getReceiptsRoot());
        assertThat(reconstructed.getTimestamp()).isEqualTo(header.getTimestamp());
        assertThat(reconstructed.getNumber()).isEqualTo(header.getNumber());
        assertThat(reconstructed.getEnergyConsumed()).isEqualTo(header.getEnergyConsumed());
        assertThat(reconstructed.getEnergyLimit()).isEqualTo(header.getEnergyLimit());
        assertThat(reconstructed.getParentHash()).isEqualTo(header.getParentHash());
        assertThat(reconstructed.getNonce()).isEqualTo(header.getNonce());
        assertThat(reconstructed.getDifficulty()).isEqualTo(header.getDifficulty());
        assertThat(reconstructed.getSealType() == header.getSealType());

        byte[] difficulty = reconstructed.getDifficulty();
    }

    // verification tests, test that no properties are being violated

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidNonceLong() {
        byte[] invalidNonceLength = new byte[33];
        A0BlockHeader.Builder builder = A0BlockHeader.Builder.newInstance(true);
        builder.withNonce(invalidNonceLength);
    }

    @Test(expected = NullPointerException.class)
    public void testInvalidNonceNull() {
        A0BlockHeader.Builder builder = A0BlockHeader.Builder.newInstance(true);
        builder.withNonce(null);
    }
}
