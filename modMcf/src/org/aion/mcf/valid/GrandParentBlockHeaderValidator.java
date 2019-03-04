package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;
import org.aion.interfaces.block.BlockHeader;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.slf4j.Logger;

public class GrandParentBlockHeaderValidator<BH extends BlockHeader>
        extends AbstractBlockHeaderValidator {

    private List<GrandParentDependantBlockHeaderRule<BH>> rules;

    public GrandParentBlockHeaderValidator(List<GrandParentDependantBlockHeaderRule<BH>> rules) {
        this.rules = rules;
    }

    public boolean validate(BH grandParent, BH parent, BH current, Logger logger) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        for (GrandParentDependantBlockHeaderRule<BH> rule : rules) {
            if (!rule.validate(grandParent, parent, current, errors)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }
}
