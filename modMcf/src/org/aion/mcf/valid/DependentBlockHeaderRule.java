package org.aion.mcf.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.AbstractValidRule;

/** A class of rules that requires memory of the previous block */
public abstract class DependentBlockHeaderRule extends AbstractValidRule {

    /**
     * Validates a dependant rule, where {@code header} represents the current block, and {@code
     * dependency} represents the {@code memory} required to validate whether the current block is
     * correct. Most likely the {@code memory} refers to the previous block
     */
    public abstract boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors);
}
