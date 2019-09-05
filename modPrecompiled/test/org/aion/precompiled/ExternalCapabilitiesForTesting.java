package org.aion.precompiled;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.aion.precompiled.type.IExternalCapabilitiesForPrecompiled;
import org.aion.precompiled.util.ByteUtil;

public class ExternalCapabilitiesForTesting implements IExternalCapabilitiesForPrecompiled {

    @Override
    public boolean verifyISig(byte[] hash, byte[] signature) {
        return !Arrays.equals(signature, new byte[64]);
    }

    @Override
    public byte[] blake2b(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot find MessageDigest Class");
        }
    }

    @Override
    public byte[] keccak256(byte[] bytes) {
        byte[] rev = reverseBytes(bytes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(rev);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot find MessageDigest Class");
        }
    }

    @Override
    public boolean verifyEd25519Signature(byte[] bytes, byte[] bytes1, byte[] bytes2) {
        return true;
    }

    @Override
    public boolean verifyEd25519Signature(byte[] bytes, byte[] signature) {
        return true;
    }

    @Override
    public byte[] getEd25519Address(byte[] signature) {
        byte[] addr = new byte[32];
        System.arraycopy(signature, 0, addr, 0, 32);
        return addr;
    }

    @Override
    public byte[] getISigAddress(byte[] signature) {
        byte[] addr = new byte[32];
        System.arraycopy(signature, 0, addr, 0, 32);
        return addr;
    }

    @Override
    public long calculateTransactionCost(byte[] data, boolean isCreate) {
        long zeroes = zeroBytesInData(data);
        long nonZeroes = data.length - zeroes;

        return 21000 + zeroes * 4 + nonZeroes * 64;
    }

    // This needs to be the reverse operation of getISigAddress
    public byte[] sign(byte[] addr, byte[] message) {
        byte[] sig = new byte[96];
        for (int i = 0; i < 32; i++) {
            sig[i] = addr[i];
            sig[i + 32] = message[i];
            sig[i + 64] = message[i];
        }
        return sig;
    }

    /**
     * Returns an address of with identifier A0, given the public key of the account (this is
     * currently our only account type)
     */
    public byte[] computeA0Address(byte[] publicKey) {
        byte[] addr = new byte[32];
        System.arraycopy(publicKey, 0, addr, 0, 32);
        addr[0] = ByteUtil.hexStringToBytes("0xA0")[0];
        return addr;
    }

    private static byte[] reverseBytes(byte[] bytes) {
        int length = bytes.length;
        byte[] rev = new byte[length];
        for (int i = 0; i < length; i++) {
            rev[i] = bytes[length - 1 - i];
        }
        return rev;
    }

    private static long zeroBytesInData(byte[] data) {
        if (data == null) {
            return 0;
        }

        int c = 0;
        for (byte b : data) {
            c += (b == 0) ? 1 : 0;
        }
        return c;
    }
}
