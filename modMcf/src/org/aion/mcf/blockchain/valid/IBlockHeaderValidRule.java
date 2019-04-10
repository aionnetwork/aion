package org.aion.mcf.blockchain.valid;

import java.util.List;
import org.aion.interfaces.block.BlockHeader;

/**
 * Block header validation rules.
 *
 * @param <BH>
 */
public interface IBlockHeaderValidRule<BH extends BlockHeader> extends IValidRule {

    boolean validate(BH header, BH dependency, List<RuleError> errors);
}
