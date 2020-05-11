package org.aion.zero.impl.blockchain;

import java.util.Optional;
import org.aion.base.AccountState;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

public interface RepoState {
    Optional<AccountState> getAccountState(AionAddress address);

    Optional<AccountState> getAccountState(AionAddress address, long blockNumber);

    Optional<ByteArrayWrapper> getCode(AionAddress address);

    Optional<ByteArrayWrapper> getStorageValue(AionAddress address, ByteArrayWrapper key);
}
