package org.aion.zero.impl.query;

import java.util.Optional;

public interface BlockQueryInterface {
    Optional<Long> getInitialStartingBlockNumber();
}
