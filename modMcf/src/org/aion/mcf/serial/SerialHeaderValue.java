package org.aion.mcf.serial;

/**
 * Not currently used yet, will use in future to determine type during deserialization
 *
 * @author yao
 */
public class SerialHeaderValue {
    public static final int BLOCK = 0x0;
    public static final int BLOCKDATA = 0x1;
    public static final int BLOCKHEADER = 0x2;
    public static final int COMMIT = 0x3;
    public static final int PROPOSAL = 0x4;
    public static final int TX = 0x5;
    public static final int PART = 0x6;
    public static final int PARTSETHEADER = 0x7;

    public static String[] serialHeaderString = {
        "block", "blockData", "blockHeader", "commit", "proposal"
    };

    public String getHeaderType(int a) {
        return serialHeaderString[a];
    }
}
