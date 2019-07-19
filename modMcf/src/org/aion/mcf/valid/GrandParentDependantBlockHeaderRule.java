package org.aion.mcf.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.AbstractValidRule;

public abstract class GrandParentDependantBlockHeaderRule
        extends AbstractValidRule {

    /**
     * A separate class of rules that infer a relationship between the current block, the block
     * preceding (parent) and the block preceding that block (grandparent)
     */
    public abstract boolean validate(BlockHeader grandParent, BlockHeader parent, BlockHeader current, List<RuleError> errors);
}
