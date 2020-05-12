package org.aion.zero.impl.valid;

import java.util.List;
import org.slf4j.Logger;

class BlockHeaderValidatorUtil {

    static void logErrors(final Logger logger, String className, final List<RuleError> errors, final List<String> headers) {
        if (errors.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(className);
        builder.append(" raised errors: \n");
        for (RuleError error : errors) {
            builder.append(error.errorClass.getSimpleName());
            builder.append("\t\t\t\t");
            builder.append(error.error);
            builder.append("\n");
        }

        for (String header : headers) {
            builder.append("\t");
            builder.append(header);
            builder.append("\n");
        }

        logger.error(builder.toString());
    }

    static void addError(String error, Class cls, List<RuleError> errors) {
        errors.add(new RuleError(cls, error));
    }
}
