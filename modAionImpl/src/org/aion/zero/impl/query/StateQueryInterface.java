package org.aion.zero.impl.query;

import java.util.Optional;
import org.aion.mcf.core.AccountState;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;

public interface StateQueryInterface {
    Optional<AccountState> getAccountState(Address address, long blockNumber);

    Optional<AccountState> getAccountState(Address address, byte[] blockHash);

    Optional<AccountState> getAccountState(Address address);

    Optional<ByteArrayWrapper> getCode(Address address);
}
