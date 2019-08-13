package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;

import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.mcf.types.AbstractBlockHeader.BlockSealType;
import org.slf4j.Logger;

public class BlockHeaderValidator extends AbstractBlockHeaderValidator {

    private Map<BlockSealType, List<BlockHeaderRule>> chainRules;

    public BlockHeaderValidator(Map<BlockSealType, List<BlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException();
        }
        chainRules = rules;
    }

    public boolean validate(BlockHeader header, Logger logger) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        List<BlockHeaderRule> rules = chainRules.get(header.getSealType());

        if (rules == null) {
            return false;
        } else {
            for (BlockHeaderRule rule : rules) {
                if (!rule.validate(header, errors)) {
                    if (logger != null) logErrors(logger, errors);
                    return false;
                }
            }
        }

        return true;
    }
}
