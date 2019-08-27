package org.aion.zero.impl.blockchain;

import org.aion.util.conversions.Hex;

/** Block identifier holds block hash and number <br> */
public class BlockIdentifier {

    /** Block hash */
    private byte[] hash;

    /** Block number */
    private long number;

    public BlockIdentifier(byte[] hash, long number) {
        this.hash = hash;
        this.number = number;
    }

    public byte[] getHash() {
        return hash;
    }

    public long getNumber() {
        return number;
    }

    @Override
    public String toString() {
        return "BlockIdentifierImpl {"
                + "hash="
                + Hex.toHexString(hash)
                + ", number="
                + number
                + '}';
    }
}
