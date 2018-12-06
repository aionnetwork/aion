package org.aion.zero.impl.query;

import java.util.Optional;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.AccountState;

public interface StateQueryInterface {
    Optional<AccountState> getAccountState(AionAddress address, long blockNumber);

    Optional<AccountState> getAccountState(AionAddress address, byte[] blockHash);

    Optional<AccountState> getAccountState(AionAddress address);

    Optional<ByteArrayWrapper> getCode(AionAddress address);
}
