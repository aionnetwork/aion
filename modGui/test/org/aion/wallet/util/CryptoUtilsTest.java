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
package org.aion.wallet.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import org.aion.crypto.ECKey;
import org.junit.Test;
import org.libsodium.jni.encoders.Hex;

public class CryptoUtilsTest {
    /* Test values taken from "Test vector 2 for ed25519" in https://github.com/satoshilabs/slips/blob/master/slip-0010.md */

    private static final Hex HEX = new Hex();

    /**
     * test vector 2 seed
     */
    private static final byte[] SEED = HEX.decode(
        "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542");
    /**
     * expected chain code for chain m
     */
    private static final byte[] EXPECTED_CHAIN_M_CHAIN_CODE = HEX.decode(
        "ef70a74db9c3a5af931b5fe73ed8e1a53464133654fd55e7a66f8570b8e33c3b");
    /**
     * expected private key for chain m
     */
    private static final byte[] EXPECTED_CHAIN_M_PRIVATE_KEY = HEX.decode(
        "171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012");

    @Test
    public void testHashSha512() throws Exception {
        ECKey hashedKey = CryptoUtils
            .getECKey(CryptoUtils.hashSha512(CryptoUtils.ED25519_KEY, SEED));

        byte[] I = hashedKey.getPrivKeyBytes();
        byte[] I_L = Arrays.copyOfRange(I, 0, 32);
        byte[] I_R = Arrays.copyOfRange(I, 32, 64);

        assertThat(I_L, is(EXPECTED_CHAIN_M_PRIVATE_KEY));
        assertThat(I_R, is(EXPECTED_CHAIN_M_CHAIN_CODE));
    }

    @Test
    public void testHardenNumber() {
        assertThat(CryptoUtils.hardenedNumber(0), is(HEX.decode("80000000")));
    }
}