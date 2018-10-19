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

package org.aion.mcf.valid;

import org.aion.mcf.vm.Constants;

public class TxNrgRule {
    private static final long CONTRACT_CREATE_TX_NRG_MAX = Constants.NRG_TX_CREATE_MAX + 1;
    private static final long CONTRACT_CREATE_TX_NRG_MIN = Constants.NRG_TX_CREATE - 1;
    private static final long TX_NRG_MAX = Constants.NRG_TRANSACTION_MAX + 1;
    private static final long TX_NRG_MIN = Constants.NRG_TRANSACTION - 1;

    public static boolean isValidNrgContractCreate(long nrg) {
        return (nrg > CONTRACT_CREATE_TX_NRG_MIN) && (nrg < CONTRACT_CREATE_TX_NRG_MAX);
    }

    public static boolean isValidNrgTx(long nrg) {
        return (nrg > TX_NRG_MIN) && (nrg < TX_NRG_MAX);
    }
}
