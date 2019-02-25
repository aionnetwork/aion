package org.aion.zero.impl.query;

import java.util.Optional;

public interface SyncQueryInterface {
    Optional<Long> getLocalBestBlockNumber();

    Optional<Long> getNetworkBestBlockNumber();

    boolean isSyncComplete();
}
