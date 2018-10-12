/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.wallet.util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.aion.gui.util.AionConstants;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

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
            } else if (os.contains("nix") || os.contains("nux") || os.indexOf("aix") > 0) {
                try {
                    if (Runtime.getRuntime().exec(new String[]{"which", "xdg-open"})
                        .getInputStream().read() != -1) {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", URL});
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
