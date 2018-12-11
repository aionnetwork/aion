package org.aion.base.util;

import static org.aion.base.util.ByteUtil.hexStringToBytes;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ByteArrayWrapperTest {

    /** @return input values for {@link #testWrap(String)} */
    @SuppressWarnings("unused")
    private Object hexValues() {

        List<Object> parameters = new ArrayList<>();

        parameters.add("");
        parameters.add("eE55fF66eE55fF66eE55fF66eE55fF66");
        parameters.add("aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44");
        parameters.add("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        parameters.add("0000000000000000000000000000000000000000000000000000000000000000");
        parameters.add("0000000000000000000000000000000000000000000000000000000000000001");

        return parameters.toArray();
    }

    /** 1. Wrap the input data 2. Assert to see if equal */
    @Test
    @Parameters(method = "hexValues")
    public void testWrap(String inputString) {

        ByteArrayWrapper tempArray;
        byte[] inputByte = hexStringToBytes(inputString);

        try {
            tempArray = ByteArrayWrapper.wrap(inputByte);
            assertEquals(tempArray.toString(), inputString.toLowerCase());
            assertEquals(tempArray.toBytes(), tempArray.getData());
            System.out.println("Valid " + tempArray);
        } catch (NullPointerException e) {
            System.out.println("Invalid");
        }
    }

    @Test
    public void testCollision() {
        java.util.HashMap<ByteArrayWrapper, Object> map = new java.util.HashMap<>();

        for (int i = 0; i < 2000; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(20);
            buffer.putInt(i);

            map.put(new ByteArrayWrapper(buffer.array()), new Object());
        }

        int cnt1 = 0;
        for (ByteArrayWrapper k : map.keySet()) {
            if (map.get(k) == null) {
                System.out.println("1111 " + k);
                cnt1++;
            }
        }

        int cnt2 = 0;
        for (java.util.Map.Entry<ByteArrayWrapper, Object> e : map.entrySet()) {
            if (e.getValue() == null) {
                System.out.println("2222 " + e);
                cnt2++;
            }
        }

        assertEquals(0, cnt1);
        assertEquals(0, cnt2);
    }
}
