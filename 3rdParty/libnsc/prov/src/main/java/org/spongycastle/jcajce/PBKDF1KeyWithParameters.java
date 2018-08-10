package org.spongycastle.jcajce;

import javax.crypto.interfaces.PBEKey;

import org.spongycastle.crypto.CharToByteConverter;
import org.spongycastle.util.Arrays;

/**
 * A password based key for use with PBKDF1 as defined in PKCS#5 with full PBE parameters.
 */
public class PBKDF1KeyWithParameters
    extends PBKDF1Key
    implements PBEKey
{
    private final byte[] salt;
    private final int iterationCount;

    /**
     * Basic constructor for a password based key with generation parameters for PBKDF1.
     *
     * @param password password to use.
     * @param converter the converter to use to turn the char array into octets.
     * @param salt salt for generation algorithm
     * @param iterationCount iteration count for generation algorithm.
     */
    public PBKDF1KeyWithParameters(char[] password, CharToByteConverter converter, byte[] salt, int iterationCount)
    {
        super(password, converter);

        this.salt = Arrays.clone(salt);
        this.iterationCount = iterationCount;
    }

    /**
     * Return the salt to use in the key derivation function.
     *
     * @return the salt to use in the KDF.
     */
    public byte[] getSalt()
    {
        return salt;
    }

    /**
     * Return the iteration count to use in the key derivation function.
     *
     * @return the iteration count to use in the KDF.
     */
    public int getIterationCount()
    {
        return iterationCount;
    }
}
