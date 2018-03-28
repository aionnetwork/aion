package org.aion.zero.impl.core;

import java.util.HashMap;
import java.util.Map;

public enum EnergyStrategies {
    MONOTONIC("monotonic", (parent, config) -> {
        long divisorLimit = config.getConstants().getEnergyDivisorLimitLong();
        long lowerBound = config.getConstants().getEnergyLowerBoundLong();
        return EnergyLimitStrategy.monotonicallyIncreasingEnergyStrategy(
                parent.energyLimit,
                parent.energyConsumed,
                lowerBound,
                divisorLimit
        );
    }),

    CLAMPED_DECAYING("clamped-decay", (parent, config) -> 0),

    DECAYING("decaying", (parent, config) -> 0),
    TARGETTED("targetted", (parent, config) -> 0);

    // never updated after init, no need to synchronized
    private static final Map<String, EnergyStrategies> reverseMap = new HashMap<>();
    static {
        reverseMap.put(MONOTONIC.label, MONOTONIC);
        reverseMap.put(CLAMPED_DECAYING.label, CLAMPED_DECAYING);
        reverseMap.put(DECAYING.label, DECAYING);
        reverseMap.put(TARGETTED.label, TARGETTED);
    }

    private final String label;
    private final IEnergyLimitStrategy energyLimitStrategy;

    EnergyStrategies(String label, IEnergyLimitStrategy strategyFunc) {
        this.label = label;
        this.energyLimitStrategy = strategyFunc;
    }

    public String getLabel() {
        return this.label;
    }

    public IEnergyLimitStrategy getStrategy() {
        return this.energyLimitStrategy;
    }

    // reverse mapper from label
    public static EnergyStrategies getStrategy(String label) {
        return reverseMap.get(label);
    }
}
