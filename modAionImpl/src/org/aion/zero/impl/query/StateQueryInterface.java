package org.aion.zero.impl.query;

import java.util.Optional;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.AccountState;

public interface StateQueryInterface {
    Optional<AccountState> getAccountState(Address address, long blockNumber);

    Optional<AccountState> getAccountState(Address address, byte[] blockHash);

    Optional<AccountState> getAccountState(Address address);

    Optional<ByteArrayWrapper> getCode(Address address);
}
