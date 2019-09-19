package org.aion.zero.impl.types;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.aion.base.ConstantUtil;
import org.aion.base.AccountState;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.junit.Test;

/**
 * Specifies the properties of AionGenesis and intended parameters, ensures that the creation of
 * AionGenesis (atleast through suggested codepaths) always produce a valid genesis block.
 *
 * @author yao
 */
public class GenesisSpecificationTest {

    /**
     * Test that the default genesis block built from the builder produces the correct genesis specs
     */
    @Test
    public void defaultGenesisBlockTest() {
        AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
        AionGenesis genesis = genesisBuilder.buildForTest();

        assertThat(genesis.getParentHash()).isEqualTo(AionGenesis.GENESIS_PARENT_HASH);
        assertThat(genesis.getCoinbase()).isEqualTo(AionGenesis.GENESIS_COINBASE);
        assertThat(genesis.getDifficulty()).isEqualTo(AionGenesis.GENESIS_DIFFICULTY);
        assertThat(genesis.getDifficultyBI())
                .isEqualTo(new BigInteger(1, AionGenesis.GENESIS_DIFFICULTY));
        assertThat(genesis.getLogBloom()).isEqualTo(AionGenesis.GENESIS_LOGSBLOOM);
        assertThat(genesis.getTimestamp()).isEqualTo(AionGenesis.GENESIS_TIMESTAMP);
        assertThat(genesis.getNrgConsumed()).isEqualTo(0);
        assertThat(genesis.getNrgLimit()).isEqualTo(AionGenesis.GENESIS_ENERGY_LIMIT);
        assertThat(genesis.getTxTrieRoot()).isEqualTo(ConstantUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getReceiptsRoot()).isEqualTo(ConstantUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getDifficultyBI())
                .isEqualTo(new BigInteger(1, AionGenesis.GENESIS_DIFFICULTY));
        assertThat(genesis.getTransactionsList().isEmpty()).isEqualTo(true);

        Map<AionAddress, AccountState> premined = genesis.getPremine();
        Set<AionAddress> keySet = premined.keySet();

        // default set
        Set<AionAddress> defaultKeySet = AionGenesis.GENESIS_PREMINE.keySet();
        assertThat(defaultKeySet.equals(keySet)).isEqualTo(true);
    }

    /**
     * Test that the genesis block can be overrode by certain configuration options that correspond
     * to the options provided by {@link AionGenesis.Builder}
     */
    @Test
    public void overrideGenesisBlockTest() {
        AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();

        // values to override defaults with
        byte[] overrideHash =
                ByteUtil.hexStringToBytes(
                        "DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF");
        byte[] overrideAddress =
                ByteUtil.hexStringToBytes(
                        "DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF");
        BigInteger overrideValue = BigInteger.valueOf(1337);
        AccountState defaultAccountState = new AccountState(overrideValue, overrideValue);

        HashSet<AionAddress> accountStateSet = new HashSet<>();
        accountStateSet.add(new AionAddress(overrideHash));

        genesisBuilder
                .withParentHash(overrideHash)
                .withCoinbase(new AionAddress(overrideAddress))
                .withDifficulty(overrideValue.toByteArray())
                .withEnergyLimit(overrideValue.longValue())
                .withNonce(overrideHash)
                .withNumber(overrideValue.longValue())
                .withTimestamp(overrideValue.longValue())
                .addPreminedAccount(new AionAddress(overrideAddress), defaultAccountState);

        AionGenesis genesis = genesisBuilder.buildForTest();

        assertThat(genesis.getParentHash()).isEqualTo(overrideHash);
        assertThat(genesis.getCoinbase().toByteArray()).isEqualTo(overrideAddress);
        assertThat(genesis.getDifficulty()).isEqualTo(overrideValue.toByteArray());
        assertThat(genesis.getDifficultyBI()).isEqualTo(overrideValue);
        assertThat(genesis.getTimestamp()).isEqualTo(overrideValue.longValue());
        assertThat(genesis.getNrgConsumed()).isEqualTo(0);
        assertThat(genesis.getNrgLimit()).isEqualTo(overrideValue.longValue());
        assertThat(genesis.getTxTrieRoot()).isEqualTo(ConstantUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getReceiptsRoot()).isEqualTo(ConstantUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getDifficultyBI()).isEqualTo(overrideValue);
        assertThat(genesis.getTransactionsList().isEmpty()).isEqualTo(true);

        assertThat(genesis.getPremine().keySet()).isEqualTo(accountStateSet);
    }
}
