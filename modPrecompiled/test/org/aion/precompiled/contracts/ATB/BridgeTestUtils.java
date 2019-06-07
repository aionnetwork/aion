package org.aion.precompiled.contracts.ATB;

import org.aion.types.AionAddress;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.HashUtil;
import org.aion.fastvm.ExecutionContext;
import org.aion.util.types.AddressUtils;


public class BridgeTestUtils {
    static ExecutionContext dummyContext() {
        return context(AddressUtils.ZERO_ADDRESS, AddressUtils.ZERO_ADDRESS, new byte[0]);
    }

    static ExecutionContext context(AionAddress from, AionAddress to, byte[] txData) {
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

        return new ExecutionContext(
                null,
                transactionHash,
                address,
                origin,
                caller,
                nrgPrice,
                nrgLimit,
                callValue,
                callData,
                callDepth,
                flag,
                kind,
                blockCoinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockDifficulty);
    }
}
