package org.spongycastle.crypto.params;

import org.spongycastle.crypto.DerivationParameters;

/**
 * parameters for Key derivation functions for IEEE P1363a
 */
public class KDFParameters
    implements DerivationParameters
{
    byte[]  iv;
    byte[]  shared;

    public KDFParameters(
        byte[]  shared,
        byte[]  iv)
    {
        this.shared = shared;
        this.iv = iv;
    }

    public byte[] getSharedSecret()
    {
        return shared;
    }

    public byte[] getIV()
    {
        return iv;
    }
}
