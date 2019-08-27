package org.aion.mcf.blockchain.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;

/**
 * Block header validation rules.
 *
 */
public interface IBlockHeaderValidRule {

    boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors);
}
