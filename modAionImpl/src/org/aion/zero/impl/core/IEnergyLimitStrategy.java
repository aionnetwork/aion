package org.aion.zero.impl.core;

import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.types.A0BlockHeader;

@FunctionalInterface
public interface IEnergyLimitStrategy {
    long calculateEnergy(A0BlockHeader parent, ChainConfiguration config);
}
