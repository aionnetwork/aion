package org.aion.mcf.valid;

import org.aion.base.type.IBlockHeader;
import org.aion.mcf.blockchain.valid.AbstractValidRule;

import java.util.List;

public abstract class GrandParentDependantBlockHeaderRule<BH extends IBlockHeader>
        extends AbstractValidRule {

    /**
     * <p>A separate class of rules that infer a relationship between the current block,
     * the block preceding (parent) and the block preceding that block (grandparent)</p>
     */
    abstract public boolean validate(
            BH grandParent, BH parent, BH current, List<RuleError> errors);
}
