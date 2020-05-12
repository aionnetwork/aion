package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.zero.impl.types.BlockHeader;

public interface GrandParentDependantBlockHeaderRule {

    /**
     * A separate class of rules that infer a relationship between the current block, the block
     * preceding (parent) and the block preceding that block (grandparent)
     */
    boolean validate(
            BlockHeader grandParent,
            BlockHeader parent,
            BlockHeader current,
            List<RuleError> errors);
}
