package org.aion.zero.impl.query;

import java.util.Optional;
import org.aion.mcf.core.AccountState;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

public interface StateQueryInterface {
    Optional<AccountState> getAccountState(AionAddress address, long blockNumber);

    Optional<AccountState> getAccountState(AionAddress address, byte[] blockHash);

    Optional<AccountState> getAccountState(AionAddress address);

    Optional<ByteArrayWrapper> getCode(AionAddress address);
}
