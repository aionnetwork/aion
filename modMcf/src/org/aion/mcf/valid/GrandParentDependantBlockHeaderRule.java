package org.aion.mcf.valid;

import java.util.List;
import org.aion.interfaces.block.BlockHeader;
import org.aion.mcf.blockchain.valid.AbstractValidRule;

public abstract class GrandParentDependantBlockHeaderRule<BH extends BlockHeader>
        extends AbstractValidRule {

    /**
     * A separate class of rules that infer a relationship between the current block, the block
     * preceding (parent) and the block preceding that block (grandparent)
     */
    public abstract boolean validate(BH grandParent, BH parent, BH current, List<RuleError> errors);
}
