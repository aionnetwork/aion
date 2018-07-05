package org.aion.mcf.config.dynamic2;

public class InFlightConfigChangeResult {
    private final boolean success;
    private final IDynamicConfigApplier applier;

    public InFlightConfigChangeResult(boolean success, IDynamicConfigApplier applier) {
        this.success = success;
        this.applier = applier;
    }

    boolean isSuccess() {
        return this.success;
    }

    IDynamicConfigApplier getApplier() {
        return this.applier;
    }

    @Override
    public String toString() {
        return "InFlightConfigChangeResult{" +
                "success=" + success +
                ", applier=" + applier +
                '}';
    }
}
