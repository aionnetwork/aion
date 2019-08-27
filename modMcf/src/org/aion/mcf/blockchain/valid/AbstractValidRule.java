package org.aion.mcf.blockchain.valid;

import java.util.List;

public abstract class AbstractValidRule {

    public void addError(String error, List<RuleError> errors) {
        errors.add(new RuleError(this.getClass(), error));
    }
}
