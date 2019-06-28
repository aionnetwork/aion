package org.aion.precompiled.contracts.ATB;

import java.util.ArrayList;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.HashUtil;
import org.aion.util.types.AddressUtils;

public class BridgeTestUtils {
    static PrecompiledTransactionContext dummyContext() {
        return context(AddressUtils.ZERO_ADDRESS, AddressUtils.ZERO_ADDRESS, new byte[0]);
    }

    static PrecompiledTransactionContext context(AionAddress from, AionAddress to, byte[] txData) {
        final byte[] transactionHash = HashUtil.h256("transaction".getBytes());
        final AionAddress address = to;
        final AionAddress origin = from;
        final AionAddress caller = origin;
        final DataWordImpl nrgPrice = DataWordImpl.ONE;
        final long nrgLimit = 21000L;
        final DataWordImpl callValue = DataWordImpl.ZERO;
        final byte[] callData = txData;
        final int callDepth = 1;
        final int flag = 0;
        final int kind = 0;
        final AionAddress blockCoinbase =
                new AionAddress(
                        AddressSpecs.computeA0Address(HashUtil.h256("coinbase".getBytes())));
        long blockNumber = 0;
        long blockTimestamp = 0;
        long blockNrgLimit = 0;
        DataWordImpl blockDifficulty = DataWordImpl.ZERO;

        return new PrecompiledTransactionContext(
                address,
                origin,
                caller,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                transactionHash,
                transactionHash,
                blockNumber,
                nrgLimit,
                callDepth);
    }
}
