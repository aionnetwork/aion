package org.aion.crypto.hash;

public class Blake2bNative {

    public static native byte[] blake256(byte[] in);

    /*
    Generate hashes to validate an Equihash solution
     */
    public static native byte[][] genSolutionHash(
            byte[] personalization, byte[] nonce, int[] indices, byte[] header);

    public static byte[] blake256(byte[] in1, byte[] in2) {
        byte[] arr = new byte[in1.length + in2.length];
        System.arraycopy(in1, 0, arr, 0, in1.length);
        System.arraycopy(in2, 0, arr, in1.length, in2.length);

        return blake256(arr);
    }

    public static byte[][] getSolutionHash(
            byte[] personalization, byte[] nonce, int[] indices, byte[] header) {
        return genSolutionHash(personalization, nonce, indices, header);
    }
}
