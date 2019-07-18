package org.aion.mcf.valid;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.slf4j.Logger;

public class GrandParentBlockHeaderValidator
        extends AbstractBlockHeaderValidator {

    private List<GrandParentDependantBlockHeaderRule> rules;

    public GrandParentBlockHeaderValidator(List<GrandParentDependantBlockHeaderRule> rules) {
        this.rules = rules;
    }

    public boolean validate(BlockHeader grandParent, BlockHeader parent, BlockHeader current, Logger logger) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        for (GrandParentDependantBlockHeaderRule rule : rules) {
            if (!rule.validate(grandParent, parent, current, errors)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }

    public boolean validate(BlockHeader grandParent, BlockHeader parent, BlockHeader current, Logger logger, BigInteger stake) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        for (GrandParentDependantBlockHeaderRule rule : rules) {
            if (!rule.validate(grandParent, parent, current, errors, stake)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }
}
