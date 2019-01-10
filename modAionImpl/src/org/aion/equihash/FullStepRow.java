package org.aion.equihash;

import static org.aion.base.util.ByteUtil.intToBytes;

class FullStepRow extends StepRow {

    public FullStepRow(int width, byte[] hashIn, int hInLen, int hLen, int cBitLen, int index)
            throws NullPointerException {
        super(width, hashIn, hInLen, cBitLen);

        byte[] indexBytes = intToBytes(index);
        System.arraycopy(indexBytes, 0, this.getHash(), hLen, indexBytes.length);
    }

    public FullStepRow(int width, FullStepRow a, FullStepRow b, int len, int lenIndices, int trim)
            throws NullPointerException {
        super(width, a);

        // Value of a is checked in super()
        if (b == null) {
            throw new NullPointerException("null FullStepRow");
        }

        // Merge a and b
        for (int i = trim; i < len; i++) {
            this.getHash()[i - trim] = (byte) (a.getHash()[i] ^ b.getHash()[i]);
        }

        if (EquiUtils.indicesBefore(a, b, len, lenIndices)) {
            System.arraycopy(a.getHash(), len, this.getHash(), len - trim, lenIndices);
            System.arraycopy(b.getHash(), len, this.getHash(), len - trim + lenIndices, lenIndices);
        } else {
            System.arraycopy(b.getHash(), len, this.getHash(), len - trim, lenIndices);
            System.arraycopy(a.getHash(), len, this.getHash(), len - trim + lenIndices, lenIndices);
        }
    }
}
