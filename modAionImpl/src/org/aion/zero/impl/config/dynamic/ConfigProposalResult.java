package org.aion.zero.impl.config.dynamic;

import java.io.Serializable;

public class ConfigProposalResult implements Serializable {
    private final boolean success;
    private final Throwable errorCause;

    public ConfigProposalResult(boolean success) {
        this(success, null);
    }

    public ConfigProposalResult(boolean success, Throwable errorCause) {
        this.success = success;
        this.errorCause = errorCause;
    }

    public boolean isSuccess() {
        return success;
    }

    public Throwable getErrorCause() {
        return errorCause;
    }

    @Override
    public String toString() {
        return "ConfigProposalResult{" + "success=" + success + ", errorCause=" + errorCause + '}';
    }
}
