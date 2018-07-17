package org.aion.wallet.util;

import org.aion.gui.util.AionConstants;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class URLManager {

    private static final Logger log = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    private static final String TRANSACTION_URL = "/#/transaction/";

    public static void openDashboard() {
        openURL(AionConstants.AION_URL);
    }

    public static void openTransaction(final String transactionHash) {
        if (transactionHash != null && !transactionHash.isEmpty()) {
            openURL(AionConstants.AION_URL + TRANSACTION_URL + transactionHash);
        }
    }

    private static void openURL(final String URL) {
        if (URL != null) {
            final String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                try {
                    Desktop.getDesktop().browse(new URI(URL));
                } catch (IOException | URISyntaxException e) {
                    log.error("Exception occurred trying to open website: %s", e.getMessage(), e);
                }
            } else if (os.contains("nix") || os.contains("nux") || os.indexOf("aix") > 0)
                try {
                    if (Runtime.getRuntime().exec(new String[]{"which", "xdg-open"}).getInputStream().read() != -1) {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", URL});
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }
}
