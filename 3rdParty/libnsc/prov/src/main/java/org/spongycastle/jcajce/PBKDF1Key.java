package org.spongycastle.jcajce;

import org.spongycastle.crypto.CharToByteConverter;

/**
 * A password based key for use with PBKDF1 as defined in PKCS#5.
 */
public class PBKDF1Key
    implements PBKDFKey
{
    private final char[] password;
    private final CharToByteConverter converter;

    /**
     * Basic constructor for a password based key with generation parameters for PBKDF1.
     *
     * @param password password to use.
     * @param converter the converter to use to turn the char array into octets.
     */
    public PBKDF1Key(char[] password, CharToByteConverter converter)
    {
        this.password = new char[password.length];
        this.converter = converter;

        System.arraycopy(password, 0, this.password, 0, password.length);
    }

    /**
     * Return a reference to the char[] array holding the password.
     *
     * @return a reference to the password array.
     */
    public char[] getPassword()
    {
        return password;
    }

    /**
     * Return the password based key derivation function this key is for,
     *
     * @return the string "PBKDF1"
     */
    public String getAlgorithm()
    {
        return "PBKDF1";
    }

    /**
     * Return the format encoding.
     *
     * @return the type name representing a char[] to byte[] conversion.
     */
    public String getFormat()
    {
        return converter.getType();
    }

    /**
     * Return the password converted to bytes.
     *
     * @return the password converted to a byte array.
     */
    public byte[] getEncoded()
    {
        return converter.convert(password);
    }
}
