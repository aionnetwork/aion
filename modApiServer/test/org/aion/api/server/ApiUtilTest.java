package org.aion.api.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import org.aion.util.bytes.ByteUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class ApiUtilTest {

    private byte vers, retCode;

    private byte errorLength;

    private byte[] hash;
    private byte[] error;
    private byte[] result;

    public ApiUtilTest() {
        errorLength = 7;
        byte resultLength = 6;
        vers = RandomUtils.nextBytes(1)[0];
        retCode = RandomUtils.nextBytes(1)[0];
        hash = RandomUtils.nextBytes(ApiUtil.HASH_LEN);
        error = RandomUtils.nextBytes(errorLength);
        result = RandomUtils.nextBytes(resultLength);
        System.out.println("vers set to " + vers);
        System.out.println("retCode set to " + retCode);
        System.out.println("hash set to " + ByteUtil.toHexString(hash));
        System.out.println("error set to " + ByteUtil.toHexString(error));
        System.out.println("result set to " + ByteUtil.toHexString(result));
    }

    @Test
    public void testNulls() {

        assertNull(ApiUtil.toReturnHeader(vers, retCode, null));
        assertNull(ApiUtil.toReturnHeader(vers, retCode, null, null));
        assertNull(ApiUtil.toReturnHeader(vers, retCode, null, null, null));
        assertNull(ApiUtil.combineRetMsg(null, null));
        assertNull(ApiUtil.combineRetMsg(null, (byte) 1));
        assertNull(ApiUtil.getApiMsgHash(null));
    }

    @Test
    public void testHeader() {

        byte[] header = ApiUtil.toReturnHeader(vers, retCode);
        assertEquals(vers, header[0]);
        assertEquals(retCode, header[1]);
        assertEquals(0, header[2]);
    }

    @Test
    public void testHeaderHash() {

        byte[] header = ApiUtil.toReturnHeader(vers, retCode, hash);
        assertEquals(vers, header[0]);
        assertEquals(retCode, header[1]);
        assertEquals(1, header[2]);
        assertArrayEquals(hash, Arrays.copyOfRange(header, 3, header.length));
    }

    @Test
    public void testHeaderHashError() {

        byte[] header = ApiUtil.toReturnHeader(vers, retCode, hash, error);
        assertEquals(vers, header[0]);
        assertEquals(retCode, header[1]);
        assertEquals(1, header[2]);
        assertArrayEquals(hash, Arrays.copyOfRange(header, 3, ApiUtil.HASH_LEN + 3));
        assertEquals(error.length, header[ApiUtil.HASH_LEN + 3]);
        assertArrayEquals(error, Arrays.copyOfRange(header, ApiUtil.HASH_LEN + 4, header.length));

        error = new byte[0];
        header = ApiUtil.toReturnHeader(vers, retCode, hash, error);
        assertEquals(vers, header[0]);
        assertEquals(retCode, header[1]);
        assertEquals(1, header[2]);
        assertArrayEquals(hash, Arrays.copyOfRange(header, 3, header.length - 1));
        assertEquals(0, header[header.length - 1]);
    }

    @Test
    public void testHeaderHashErrorResult() {

        byte[] header = ApiUtil.toReturnHeader(vers, retCode, hash, error, result);
        assertEquals(vers, header[0]);
        assertEquals(retCode, header[1]);
        assertEquals(1, header[2]);
        assertArrayEquals(hash, Arrays.copyOfRange(header, 3, ApiUtil.HASH_LEN + 3));
        assertEquals(error.length, header[ApiUtil.HASH_LEN + 3]);
        assertArrayEquals(
                error,
                Arrays.copyOfRange(
                        header, ApiUtil.HASH_LEN + 4, ApiUtil.HASH_LEN + errorLength + 4));
        assertArrayEquals(
                result,
                Arrays.copyOfRange(header, ApiUtil.HASH_LEN + errorLength + 4, header.length));

        error = new byte[0];
        header = ApiUtil.toReturnHeader(vers, retCode, hash, error, result);
        assertEquals(vers, header[0]);
        assertEquals(retCode, header[1]);
        assertEquals(1, header[2]);
        assertArrayEquals(hash, Arrays.copyOfRange(header, 3, ApiUtil.HASH_LEN + 3));
        assertEquals(0, header[ApiUtil.HASH_LEN + 3]);
        assertArrayEquals(result, Arrays.copyOfRange(header, ApiUtil.HASH_LEN + 4, header.length));
    }

    @Test
    public void testReturnEvtHeader() {

        byte[] header = ApiUtil.toReturnEvtHeader(vers, result);
        assertEquals(vers, header[0]);
        assertEquals(106, header[1]);
        assertEquals(0, header[2]);
        assertArrayEquals(result, Arrays.copyOfRange(header, 3, header.length));
    }

    @Test
    public void testCombineRetMsg() {

        byte[] header = ApiUtil.combineRetMsg(hash, (byte) 5);
        assertArrayEquals(hash, Arrays.copyOfRange(header, 0, hash.length));
        assertEquals(5, header[hash.length]);

        header = ApiUtil.combineRetMsg(hash, result);
        assertArrayEquals(hash, Arrays.copyOfRange(header, 0, hash.length));
        assertArrayEquals(result, Arrays.copyOfRange(header, hash.length, header.length));
    }
}
