package org.aion.crypto.ed25519;

import java.math.BigInteger;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.ISignature;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.file.NativeLoader;
import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;
import org.spongycastle.util.encoders.Hex;

/**
 * ED25519 key implementation based on libsodium.
 *
 * @author yulong
 */
public class ECKeyEd25519 implements ECKey {

    protected static int PUBKEY_BYTES;
    protected static int SECKEY_BYTES;
    protected static int SIG_BYTES;

    /**
     * Indicates the first type of accounts that we have a normal account, this is shared by both
     * regular and contract accounts
     */
    private static final byte DEFAULT_ACCOUNT_ID = ByteUtil.hexStringToBytes("0xA0")[0];

    private final byte[] address;

    static {
        NativeLoader.loadLibrary("sodium");
        NaCl.sodium();
        PUBKEY_BYTES = Sodium.crypto_sign_ed25519_publickeybytes();
        SECKEY_BYTES = Sodium.crypto_sign_ed25519_secretkeybytes();
        SIG_BYTES = Sodium.crypto_sign_ed25519_bytes();
    }

    private byte[] pk;
    private byte[] sk;

    public ECKeyEd25519(byte[] pk, byte[] sk) {
        this.pk = pk;
        this.sk = sk;
        this.address = computeAddress(pk);
    }

    public ECKeyEd25519() {
        pk = new byte[PUBKEY_BYTES];
        sk = new byte[SECKEY_BYTES];
        Sodium.crypto_sign_ed25519_keypair(pk, sk);
        this.address = computeAddress(pk);
    }

    public ECKey fromPrivate(BigInteger privKey) {
        throw new UnsupportedOperationException();
    }

    public ECKey fromPrivate(byte[] bs) {
        if (bs == null || bs.length != SECKEY_BYTES) {
            return null;
        }

        byte[] pk = new byte[PUBKEY_BYTES];
        byte[] sk = bs;
        Sodium.crypto_sign_ed25519_sk_to_pk(pk, sk);
        return new ECKeyEd25519(pk, sk);
    }

    /**
     * Modified address for Q2 testNet, this variant will have one byte reserved for further address
     * space modifications, while the remaining 31-bytes contain a hash[1:] of the PK where [1:]
     * denotes all but the first byte
     */
    public byte[] computeAddress(byte[] pubBytes) {
        return AddressSpecs.computeA0Address(pubBytes);
    }

    public byte[] getAddress() {
        return this.address;
    }

    public byte[] getPubKey() {
        return pk;
    }

    /**
     * Signs a message with this key.
     *
     * @param msg
     * @return
     */
    public ISignature sign(byte[] msg) {
        byte[] sig = new byte[SIG_BYTES];
        int[] sigLen = new int[1];

        int ret = Sodium.crypto_sign_ed25519_detached(sig, sigLen, msg, msg.length, sk);
        if (ret != 0) {
            throw new RuntimeException("Failed to sign message!");
        }

        return new Ed25519Signature(pk, sig);
    }

    /**
     * Verifies if a signature is valid or not.
     *
     * @param msg
     * @param sig
     * @param pk
     * @return
     */
    public static boolean verify(byte[] msg, byte[] sig, byte[] pk) {
        if (msg == null || sig == null || pk == null) {
            return false;
        }

        return 0 == Sodium.crypto_sign_ed25519_verify_detached(sig, msg, msg.length, pk);
    }

    public byte[] getPrivKeyBytes() {
        return sk;
    }

    @Override
    public String toString() {
        // TODO: why is this using Hex from spongycastle lib?
        return "pub:" + Hex.toHexString(pk);
    }
}
