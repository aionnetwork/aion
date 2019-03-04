package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;
import org.aion.interfaces.block.BlockHeader;
import org.aion.mcf.blockchain.valid.IBlockHeaderValidRule;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.slf4j.Logger;

/** validation rules depending on parent's block header */
public class ParentBlockHeaderValidator<BH extends BlockHeader>
        extends AbstractBlockHeaderValidator {

    private List<DependentBlockHeaderRule<BH>> rules;

    public ParentBlockHeaderValidator(List<DependentBlockHeaderRule<BH>> rules) {
        this.rules = rules;
    }

    public boolean validate(BH header, BH parent, Logger logger) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        for (IBlockHeaderValidRule<BH> rule : rules) {
            if (!rule.validate(header, parent, errors)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }
}
