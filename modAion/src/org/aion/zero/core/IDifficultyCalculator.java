/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/
package org.aion.zero.core;

import java.math.BigInteger;

import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.zero.types.A0BlockHeader;

/**
 * Interface for retrieving difficulty calculations for a particular
 * chain configuration, note that depending on where the corresponding
 * class is generated, it will utilized different algorithms. However the
 * common interface of the current and parent blockHeader will remain.
 *
 * @author yao
 */
@FunctionalInterface
public interface IDifficultyCalculator {
    BigInteger calculateDifficulty(A0BlockHeader current, A0BlockHeader dependency);
}
