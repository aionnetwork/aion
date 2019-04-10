package org.aion.precompiled.contracts.ATB;

import java.math.BigInteger;
import javax.annotation.Nonnull;
import org.aion.util.bytes.ByteUtil;

public class BridgeDeserializer {

    private static final int ERR_INT = -1;

    private static final int CALL_OFFSET = 4;
    private static final int DWORD_SIZE = 32;
    private static final int ADDR_SIZE = 32;

    /**
     * Size of a "meta" element for defining lists. There are two types, the first is an offset
     * pointer than points to the location of the list.
     *
     * <p>The second is a length, that depicts the length of the list. In compliance with Solidity
     * Encoding in the FVM, both are 16 bytes.
     *
     * <p>We note here that despite being 16 bytes, we only use enough bytes such that the range
     * stays within a positive integer (31 bits).
     */
    private static final int LIST_META = 16;

    private static final int LIST_SIZE_MAX = 512;

    private static final BigInteger INT_MAX_VAL = BigInteger.valueOf(Integer.MAX_VALUE);

    static byte[] parseDwordFromCall(@Nonnull final byte[] call) {
        if (call.length < (DWORD_SIZE + CALL_OFFSET)) return null;
        final byte[] dword = new byte[DWORD_SIZE];
        System.arraycopy(call, CALL_OFFSET, dword, 0, ADDR_SIZE);
        return dword;
    }

    /**
     * Parses a call with one owner address, externally this is known as {@code changeOwner}
     *
     * @implNote assume that input contains function signature
     * @param call input call with function signature
     * @return {@code address} of new owner, {@code null} if anything is invalid
     */
    static byte[] parseAddressFromCall(@Nonnull final byte[] call) {
        byte[] address = parseDwordFromCall(call);
        return isInvalidAddress(address) ? null : address;
    }

    /**
     * Parses a list of addresses from input, currently only used by ring initialization. This
     * method enforces some checks on the class of addresses before parsed, in that they <b>must</b>
     * be user addresses (start with {@code 0xa0}).
     *
     * <p>The implication being that you may not set a non-user, address to be a ring member.
     *
     * @param call input data
     * @return {@code 2d array} containing list of addresses. {@code null} otherwise.
     */
    static byte[][] parseAddressList(@Nonnull final byte[] call) {
        // check minimum length
        if (call.length < CALL_OFFSET + (LIST_META * 2)) return null;

        final byte[][] addressList = parseList(call, CALL_OFFSET, 32);

        if (addressList == null) return null;

        // do a final check for address validity, if you're not sure what is
        // considered a valid address in this contract please see the function
        // below, note the implications with regards to the bridge design
        for (final byte[] l : addressList) {
            if (isInvalidAddress(l)) return null;
        }
        return addressList;
    }

    /**
     * @implNote perhaps a length check is wrongly added here too, we do not want to check later as
     *     other deserialization would be a waste.
     * @param call input data
     * @return {@code BundleRequestCall} containing deserialized data {@code null} if anything
     *     regarding deserialization is wrong
     */
    static BundleRequestCall parseBundleRequest(@Nonnull final byte[] call) {

        // account for 5 lists: sourceTransactionList, addressList, valueList,
        // signatureChunks(1,2,3)
        if (call.length < CALL_OFFSET + DWORD_SIZE + (LIST_META * 2) * 5) return null;

        final byte[] blockHash = parseDwordFromCall(call);

        if (blockHash == null) return null;

        final byte[][] sourceTransactionList = parseList(call, CALL_OFFSET + DWORD_SIZE, 32);
        if (sourceTransactionList == null) return null;

        final byte[][] addressList = parseList(call, CALL_OFFSET + DWORD_SIZE + LIST_META, 32);
        if (addressList == null) return null;

        final byte[][] uintList = parseList(call, CALL_OFFSET + DWORD_SIZE + (LIST_META * 2), 16);
        if (uintList == null) return null;

        // len(addressList) == len(uintList) == len(sourceTransactionList)
        if (addressList.length != uintList.length
                || addressList.length != sourceTransactionList.length) return null;

        final byte[][] signatureChunk1 =
                parseList(call, CALL_OFFSET + (LIST_META * 3) + DWORD_SIZE, 32);
        if (signatureChunk1 == null) return null;

        final byte[][] signatureChunk2 =
                parseList(call, CALL_OFFSET + (LIST_META * 4) + DWORD_SIZE, 32);
        if (signatureChunk2 == null) return null;

        final byte[][] signatureChunk3 =
                parseList(call, CALL_OFFSET + (LIST_META * 5) + DWORD_SIZE, 32);
        if (signatureChunk3 == null) return null;

        // len(signatureChunk1) == len(signatureChunk2) == len(signatureChunk3)
        if (signatureChunk1.length != signatureChunk2.length
                || signatureChunk1.length != signatureChunk3.length) return null;

        final byte[][] mergedSignatureList = new byte[signatureChunk2.length][];
        for (int i = 0; i < signatureChunk2.length; i++) {
            mergedSignatureList[i] =
                    ByteUtil.merge(signatureChunk1[i], signatureChunk2[i], signatureChunk3[i]);
        }

        // assemble bundle

        BridgeTransfer[] bundles = new BridgeTransfer[uintList.length];
        for (int i = 0; i < uintList.length; i++) {
            BridgeTransfer transfer =
                    BridgeTransfer.getInstance(
                            new BigInteger(1, uintList[i]),
                            addressList[i],
                            sourceTransactionList[i]);

            if (transfer == null) return null;

            bundles[i] = transfer;
        }

        return new BundleRequestCall(blockHash, bundles, mergedSignatureList);
    }

