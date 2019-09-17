package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;

/** A class of rules that requires memory of the previous block */
public interface DependentBlockHeaderRule {

    /**
     * Validates a dependant rule, where {@code header} represents the current block, and {@code
     * dependency} represents the {@code memory} required to validate whether the current block is
     * correct. Most likely the {@code memory} refers to the previous block
     */
    boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors);

    boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors, Object arg);

}