/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.zero.impl.core.energy;

import java.util.HashMap;
import java.util.Map;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.config.CfgEnergyStrategy;

public enum EnergyStrategies {
    MONOTONIC("monotonic-increase"),
    CLAMPED_DECAYING("clamped-decay"),
    DECAYING("decaying"),
    TARGETTED("targetted");

    // never updated after init, no need to synchronized
    private static final Map<String, EnergyStrategies> reverseMap = new HashMap<>();

    static {
        reverseMap.put(MONOTONIC.label, MONOTONIC);
        reverseMap.put(CLAMPED_DECAYING.label, CLAMPED_DECAYING);
        reverseMap.put(DECAYING.label, DECAYING);
        reverseMap.put(TARGETTED.label, TARGETTED);
    }

    private final String label;

    EnergyStrategies(String label) {
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }

    // reverse mapper from label
    public static EnergyStrategies getStrategy(String label) {
        return reverseMap.get(label);
    }

    public static AbstractEnergyStrategyLimit getEnergyStrategy(
            final EnergyStrategies strategy,
            final CfgEnergyStrategy config,
            final ChainConfiguration chainConfig) {
        switch (strategy) {
            case DECAYING:
                return new DecayStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong());
            case MONOTONIC:
                return new MonotonicallyIncreasingStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong());
            case TARGETTED:
                return new TargetStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong(),
                        config.getTarget());
            case CLAMPED_DECAYING:
                return new ClampedDecayStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong(),
                        config.getUpperBound(),
                        config.getLowerBound());
        }
        throw new IllegalStateException("this should never happen");
    }
}
