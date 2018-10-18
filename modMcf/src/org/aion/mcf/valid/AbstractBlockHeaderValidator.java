package org.aion.mcf.valid;


import org.aion.mcf.blockchain.valid.IValidRule;
import org.slf4j.Logger;

import java.util.List;

public abstract class AbstractBlockHeaderValidator {

    public void logErrors(final Logger logger,
                          final List<IValidRule.RuleError> errors) {
        if (errors.isEmpty())
            return;

        if (logger.isErrorEnabled()) {
            StringBuilder builder = new StringBuilder();
            builder.append(this.getClass().getSimpleName());
            builder.append(" raised errors: \n");
            for (IValidRule.RuleError error : errors) {
                builder.append(error.errorClass.getSimpleName());
                builder.append("\t\t\t\t");
                builder.append(error.error);
                builder.append("\n");
            }
            logger.error(builder.toString());
        }
    }
}
