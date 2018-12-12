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
 *     Centrys.
 *     Aion foundation.
 */

package org.aion.precompiled.contracts;

import org.aion.base.type.AionAddress;
import org.aion.vm.FastVmResultCode;
import org.aion.vm.FastVmTransactionResult;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.vm.IPrecompiledContract;

public class EDVerifyContract implements IPrecompiledContract {

    // set to a default cost for now, this will need to be adjusted
    private static final long COST = 3000L;
    private static final String INCORRECT_LENGTH = "Incorrect input length";


    /**
     * @param input 128 bytes of data input, [32-bytes message, 32-bytes public key, 64-bytes signature]
     * @return the verification result of the given input (publickey address for pass, all-0's address for fail)
     */
    @Override
    public FastVmTransactionResult execute(byte[] input, long nrgLimit) {

        // check length
        if (input == null || input.length != 128) {
            return new FastVmTransactionResult(
                FastVmResultCode.FAILURE, nrgLimit - COST, INCORRECT_LENGTH.getBytes());
        }

        if (COST > nrgLimit) {
            return new FastVmTransactionResult(FastVmResultCode.OUT_OF_NRG, 0);
        }
        byte[] msg = new byte[32];
        byte[] sig = new byte[64];
        byte[] pubKey = new byte[32];

        System.arraycopy(input, 0, msg, 0, 32);
        System.arraycopy(input, 32, pubKey, 0, 32);
        System.arraycopy(input, 64, sig, 0, 64);


        try {
            boolean verify = ECKeyEd25519.verify(msg, sig, pubKey);
            return new FastVmTransactionResult(FastVmResultCode.SUCCESS, nrgLimit - COST, verify ? pubKey : AionAddress
                .ZERO_ADDRESS().toBytes());
        } catch (Exception e) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
    }
}
