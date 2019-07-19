package org.aion.mcf.valid;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.IBlockHeaderValidRule;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.slf4j.Logger;

/** validation rules depending on parent's block header */
public class ParentBlockHeaderValidator
        extends AbstractBlockHeaderValidator {

    private List<DependentBlockHeaderRule> rules;
    private List<DependentBlockHeaderRuleWithArg> argRules;


    public ParentBlockHeaderValidator(List<DependentBlockHeaderRule> rules) {
        this.rules = rules;
        argRules = new ArrayList<>();
    }

    public ParentBlockHeaderValidator(List<DependentBlockHeaderRule> _rules, List<DependentBlockHeaderRuleWithArg> _argRules) {
        rules = _rules;
        argRules = _argRules;
    }


    public boolean validate(BlockHeader header, BlockHeader parent, Logger logger, BigInteger stake) {
        List<IValidRule.RuleError> errors = new LinkedList<>();

        for (DependentBlockHeaderRule rule : rules) {
            if (!rule.validate(header, parent, errors)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }

        for (DependentBlockHeaderRuleWithArg rule : argRules) {
            if (!rule.validate(header, parent, errors, stake)) {
                if (logger != null) logErrors(logger, errors);
                return false;
            }
        }

        return true;
    }
}
