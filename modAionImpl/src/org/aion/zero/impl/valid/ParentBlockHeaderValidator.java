package org.aion.zero.impl.valid;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.slf4j.Logger;

/** validation rules depending on parent's block header */
public class ParentBlockHeaderValidator {

    private Map<Byte, List<DependentBlockHeaderRule>> chainRules;

    public ParentBlockHeaderValidator(Map<Byte, List<DependentBlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException();
        }
        chainRules = rules;
    }

    public boolean validate(BlockHeader header, BlockHeader parent, Logger logger) {
        return validate(header, parent, logger, null);
    }

    public boolean validate(
            BlockHeader header, BlockHeader parent, Logger logger, Object extraArg) {
        if (header == null) {
            RuleError err = new RuleError(this.getClass(),"the input header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err));
            return false;
        }

        if (parent == null) {
            RuleError err = new RuleError(this.getClass(),"the input parent header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err));
            return false;
        }

        List<DependentBlockHeaderRule> rules = chainRules.get(header.getSealType().getSealId());

        if (rules == null) {
            return false;
        } else {
            List<RuleError> errors = new LinkedList<>();
            for (DependentBlockHeaderRule rule : rules) {
                if (!rule.validate(header, parent, errors, extraArg)) {
                    if (logger != null) {
                        BlockHeaderValidatorUtil.logErrors(
                                logger, this.getClass().getSimpleName(), errors);
                    }
                    return false;
                }
            }
        }

        return true;
    }
}
