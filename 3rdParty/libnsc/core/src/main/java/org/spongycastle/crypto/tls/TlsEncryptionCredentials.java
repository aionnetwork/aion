package org.spongycastle.crypto.tls;

import java.io.IOException;

public interface TlsEncryptionCredentials extends TlsCredentials
{
    byte[] decryptPreMasterSecret(byte[] encryptedPreMasterSecret)
        throws IOException;
}
