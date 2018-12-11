package org.aion.mcf.valid;

import java.util.List;
import org.aion.base.type.IBlockHeader;
import org.aion.mcf.blockchain.valid.AbstractValidRule;
import org.aion.mcf.blockchain.valid.IBlockHeaderValidRule;

/** A class of rules that requires memory of the previous block */
public abstract class DependentBlockHeaderRule<BH extends IBlockHeader> extends AbstractValidRule
        implements IBlockHeaderValidRule<BH> {

    /**
     * Validates a dependant rule, where {@code header} represents the current block, and {@code
     * dependency} represents the {@code memory} required to validate whether the current block is
     * correct. Most likely the {@code memory} refers to the previous block
     */
    public abstract boolean validate(BH header, BH dependency, List<RuleError> errors);
}
