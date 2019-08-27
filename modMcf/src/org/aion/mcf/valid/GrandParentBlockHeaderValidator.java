package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.RuleError;
import org.slf4j.Logger;

public class GrandParentBlockHeaderValidator
        extends AbstractBlockHeaderValidator {

    private List<GrandParentDependantBlockHeaderRule> rules;

    public GrandParentBlockHeaderValidator(List<GrandParentDependantBlockHeaderRule> rules) {
        this.rules = rules;
    }

    public boolean validate(BlockHeader grandParent, BlockHeader parent, BlockHeader current, Logger logger) {
        List<RuleError> errors = new LinkedList<>();

        for (GrandParentDependantBlockHeaderRule rule : rules) {
            if (!rule.validate(grandParent, parent, current, errors)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }
}