    /**
     * Parses a list given an offset, where the offset indicates the index that contains the list
     * metadata (offset) value.
     *
     * <p>Recall that list is encoded in the following pattern: [pointer to list metadata] ... [list
     * metadata (length)][element][...]
     *
     * @implNote we assume that the maximum size of each input will be limited to {@code
     *     Integer.MAX_VALUE}
     * @param call input call (with function signature)
     * @param offset the offset in the call at which to start
     * @param elementLength the length of each element in the array
     * @return {@code array} of byte arrays, or {@code null} if list is improperly formatted
     */
    private static byte[][] parseList(
            @Nonnull final byte[] call, final int offset, final int elementLength) {
        final int callLength = call.length;

        // check minimum length
        if (callLength < offset + (LIST_META * 2)) return null;

        /*
         * Correct case S#1, found that we previously incremented the listOffset before
         * checking for ERR_INT, would have led to a situation where this check
         * (whether the first listOffset was invalid or not) would not trigger.
         *
         * Correct by checking before incrementing with CALL_OFFSET.
         */
        int listOffset = parseMeta(call, offset);
        if (listOffset == ERR_INT) return null;
        listOffset = listOffset + CALL_OFFSET;

        /*
         * parseMeta() performs checks on listOffset.
         */
        final int listLength = parseMeta(call, listOffset);
        if (listLength == ERR_INT) return null;

        /*
         * Covers case Y#2, if attacker tries to construct and overflow to OOM output array,
         * it will be caught here.
         */
        int consumedLength;
        try {
            consumedLength = Math.multiplyExact(listLength, elementLength);
        } catch (ArithmeticException e) {
            return null;
        }

        /*
         * Cover case S#4, assign an upper bound to the size of array we create.
         * Under current gas estimations, our token takes approximately 30k-50k gas for a
         * transfer.
         *
         * 30_000 * 512 = 15_360_000, which is above the current Ethereum block limit.
         * Otherwise, if the limit does increase, we can simply cut bundles at this length.
         */
        if (listLength > LIST_SIZE_MAX) return null;

        /*
         * Recall that we confirmed listOffset <= call.length. To check that offset, we then
         * further consumed the offset position.
         */
        if (consumedLength > (call.length - listOffset - LIST_META)) return null;

        final byte[][] output = new byte[listLength][];

        // ensuring the range is within integer positive values
        int start = listOffset + LIST_META;
        if (start < 0) { // overflow
            return null;
        }
        int end = start + consumedLength;
        if (end < 0) { // overflow
            return null;
        }

        int counter = 0;
        for (int i = start; i < end; i += elementLength) {
            byte[] element = new byte[elementLength];
            System.arraycopy(call, i, element, 0, elementLength);
            output[counter] = element;
            counter++;
        }
        return output;
    }

    private static int parseMeta(@Nonnull final byte[] call, final int offset) {

        // check for out of bounds exceptions, each one of these is important to check since integer
        // overflow can be taken advantage of to pass this block if one of these is omitted.
        if ((offset < 0) || (offset > call.length) || (offset + LIST_META > call.length))
            return ERR_INT;

        // more minimum length checks
        final byte[] pt = new byte[LIST_META];
        System.arraycopy(call, offset, pt, 0, LIST_META);

        // check that destination is parse-able
        final BigInteger bigInt = new BigInteger(1, pt);
        if (bigInt.compareTo(INT_MAX_VAL) > 0) return ERR_INT;

        try {
            return bigInt.intValueExact();
        } catch (ArithmeticException e) {
            /*
             * Catch case S#3, handles the case where someone could overload
             * the biginteger, and try to create a value larger than INT_MAX,
             * in this case we should consider the meta decode a failure and
             * return -1;
             */
            return ERR_INT;
        }
    }

    private static final byte ADDRESS_HEADER = ByteUtil.hexStringToBytes("0xa0")[0];

    /**
     * @implNote something interesting here: enforcing 0xa0 in our address checks prevents people
     *     from sending transactions to random addresses (that do not contain the header), not sure
     *     if this will cause some unintended consequences.
     * @param address the address checked for validity
     * @return {@code true} if address invalid, {@code false} otherwise
     */
    private static boolean isInvalidAddress(byte[] address) {
        return address == null || address.length != 32 || address[0] != ADDRESS_HEADER;
    }
}
