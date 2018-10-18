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

import org.aion.crypto.ed25519.ECKeyEd25519;
import org.junit.Test;
import org.libsodium.jni.encoders.Hex;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class SeededECKeyEd25519Test {
    private static final Hex HEX = new Hex();

    @Test
    public void test() {
        byte[] seed = HEX.decode("e33c0f7d81d843c572275f287498e8d408654fdf0d1e065b84e2e6f157aab09b"
        );
        SeededECKeyEd25519 unit = new SeededECKeyEd25519(seed);
        assertThat(unit.getPrivKeyBytes(), is(HEX.decode(
                "e33c0f7d81d843c572275f287498e8d408654fdf0d1e065b84e2e6f157aab09b732c11131b5abd9ca5c42153765e05e9fff05c184b0eb920d40fd5a69e15be63"
        )));
        assertThat(unit.getPubKey(), is(HEX.decode(
                "732c11131b5abd9ca5c42153765e05e9fff05c184b0eb920d40fd5a69e15be63"
        )));
        assertThat(unit.getAddress(),
                is(new ECKeyEd25519(unit.getPubKey(), unit.getPrivKeyBytes()).getAddress()));
    }
}