package org.aion.equihash;

class StepRow {

    private int width;
    private byte[] hash;

    /** @throws NullPointerException when given null input */
    StepRow(int width, byte[] hashIn, int hInLen, int cBitLength) {
        if (hashIn == null) throw new NullPointerException("Null hashIn");

        this.width = width;
        this.hash = new byte[width];

        // Byte pad is 0 based on the equihash specification
        EquiUtils.extendArray(hashIn, hInLen, this.hash, cBitLength, 0);
    }

    StepRow(int width, StepRow a) {
        this.hash = new byte[width];
        System.arraycopy(a.getHash(), 0, this.getHash(), 0, a.width);
    }

    public byte[] getHash() {
        return hash;
    }

    public boolean isZero(int len) {
        for (int i = 0; i < len; i++) {
            if (this.getHash()[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
