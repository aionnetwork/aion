package org.aion.crypto.jce;

import java.security.Provider;
import org.spongycastle.jce.provider.BouncyCastleProvider;

public final class SpongyCastleProvider {

    private static class Holder {
        private static final Provider INSTANCE = new BouncyCastleProvider();
    }

    public static Provider getInstance() {
        return Holder.INSTANCE;
    }
}
