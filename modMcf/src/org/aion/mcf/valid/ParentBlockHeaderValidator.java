package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.mcf.types.AbstractBlockHeader.BlockSealType;
import org.slf4j.Logger;

/** validation rules depending on parent's block header */
public class ParentBlockHeaderValidator
        extends AbstractBlockHeaderValidator {


    private Map<BlockSealType, List<DependentBlockHeaderRule>> chainRules;

    public ParentBlockHeaderValidator(Map<BlockSealType, List<DependentBlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException();
        }
        chainRules = rules;
    }

    public boolean validate(BlockHeader header, BlockHeader parent, Logger logger) {
        return validate(header, parent, logger, null);
    }

    public boolean validate(BlockHeader header, BlockHeader parent, Logger logger, Object extraArg) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        List<DependentBlockHeaderRule> rules = chainRules.get(header.getSealType());

        if (rules == null) {
            return false;
        } else {
            for (DependentBlockHeaderRule rule : rules) {
                if (!rule.validate(header, parent, errors, extraArg)) {
                    if (logger != null) logErrors(logger, errors);
                    return false;
                }
            }
        }

        return true;
    }
}
