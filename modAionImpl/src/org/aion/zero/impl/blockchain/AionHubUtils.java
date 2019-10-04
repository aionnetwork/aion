package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Map;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.RepositoryCache;
import org.aion.util.types.DataWord;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionGenesis;
import org.aion.zero.impl.db.AionRepositoryImpl;

/** {@link AionHub} functionality where a full instantiation of the class is not desirable. */
public class AionHubUtils {

    public static void buildGenesis(AionGenesis genesis, AionRepositoryImpl repository) {
        // initialization section for network balance contract
        RepositoryCache track = repository.startTracking();

        AionAddress networkBalanceAddress = ContractInfo.TOTAL_CURRENCY.contractAddress;
        track.createAccount(networkBalanceAddress);
        // saving FVM type for networkBalance contract
        track.saveVmType(networkBalanceAddress, InternalVmType.FVM);

        for (Map.Entry<Integer, BigInteger> addr : genesis.getNetworkBalances().entrySet()) {
            // assumes only additions are performed in the genesis
            track.addStorageRow(
                    networkBalanceAddress,
                    new DataWord(addr.getKey()).toWrapper(),
                    wrapValueForPut(new DataWord(addr.getValue())));
        }

        for (AionAddress addr : genesis.getPremine().keySet()) {
            track.createAccount(addr);
            track.addBalance(addr, genesis.getPremine().get(addr).getBalance());
        }
        track.flush();

        repository.commitBlock(genesis.getHashWrapper(), genesis.getNumber(), genesis.getStateRoot());
        repository
                .getBlockStore()
                .saveBlock(
                        genesis,
                        genesis.getMiningDifficulty(),
                        genesis.getStakingDifficulty(),
                        true,
                        Long.MAX_VALUE);
    }

    private static ByteArrayWrapper wrapValueForPut(DataWord value) {
        return (value.isZero())
                ? value.toWrapper()
                : new ByteArrayWrapper(value.getNoLeadZeroesData());
    }
}
