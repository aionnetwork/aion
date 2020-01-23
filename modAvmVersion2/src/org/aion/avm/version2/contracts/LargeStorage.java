package org.aion.avm.version2.contracts;

import avm.Blockchain;
import java.math.BigInteger;
import org.aion.avm.tooling.abi.Callable;

/**
 * This contract provides methods for adding data to storage. It counts and logs the number of calls
 * made to add/update stored data. It also allows querying the stored data.
 */
public class LargeStorage {

    /**
     * This counter is not intended as a storage key counter. Its purpose is to make changes to the
     * object graph as well as the storage.
     */
    private static BigInteger countPutCalls = BigInteger.ZERO;

    @Callable
    public static void putStorage(byte[] key, byte[] value) {
        Blockchain.putStorage(convertToFittingKey(key), value);
        countPutCalls = countPutCalls.add(BigInteger.ONE);
        Blockchain.log(countPutCalls.toByteArray());
    }

    @Callable
    public static String getStorage(byte[] key) {
        byte[] payload = Blockchain.getStorage(convertToFittingKey(key));
        return (null != payload) ? new String(payload) : null;
    }

    private static byte[] convertToFittingKey(byte[] raw) {
        // The key needs to be 32-bytes so either truncate or 0x0-pad the bytes from the string.
        byte[] key = new byte[32];
        int length = StrictMath.min(key.length, raw.length);
        System.arraycopy(raw, 0, key, 0, length);
        return key;
    }
}
