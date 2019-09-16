package org.aion.zero.impl.valid;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.slf4j.Logger;

public class GrandParentBlockHeaderValidator
        extends AbstractBlockHeaderValidator {

    private Map<Byte, List<GrandParentDependantBlockHeaderRule>> chainRules;


    public GrandParentBlockHeaderValidator(Map<Byte, List<GrandParentDependantBlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException();
        }
        chainRules = rules;
    }

    public boolean validate(BlockHeader grandParent, BlockHeader parent, BlockHeader current, Logger logger) {
        List<RuleError> errors = new LinkedList<>();

        List<GrandParentDependantBlockHeaderRule> rules = chainRules.get(current.getSealType().getSealId());

        if (rules == null) {
            return false;
        } else {
            for (GrandParentDependantBlockHeaderRule rule : rules) {
                if (!rule.validate(grandParent, parent, current, errors)) {
                    if (logger != null) logErrors(logger, errors);
                    return false;
                }
            }
        }

        return true;
    }
}
