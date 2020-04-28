package org.aion.zero.impl.valid;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.slf4j.Logger;

/** validation rules depending on parent's block header */
public class ParentBlockHeaderValidator {

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

    public boolean validate(
            BlockHeader header, BlockHeader parent, Logger logger, Object extraArg) {
        if (header == null) {
            List<String> headers = List.of("current-null", "parent-" + (parent == null ? "null" : parent.toString()));
            RuleError err = new RuleError(this.getClass(),"the input header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err), headers);
            return false;
        }

        if (parent == null) {
            List<String> headers = List.of("current-" + header.toString(), "parent-null");
            RuleError err = new RuleError(this.getClass(),"the input parent header is null");
            BlockHeaderValidatorUtil.logErrors(
                    logger, this.getClass().getSimpleName(), Collections.singletonList(err), headers);
            return false;
        }

        List<DependentBlockHeaderRule> rules = chainRules.get(header.getSealType());

        if (rules == null) {
            return false;
        } else {
            List<RuleError> errors = new LinkedList<>();

            for (DependentBlockHeaderRule rule : rules) {
                if (!rule.validate(header, parent, errors, extraArg)) {
                    List<String> headers = List.of("current-" + header.toString(), "parent-" + parent.toString());
                    if (logger != null) {
                        BlockHeaderValidatorUtil.logErrors(
                                logger, this.getClass().getSimpleName(), errors, headers);
                    }
                    return false;
                }
            }
        }

        return true;
    }
}
