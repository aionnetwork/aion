package org.aion.mcf.config.dynamic2;

import java.io.Serializable;

public class ConfigProposalResult implements Serializable {
    public boolean success;

    public ConfigProposalResult(boolean success) {
        this.success = success;
    }

}
