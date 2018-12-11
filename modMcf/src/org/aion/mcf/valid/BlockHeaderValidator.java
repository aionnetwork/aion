package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.mcf.types.AbstractBlockHeader;
import org.slf4j.Logger;

public class BlockHeaderValidator<BH extends AbstractBlockHeader>
        extends AbstractBlockHeaderValidator {

    private List<BlockHeaderRule<BH>> rules;

    public BlockHeaderValidator(List<BlockHeaderRule<BH>> rules) {
        this.rules = rules;
    }

    public boolean validate(BH header, Logger logger) {
        List<IValidRule.RuleError> errors = new LinkedList<>();
        for (BlockHeaderRule<BH> rule : rules) {
            if (!rule.validate(header, errors)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }
}
