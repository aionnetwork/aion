package org.aion.wallet.util;

public class ConfigUtils {

    public static final String WALLET_API_ENABLED_FLAG = "wallet.api.enabled";

    public static boolean isEmbedded() {
        return !Boolean.valueOf(System.getProperty(WALLET_API_ENABLED_FLAG));
    }
}
