package org.aion.mcf.config;

import java.io.Serializable;

public class ConfigProposalResult implements Serializable {
    public String result;

    public ConfigProposalResult(String result) {
        this.result = result;
    }

}
