package org.aion.zero.impl.precompiled.contracts.ATB;

import java.util.ArrayList;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;

public class BridgeTestUtils {
    static PrecompiledTransactionContext dummyContext() {
        return context(AddressUtils.ZERO_ADDRESS, AddressUtils.ZERO_ADDRESS);
    }

    static PrecompiledTransactionContext context(AionAddress from, AionAddress to) {
        final byte[] transactionHash = HashUtil.h256("transaction".getBytes());
        final long nrgLimit = 21000L;
        final int callDepth = 1;
        long blockNumber = 0;

        return new PrecompiledTransactionContext(
                to,
                from,
                from,
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
