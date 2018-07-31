package org.spongycastle.crypto.modes.gcm;

public interface GCMExponentiator
{
    void init(byte[] x);
    void exponentiateX(long pow, byte[] output);
}
