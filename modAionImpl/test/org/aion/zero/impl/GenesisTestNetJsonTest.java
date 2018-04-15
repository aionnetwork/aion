package org.aion.zero.impl;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.GenesisBlockLoader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class GenesisTestNetJsonTest {

    // if we're running in the module itself
    private static final String TESTNET_DEFAULT_LOCATION = "../modBoot/resource/testnet.json";

    // if we're running in the project folder
    private static final String TESTNET_ALT_LOCATION = "./modBoot/resource/testnet.json";

    private static AionGenesis genesis;

    @BeforeClass
    public static void loadFile() {
        // load the genesis file through the genesis loader
        // try to load the file from two places, depending on where
        // we run the test from
        try {
            genesis = GenesisBlockLoader.loadJSON(TESTNET_DEFAULT_LOCATION);
        } catch (IOException e) {
            System.out.println(String.format("Failed to load genesis from: %s", TESTNET_DEFAULT_LOCATION));
            System.out.println("trying alternative");
            try {
                genesis = GenesisBlockLoader.loadJSON(TESTNET_ALT_LOCATION);
            } catch (IOException e2) {
                System.out.println("Failed to load from alternative location");
                e2.printStackTrace();
            }
        }
    }

    @Test
    public void testNetJsonLoad() throws Exception {
        if (genesis == null)
            throw new Exception("did not properly load genesis file");

        Map<Address, AccountState> premineAccounts = genesis.getPremine();

        Address[] accString = new Address[]{
                Address.wrap("0xa0483412e8c8e769df037231d336e96f7f6d843cf7224c3d8fbe0ec7cdc12ac6"),
                Address.wrap("0xa0353561f8b6b5a8b56d535647a4ddd7278e80c2494e3314d1f0605470f56925"),
                Address.wrap("0xa0274c1858ca50576f4d4d18b719787b76bb454c33749172758a620555bf4586"),
                Address.wrap("0xa06691a968e8fe22dc79b6dd503d44cb96d9a523ae265b63c6752389be90185d"),
                Address.wrap("0xa0a95b372efe55c77a75364407f0403dfefd3131519ca980b2d92b1d2d7297a7"),
                Address.wrap("0xa07262e1d026fca027e5f4f38668303b74a2bba8bd07470fa39d0f3b03f882f1"),
                Address.wrap("0xa08f92c80e34c95b8b2e8cb208d47e3e6048b7e0ea44d866e33e865c365b1417"),
                Address.wrap("0xa022045f22772463c94e08d1fd32f7b251d4c9cfd1c92e039f1517b906776283"),
                Address.wrap("0xa04c282f636feff4d6b35174fc2d8e05c23df4b1d59508712712627184dd8a93"),
                Address.wrap("0xa0f8654c63ae53598cf42435f53b1ebd9b7df6cbceba10af235aa2393f03034c"),
                Address.wrap("0xa0f3defb01b531c5a28680eb928e79ea18bc155f1060e1d923d660d74883518b")
        };

        Address tokenAddress = new Address("0xa02198c9192bb89e9b2e8536d96cbf287fde80625afcf1a5e5632e23c1260d61");

        for (Address a : accString) {
            assertThat(premineAccounts.containsKey(a)).isTrue();
            AccountState acc = premineAccounts.get(a);

            assertThat(acc).isNotNull();
            assertThat(acc.getBalance()).isEqualTo(new BigInteger("314159000000000000000000"));
        }

        assertThat(premineAccounts.containsKey(tokenAddress)).isTrue();
        AccountState tokenAcc = premineAccounts.get(tokenAddress);
        assertThat(tokenAcc.getBalance()).isEqualTo(new BigInteger("465934586660000000000000000"));

        // assert that the loaded fields are correct
        assertThat(genesis.getChainId()).isEqualTo(2);
        assertThat(genesis.getDifficultyBI()).isEqualTo(BigInteger.valueOf(16));
        assertThat(genesis.getCoinbase()).isEqualTo(new Address("0x0000000000000000000000000000000000000000000000000000000000000000"));
        assertThat(genesis.getTimestamp()).isEqualTo(1497536993L);
        assertThat(genesis.getParentHash()).isEqualTo(ByteUtil.hexStringToBytes("0x0000000000000000000000000000000000000000000000000000000000000000"));
        assertThat(genesis.getNrgLimit()).isEqualTo(10000000L);
        assertThat(genesis.getNrgConsumed()).isEqualTo(0L);
    }
}
