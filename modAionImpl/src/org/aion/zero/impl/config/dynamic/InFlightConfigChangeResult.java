package org.aion.zero.impl.config.dynamic;

public class InFlightConfigChangeResult {
    private final boolean success;
    private final IDynamicConfigApplier applier;

    public InFlightConfigChangeResult(boolean success, IDynamicConfigApplier applier) {
        this.success = success;
        this.applier = applier;
    }

    public boolean isSuccess() {
        return this.success;
    }

    public IDynamicConfigApplier getApplier() {
        return this.applier;
    }

    @Override
    public String toString() {
        return "InFlightConfigChangeResult{" + "success=" + success + ", applier=" + applier + '}';
    }
}
