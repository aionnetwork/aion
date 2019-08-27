package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.mcf.blockchain.valid.RuleError;
import org.slf4j.Logger;

public class BlockHeaderValidator extends AbstractBlockHeaderValidator {

    private List<BlockHeaderRule> rules;

    public BlockHeaderValidator(List<BlockHeaderRule> rules) {
        this.rules = rules;
    }

    public boolean validate(BlockHeader header, Logger logger) {
        List<RuleError> errors = new LinkedList<>();
        for (BlockHeaderRule rule : rules) {
            if (!rule.validate(header, errors)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }
        return true;
    }
}
