package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.aion.crypto.ECKey;
import org.aion.db.impl.SystemExitCodes;
import org.aion.zero.impl.types.Block;
import org.aion.types.AionAddress;
import org.aion.zero.impl.core.ImportResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class StandaloneBlockchainTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testStandaloneBlockchainGenerateAccounts() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withDefaultAccounts().build();

        assertThat(bundle.bc).isNotNull();
        assertThat(bundle.privateKeys).isNotNull();
        assertThat(bundle.privateKeys.size()).isEqualTo(10);

        for (ECKey k : bundle.privateKeys) {
            assertThat(bundle.bc.getRepository().getBalance(new AionAddress(k.getAddress())))
                    .isGreaterThan(BigInteger.ZERO);
        }
    }

    @Test
    public void testBlockChainShutdownhook() {
        exit.expectSystemExitWithStatus(SystemExitCodes.NORMAL);

        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        StandaloneBlockchain sb = bundle.bc;

        AionBlockchainImpl.shutdownHook = 2;
        Block block1 = sb.createBlock(sb.genesis, Collections.EMPTY_LIST, false, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        ImportResult result = sb.tryToConnect(block1);
        assertThat(result.isBest()).isTrue();

        Block block2 = sb.createBlock(block1, Collections.EMPTY_LIST, false, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        // expect the system will be shutdown when import the block2
        sb.tryToConnect(new BlockWrapper(block2));
    }
}
