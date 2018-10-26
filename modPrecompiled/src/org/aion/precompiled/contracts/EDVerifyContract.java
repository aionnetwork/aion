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
 *     Centrys
 */

package org.aion.precompiled.contracts;
import org.aion.base.type.IExecutionResult;
import org.aion.base.util.Hex;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;

public class EDVerifyContract implements IPrecompiledContract {

    // set to a default cost for now, this will need to be adjusted
    private final static long COST = 21000L;

    @Override
    public IExecutionResult execute(byte[] input, long nrgLimit) {
        if (COST > nrgLimit) {
            return new ExecutionResult(ExecutionResult.ResultCode.OUT_OF_NRG, 0);
        }
        byte[] msg = new byte[32];
        byte[] sig = new byte[64];
        byte[] pubKey = new byte[32];

        System.arraycopy(input, 0, msg, 0, 32);
        System.arraycopy(input, 32, sig, 0, 64);
        System.arraycopy(input, 96, pubKey, 0, 32);

        //TODO: may need to remove after end-to-end test
        try {
            System.out.println("EDVERIFY Message: " + Hex.toHexString(msg));
            System.out.println("EDVERIFY Sig: " + Hex.toHexString(sig));
            System.out.println("EDVERIFY PubKey: " + Hex.toHexString(pubKey));
        } catch (Exception e) {
            System.out.println("could not get hex string: " + e.getMessage());
        }

        try {
            boolean verify = ECKeyEd25519.verify(msg, sig, pubKey);
            byte[] result = new byte[1];
            result[0] = verify ? (byte) 1 : (byte) 0;
            return new ExecutionResult(ExecutionResult.ResultCode.SUCCESS, nrgLimit - COST, result);
        } catch (Throwable e) {
            return new ExecutionResult(ExecutionResult.ResultCode.INTERNAL_ERROR, 0);
        }
    }
}
