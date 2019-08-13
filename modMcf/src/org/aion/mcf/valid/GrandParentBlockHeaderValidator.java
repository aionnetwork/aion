package org.aion.mcf.valid;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.mcf.types.AbstractBlockHeader.BlockSealType;
import org.slf4j.Logger;

public class GrandParentBlockHeaderValidator
        extends AbstractBlockHeaderValidator {

    private Map<BlockSealType, List<GrandParentDependantBlockHeaderRule>> chainRules;


    public GrandParentBlockHeaderValidator(Map<BlockSealType, List<GrandParentDependantBlockHeaderRule>> rules) {
        if (rules == null) {
            throw new NullPointerException();
        }
        chainRules = rules;
    }

    public boolean validate(BlockHeader grandParent, BlockHeader parent, BlockHeader current, Logger logger) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        List<GrandParentDependantBlockHeaderRule> rules = chainRules.get(current.getSealType());

        if (rules == null) {
            return false;
        } else {
            for (GrandParentDependantBlockHeaderRule rule : rules) {
                if (!rule.validate(grandParent, parent, current, errors)) {
                    if (logger != null) logErrors(logger, errors);
                    return false;
                }
            }
        }

        return true;
    }
}
