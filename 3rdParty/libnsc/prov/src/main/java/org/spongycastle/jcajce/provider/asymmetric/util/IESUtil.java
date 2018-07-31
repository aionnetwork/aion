package org.spongycastle.jcajce.provider.asymmetric.util;

import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.jce.spec.IESParameterSpec;

public class IESUtil
{
    public static IESParameterSpec guessParameterSpec(BufferedBlockCipher iesBlockCipher)
    {
        if (iesBlockCipher == null)
        {
            return new IESParameterSpec(null, null, 128);
        }
        else
        {
            BlockCipher underlyingCipher = iesBlockCipher.getUnderlyingCipher();

            if (underlyingCipher.getAlgorithmName().equals("DES") ||
                underlyingCipher.getAlgorithmName().equals("RC2") ||
                underlyingCipher.getAlgorithmName().equals("RC5-32") ||
                underlyingCipher.getAlgorithmName().equals("RC5-64"))
            {
                return new IESParameterSpec(null, null, 64, 64);
            }
            else if (underlyingCipher.getAlgorithmName().equals("SKIPJACK"))
            {
                return new IESParameterSpec(null, null, 80, 80);
            }
            else if (underlyingCipher.getAlgorithmName().equals("GOST28147"))
            {
                return new IESParameterSpec(null, null, 256, 256);
            }

            return new IESParameterSpec(null, null, 128, 128);
        }
    }
}
