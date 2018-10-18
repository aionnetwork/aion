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
package org.aion.wallet.crypto;

import org.aion.crypto.ECKey;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.util.CryptoUtils;
import org.junit.Test;
import org.libsodium.jni.encoders.Hex;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class MasterKeyTest {
    private static final Hex HEX = new Hex();

    /* Test values taken from "Test vector 2 for ed25519" in https://github.com/satoshilabs/slips/blob/master/slip-0010.md */
    private static final byte[] SEED = HEX.decode(
            "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542");
    private static final byte[] EXPECTED_CHAIN_M_0H_PRIVATE =  HEX.decode(
            "1559eb2bbec5790b0c65d8693e4d0875b1747f4970ae8b650486ed7470845635");
    private static final byte[] EXPECTED_CHAIN_M_0H_PUBLIC = HEX.decode(
            "86fab68dcb57aa196c77c5f264f215a112c22a912c10d123b0d03c3c28ef1037");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_PRIVATE =  HEX.decode(
            "ea4f5bfe8694d8bb74b7b59404632fd5968b774ed545e810de9c32a4fb4192f4");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_PUBLIC = HEX.decode(
            "5ba3b9ac6e90e83effcd25ac4e58a1365a9e35a3d3ae5eb07b9e4d90bcf7506d");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_1H_PRIVATE =  HEX.decode(
            "3757c7577170179c7868353ada796c839135b3d30554bbb74a4b1e4a5a58505c");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_1H_PUBLIC = HEX.decode(
            "2e66aa57069c86cc18249aecf5cb5a9cebbfd6fadeab056254763874a9352b45");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_PRIVATE
            = HEX.decode("5837736c89570de861ebc173b1086da4f505d4adb387c6a1b1342d5e4ac9ec72");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_PUBLIC
            = HEX.decode("e33c0f7d81d843c572275f287498e8d408654fdf0d1e065b84e2e6f157aab09b");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_2H_PRIVATE
            =  HEX.decode("551d333177df541ad876a60ea71f00447931c0a9da16f227c11ea080d7391b8d");
    private static final byte[] EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_2H_PUBLIC
            = HEX.decode("47150c75db263559a70d5778bf36abbab30fb061ad69f69ece61a72b0cfa4fc0");

    @Test
    public void testDeriveHardened() throws Exception {
        ECKey hashedKey = CryptoUtils.getECKey(CryptoUtils.hashSha512(CryptoUtils.ED25519_KEY, SEED));
        MasterKey unit = new MasterKey(hashedKey);

        ECKey chain_m_0H = unit.deriveHardened(new int[] { 0 });
        byte[] chain_m_0H_left = Arrays.copyOfRange(
                chain_m_0H.getPrivKeyBytes(), 0, 32);

        assertThat(chain_m_0H_left, is(EXPECTED_CHAIN_M_0H_PRIVATE));
        assertThat(chain_m_0H.getPubKey(), is(EXPECTED_CHAIN_M_0H_PUBLIC));

        ECKey chain_m_0H_2147483647H = unit.deriveHardened(new int[] { 0, 2147483647 });
        byte[] chain_m_0H_2147483647H_left = Arrays.copyOfRange(
                chain_m_0H_2147483647H.getPrivKeyBytes(), 0, 32);

        assertThat(chain_m_0H_2147483647H_left, is(EXPECTED_CHAIN_M_0H_2147483647H_PRIVATE));
        assertThat(chain_m_0H_2147483647H.getPubKey(), is(EXPECTED_CHAIN_M_0H_2147483647H_PUBLIC));

        ECKey chain_m_0H_2147483647H_1H = unit.deriveHardened(new int[] { 0, 2147483647, 1 });
        byte[] chain_m_0H_2147483647H_1H_left = Arrays.copyOfRange(
                chain_m_0H_2147483647H_1H.getPrivKeyBytes(), 0, 32);

        assertThat(chain_m_0H_2147483647H_1H_left,
                is(EXPECTED_CHAIN_M_0H_2147483647H_1H_PRIVATE));
        assertThat(chain_m_0H_2147483647H_1H.getPubKey(),
                is(EXPECTED_CHAIN_M_0H_2147483647H_1H_PUBLIC));

        ECKey chain_m_0H_2147483647H_1H_2147483646H
                = unit.deriveHardened(new int[] { 0, 2147483647, 1, 2147483646 });
        byte[] chain_m_0H_2147483647H_1H_2147483646H_left = Arrays.copyOfRange(
                chain_m_0H_2147483647H_1H_2147483646H.getPrivKeyBytes(), 0, 32);

        assertThat(chain_m_0H_2147483647H_1H_2147483646H_left,
                is(EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_PRIVATE));
        assertThat(chain_m_0H_2147483647H_1H_2147483646H.getPubKey(),
                is(EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_PUBLIC));

        ECKey chain_m_0H_2147483647H_1H_2147483646H_2H
                = unit.deriveHardened(new int[] { 0, 2147483647, 1, 2147483646, 2 });
        byte[] chain_m_0H_2147483647H_1H_2147483646H_2H_left = Arrays.copyOfRange(
                chain_m_0H_2147483647H_1H_2147483646H_2H.getPrivKeyBytes(), 0, 32);

        assertThat(chain_m_0H_2147483647H_1H_2147483646H_2H_left,
                is(EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_2H_PRIVATE));
        assertThat(chain_m_0H_2147483647H_1H_2147483646H_2H.getPubKey(),
                is(EXPECTED_CHAIN_M_0H_2147483647H_1H_2147483646H_2H_PUBLIC));
    }

    @Test(expected = ValidationException.class)
    public void testDeriveHardenedWhenPathEmpty() throws Exception {
        ECKey hashedKey = CryptoUtils.getECKey(CryptoUtils.hashSha512(CryptoUtils.ED25519_KEY, SEED));
        MasterKey unit = new MasterKey(hashedKey);

        ECKey chain_m_0H = unit.deriveHardened(new int[0]);
    }
}