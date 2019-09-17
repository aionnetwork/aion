package org.aion.zero.impl.valid;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.slf4j.Logger;

public class BlockHeaderValidator {

    private Map<Byte, List<BlockHeaderRule>> chainRules;

    public BlockHeaderValidator(Map<Byte, List<BlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException("The blockHeaderRule can not be null");
        }
        chainRules = rules;
    }

    public boolean validate(BlockHeader header, Logger logger) {
        if (header == null) {
            RuleError err = new RuleError(this.getClass(),"the input header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err));
            return false;
        }

        List<BlockHeaderRule> rules = chainRules.get(header.getSealType().getSealId());
        if (rules == null) {
            return false;
        } else {
            List<RuleError> errors = new LinkedList<>();
            for (BlockHeaderRule rule : rules) {
                if (!rule.validate(header, errors)) {
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
