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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */
package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.aion.zero.exceptions.HeaderStructureException;
import org.aion.zero.impl.AionGenesis;
import org.aion.crypto.HashUtil;
import org.junit.Test;

/**
 * Specifies the properties of AionGenesis and intended parameters, ensures that
 * the creation of AionGenesis (atleast through suggested codepaths) always
 * produce a valid genesis block.
 * 
 * @author yao
 *
 */
public class GenesisSpecificationTest {

    /**
     * Test that the default genesis block built from the builder produces the
     * correct genesis specs
     */
    @Test
    public void defaultGenesisBlockTest() throws HeaderStructureException {
        AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
        AionGenesis genesis = genesisBuilder.build();

        assertThat(genesis.getParentHash()).isEqualTo(AionGenesis.GENESIS_PARENT_HASH);
        assertThat(genesis.getCoinbase()).isEqualTo(AionGenesis.GENESIS_COINBASE);
        assertThat(genesis.getDifficulty()).isEqualTo(AionGenesis.GENESIS_DIFFICULTY);
        assertThat(genesis.getDifficultyBI()).isEqualTo(new BigInteger(1, AionGenesis.GENESIS_DIFFICULTY));
        assertThat(genesis.getLogBloom()).isEqualTo(AionGenesis.GENESIS_LOGSBLOOM);
        assertThat(genesis.getTimestamp()).isEqualTo(AionGenesis.GENESIS_TIMESTAMP);
        assertThat(genesis.getNrgConsumed()).isEqualTo(0);
        assertThat(genesis.getNrgLimit()).isEqualTo(AionGenesis.GENESIS_ENERGY_LIMIT);
        assertThat(genesis.getTxTrieRoot()).isEqualTo(HashUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getReceiptsRoot()).isEqualTo(HashUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getDifficultyBI()).isEqualTo(new BigInteger(1, AionGenesis.GENESIS_DIFFICULTY));
        assertThat(genesis.getTransactionsList().isEmpty()).isEqualTo(true);

        Map<Address, AccountState> premined = genesis.getPremine();
        Set<Address> keySet = premined.keySet();

        // default set
        Set<Address> defaultKeySet = AionGenesis.GENESIS_PREMINE.keySet();
        assertThat(defaultKeySet.equals(keySet)).isEqualTo(true);
    }

    /**
     * Test that the genesis block can be overrode by certain configuration
     * options that correspond to the options provided by
     * {@link AionGenesis.Builder}
     */
    @Test
    public void overrideGenesisBlockTest() throws HeaderStructureException {
        AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
        
        // values to override defaults with
        byte[] overrideHash = ByteUtil.hexStringToBytes("DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF");
        byte[] overrideAddress = ByteUtil.hexStringToBytes("DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF");
        BigInteger overrideValue = BigInteger.valueOf(1337);
        AccountState defaultAccountState = new AccountState(overrideValue, overrideValue);
        
        HashSet<Address> accountStateSet = new HashSet<>();
        accountStateSet.add(Address.wrap(overrideHash));
        
        genesisBuilder
            .withParentHash(overrideHash)
            .withCoinbase(Address.wrap(overrideAddress))
            .withDifficulty(overrideValue.toByteArray())
            .withEnergyLimit(overrideValue.longValue())
            .withNonce(overrideHash)
            .withNumber(overrideValue.longValue())
            .withTimestamp(overrideValue.longValue())
            .addPreminedAccount(Address.wrap(overrideAddress), defaultAccountState);

            AionGenesis genesis = genesisBuilder.build();
        
        assertThat(genesis.getParentHash()).isEqualTo(overrideHash);
        assertThat(genesis.getCoinbase().toBytes()).isEqualTo(overrideAddress);
        assertThat(genesis.getDifficulty()).isEqualTo(overrideValue.toByteArray());
        assertThat(genesis.getDifficultyBI()).isEqualTo(overrideValue);
        assertThat(genesis.getTimestamp()).isEqualTo(overrideValue.longValue());
        assertThat(genesis.getNrgConsumed()).isEqualTo(0);
        assertThat(genesis.getNrgLimit()).isEqualTo(overrideValue.longValue());
        assertThat(genesis.getTxTrieRoot()).isEqualTo(HashUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getReceiptsRoot()).isEqualTo(HashUtil.EMPTY_TRIE_HASH);
        assertThat(genesis.getDifficultyBI()).isEqualTo(overrideValue);
        assertThat(genesis.getTransactionsList().isEmpty()).isEqualTo(true);
        
        assertThat(genesis.getPremine().keySet()).isEqualTo(accountStateSet);
    }
}
