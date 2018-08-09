package org.spongycastle.jcajce.util;

import java.security.Provider;
import java.security.Security;

import org.spongycastle.jce.provider.BouncyCastleProvider;

/**
 * A JCA/JCE helper that refers to the BC provider for all it's needs.
 */
public class BCJcaJceHelper
    extends ProviderJcaJceHelper
{
    private static Provider getBouncyCastleProvider()
    {
        if (Security.getProvider("SC") != null)
        {
            return Security.getProvider("SC");
        }
        else
        {
            return new BouncyCastleProvider();
        }
    }

    public BCJcaJceHelper()
    {
        super(getBouncyCastleProvider());
    }
}
