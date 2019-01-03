package org.aion.api.server.types;

import static junit.framework.TestCase.assertEquals;

import java.math.BigInteger;
import org.aion.base.type.Address;
import org.json.JSONObject;
import org.junit.Test;

public class ArgTxCallTest {

    @Test
    public void testFromJsonContractCreateDefaults() {
        long nrgLimit = 250_000L;
        long nrgPrice = 10L;

        JSONObject tx = new JSONObject();
        ArgTxCall txCall = ArgTxCall.fromJSON(tx, nrgPrice, 0, nrgLimit);

        assertEquals(Address.EMPTY_ADDRESS(), txCall.getFrom());
        assertEquals(Address.EMPTY_ADDRESS(), txCall.getTo());
        assertEquals(0, txCall.getData().length);
        assertEquals(BigInteger.ZERO, txCall.getNonce());
        assertEquals(BigInteger.ZERO, txCall.getValue());
        assertEquals(nrgLimit, txCall.getNrg());
        assertEquals(nrgPrice, txCall.getNrgPrice());
    }

    @Test
    public void testFromJsonTxDefaults() {
        long nrgLimit = 80_000L;
        long nrgPrice = 10L;

        String toAddr = "0xa076407088416d71467529d8312c24d7596f5d7db75a5c4129d2763df112b8a1";

        JSONObject tx = new JSONObject();

        tx.put("to", toAddr);
        ArgTxCall txCall = ArgTxCall.fromJSON(tx, nrgPrice, nrgLimit, 0);

        assertEquals(Address.EMPTY_ADDRESS(), txCall.getFrom());
        assertEquals(new Address(toAddr), txCall.getTo());
        assertEquals(0, txCall.getData().length);
        assertEquals(BigInteger.ZERO, txCall.getNonce());
        assertEquals(BigInteger.ZERO, txCall.getValue());
        assertEquals(nrgLimit, txCall.getNrg());
        assertEquals(nrgPrice, txCall.getNrgPrice());
    }
}
