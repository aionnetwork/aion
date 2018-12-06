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
