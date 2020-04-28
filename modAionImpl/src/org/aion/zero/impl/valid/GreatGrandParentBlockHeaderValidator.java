package org.aion.zero.impl.valid;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.slf4j.Logger;

public class GreatGrandParentBlockHeaderValidator {

    private Map<BlockSealType, List<GreatGrandParentDependantBlockHeaderRule>> chainRules;

    public GreatGrandParentBlockHeaderValidator(
        Map<BlockSealType, List<GreatGrandParentDependantBlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException();
        }
        chainRules = rules;
    }

    public boolean validate(BlockHeader grandParent, BlockHeader greatGrandParent, BlockHeader current, Logger logger) {
        if (grandParent == null) {
            List<String> headers =
                List.of(
                    "grandParent-null",
                    "greatGrandParent-" + (greatGrandParent == null ? "null" : greatGrandParent.toString()),
                    "current-" + (current == null ? "null" : current.toString()));
            RuleError err = new RuleError(this.getClass(),"the input grandParent header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err), headers);
            return false;
        }

        if (current == null) {
            List<String> headers =
                List.of(
                    "grandParent-" + grandParent.toString(),
                    "greatGrandParent-" + (greatGrandParent == null ? "null" : greatGrandParent.toString()),
                    "current-null");
            RuleError err = new RuleError(this.getClass(),"the input header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err), headers);
            return false;
        }

        List<GreatGrandParentDependantBlockHeaderRule> rules = chainRules.get(current.getSealType());

        if (rules == null) {
            return false;
        } else {
            List<RuleError> errors = new LinkedList<>();
            for (GreatGrandParentDependantBlockHeaderRule rule : rules) {
                if (!rule.validate(grandParent, greatGrandParent, current, errors)) {
                    if (logger != null) {
                        List<String> headers =
                            List.of(
                                "grandParent-" + grandParent.toString(),
                                "greatGrandParent-" + (greatGrandParent == null ? "null" : greatGrandParent.toString()),
                                "current-" + current.toString());
                        BlockHeaderValidatorUtil.logErrors(
                                logger, this.getClass().getSimpleName(), errors, headers);
                    }
                    return false;
                }
            }
        }

        return true;
    }
}