package org.aion.mcf.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.AbstractValidRule;

/** A class of rules that requires memory of the previous block */
public abstract class DependentBlockHeaderRuleWithArg extends AbstractValidRule {

    public abstract boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors, Object arg);
}
