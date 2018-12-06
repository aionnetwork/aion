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

package org.aion.api.server.types;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import org.aion.api.server.nrgprice.NrgOracle;
import org.aion.base.type.AionAddress;
import org.json.JSONObject;
import org.junit.Test;

public class ArgTxCallTest {

    @Test
    public void testFromJsonDefaults() {
        long nrgLimit = 90_000L;
        long nrgPrice = 10L;

        JSONObject tx = new JSONObject();
        NrgOracle nrgOracle = mock(NrgOracle.class);
        when(nrgOracle.getNrgPrice()).thenReturn(nrgPrice);
        ArgTxCall txCall = ArgTxCall.fromJSON(tx, nrgOracle, nrgLimit);

        assertEquals(AionAddress.EMPTY_ADDRESS(), txCall.getFrom());
        assertEquals(AionAddress.EMPTY_ADDRESS(), txCall.getTo());
        assertEquals(0, txCall.getData().length);
        assertEquals(BigInteger.ZERO, txCall.getNonce());
        assertEquals(BigInteger.ZERO, txCall.getValue());
        assertEquals(nrgLimit, txCall.getNrg());
        assertEquals(nrgPrice, txCall.getNrgPrice());
    }
}
