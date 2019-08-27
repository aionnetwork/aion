package org.aion.mcf.valid;

import java.util.List;
import org.aion.mcf.blockchain.valid.RuleError;
import org.slf4j.Logger;

public abstract class AbstractBlockHeaderValidator {

    public void logErrors(final Logger logger, final List<RuleError> errors) {
        if (errors.isEmpty()) return;

        if (logger.isErrorEnabled()) {
            StringBuilder builder = new StringBuilder();
            builder.append(this.getClass().getSimpleName());
            builder.append(" raised errors: \n");
            for (RuleError error : errors) {
                builder.append(error.errorClass.getSimpleName());
                builder.append("\t\t\t\t");
                builder.append(error.error);
                builder.append("\n");
            }
            logger.error(builder.toString());
        }
    }
}
