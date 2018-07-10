package org.aion.precompiled.contracts.ATB;

import org.aion.api.sol.impl.Int;
import org.aion.base.util.ByteUtil;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public class BridgeDeserializer {

    private static final int ERR_INT = -1;

    private static final int CALL_OFFSET = 4;
    private static final int ADDR_SIZE = 32;

    private static final int LIST_PT = 16;
    private static final int LIST_META = 16;

    private static final BigInteger INT_MAX_VAL = BigInteger.valueOf(Integer.MAX_VALUE);

    /**
     * Parses a call to {@code setNewOwner}, externally this is known as
     * {@code changeOwner}
     *
     * @implNote assume that input contains function signature
     *
     * @param call input call with function signature
     * @return {@code address} of new owner, {@code null} if anything is invalid
     */
    public static byte[] parseAddressFromCall(byte[] call) {
        if (call.length != (ADDR_SIZE + CALL_OFFSET))
            return null;
        byte[] address = new byte[ADDR_SIZE];
        System.arraycopy(call, CALL_OFFSET, address, 0, ADDR_SIZE);
        if (!checkAddressValidity(address))
            return null;
        return address;
    }

    /**
     * // TODO: should we re-organize this into a class based structure
     *
     * @param call
     * @return
     */
    private static byte[][][] parseBundleRequest(byte[] call) {
        // TODO
        return null;
    }

    /**
     * Parses a list given an offset, where the offset indicates the index that
     * contains the list metadata (offset) value.
     *
     * Recall that list is encoded in the following pattern:
     * [pointer to list metadata] ... [list metadata (length)][element][...]
     *
     * @implNote we assume that the maximum size of each input will be limited to
     * {@code Integer.MAX_VALUE}
     *
     * @param call input call (with function signature)
     * @param offset the offset in the call at which to start
     * @param elementLength the length of each element in the array
     * @return {@code array} of byte arrays, or {@code null} if list is improperly formatted
     */
    private static byte[][] parseList(@Nonnull final byte[] call,
                                      final int offset,
                                      final int elementLength) {
        final int callLength = call.length;

        // check minimum length
        if (callLength < CALL_OFFSET + LIST_META + LIST_PT)
            return null;

        int listOffset = parseMeta(call, offset);
        if (listOffset == ERR_INT)
            return null;

        if (listOffset > call.length)
            return null;

        int listLength = parseMeta(call, listOffset);
        if (listLength == ERR_INT)
            return null;

        // TODO:
        return null;
    }

    private static int parseMeta(byte[] call, int offset) {
        // more minimum length checks
        final byte[] pt = new byte[LIST_PT];
        System.arraycopy(call, offset, pt, 0, LIST_PT);

        // check that destination is parse-able
        final BigInteger bigInt = new BigInteger(1, pt);
        if (bigInt.compareTo(INT_MAX_VAL) > 0)
            return ERR_INT;

        // check that destination is within bounds
        return bigInt.intValueExact();
    }

    private static byte ADDRESS_HEADER = ByteUtil.hexStringToBytes("0xa0")[0];

    /**
     * @implNote something interesting here: enforcing 0xa0 in our address checks
     * prevents people from sending transactions to random addresses (that do not contain
     * the header), not sure if this will cause some unintended consquences.
     *
     * @param address
     * @return {@code true} if address valid, {@code false} otherwise
     */
    private static boolean checkAddressValidity(byte[] address) {
        if (address.length != 32)
            return false;
        if (address[0] != ADDRESS_HEADER)
            return false;
        return true;
    }
}
