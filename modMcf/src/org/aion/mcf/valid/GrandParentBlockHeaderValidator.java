package org.aion.mcf.valid;

import org.aion.base.type.IBlockHeader;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;

public class GrandParentBlockHeaderValidator<BH extends IBlockHeader> extends AbstractBlockHeaderValidator {

    private List<GrandParentDependantBlockHeaderRule<BH>> rules;

    public GrandParentBlockHeaderValidator(List<GrandParentDependantBlockHeaderRule<BH>> rules) {
        this.rules = rules;
    }

    public boolean validate(BH grandParent, BH parent, BH current, Logger logger) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        for (GrandParentDependantBlockHeaderRule<BH> rule : rules) {
            if (!rule.validate(grandParent, parent, current, errors)) {
                if (logger != null)
                    logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }
}
