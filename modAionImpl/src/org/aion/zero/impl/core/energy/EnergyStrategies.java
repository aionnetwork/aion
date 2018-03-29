package org.aion.zero.impl.core.energy;

import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.config.CfgEnergyStrategy;

import java.util.HashMap;
import java.util.Map;

public enum EnergyStrategies {
    MONOTONIC("monotonic"),
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

    public static AbstractEnergyStrategyLimit getEnergyStrategy(final EnergyStrategies strategy,
                                    final CfgEnergyStrategy config,
                                    final ChainConfiguration chainConfig) {
        switch(strategy) {
            case DECAYING:
                return new DecayStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong()
                );
            case MONOTONIC:
                return new MonotonicallyIncreasingStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong()
                );
            case TARGETTED:
                return new TargetStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong(),
                        config.getTarget()
                );
            case CLAMPED_DECAYING:
                return new ClampedDecayStrategy(
                        chainConfig.getConstants().getEnergyLowerBoundLong(),
                        chainConfig.getConstants().getEnergyDivisorLimitLong(),
                        config.getUpperBound(),
                        config.getLowerBound()
                );
        }
        throw new IllegalStateException("this should never happen");
    }
}
