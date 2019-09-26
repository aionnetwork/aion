package org.aion.zero.impl.valid;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.slf4j.Logger;

public class GreatGrandParentBlockHeaderValidator {

    private Map<Byte, List<GreatGrandParentDependantBlockHeaderRule>> chainRules;

    public GreatGrandParentBlockHeaderValidator(
        Map<Byte, List<GreatGrandParentDependantBlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException();
        }
        chainRules = rules;
    }

    public boolean validate(
            BlockHeader greatGrandParent, BlockHeader grandParent, BlockHeader current, Logger logger) {
        if (grandParent == null) {
            RuleError err = new RuleError(this.getClass(),"the input grandParent header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err));
            return false;
        }

        if (current == null) {
            RuleError err = new RuleError(this.getClass(),"the input header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err));
            return false;
        }

        List<GreatGrandParentDependantBlockHeaderRule> rules = chainRules.get(current.getSealType().getSealId());

        if (rules == null) {
            return false;
        } else {
            List<RuleError> errors = new LinkedList<>();
            for (GreatGrandParentDependantBlockHeaderRule rule : rules) {
                if (!rule.validate(greatGrandParent, grandParent, current, errors)) {
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