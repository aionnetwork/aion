/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 *
 * <p>****************************************************************************
 */
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
