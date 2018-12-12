/*
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
 * Contributors:
 *     Aion foundation.
 */

package org.aion.precompiled.contracts.ATB;

import java.math.BigInteger;
import javax.annotation.Nonnull;
import org.aion.precompiled.PrecompiledTransactionResult;

public interface Transferable {
    /**
     * Performs a transfer of value from one account to another, using a method that mimicks to the
     * best of it's ability the {@code CALL} opcode. There are some assumptions that become
     * important for any caller to know:
     *
     * @implNote this method will check that the recipient account has no code. This means that we
     *     <b>cannot</b> do a transfer to any contract account.
     * @implNote assumes that the {@code fromValue} derived from the track will never be null.
     * @param to recipient address
     * @param value to be sent (in base units)
     * @return {@code true} if value was performed, {@code false} otherwise
     */
    PrecompiledTransactionResult transfer(@Nonnull final byte[] to, @Nonnull final BigInteger value);
}
