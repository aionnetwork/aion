package org.aion.util.types;

import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;

public class AddressUtils {
    public static AionAddress ZERO_ADDRESS = new AionAddress(new byte[32]);

    public static AionAddress wrapAddress(String addressString) {
        if (addressString == null) {
            throw new IllegalArgumentException();
        } else {
            byte[] hexByte = ByteUtil.hexStringToBytes(addressString);
            return new AionAddress(hexByte);
        }
    }
}
