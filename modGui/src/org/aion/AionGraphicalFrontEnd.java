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

package org.aion;

import java.util.ServiceLoader;
import org.aion.gui.controller.MainWindow;
import org.aion.log.AionLoggerFactory;
import org.aion.zero.impl.config.CfgAion;

/**
 * Entry-point for the graphical front-end for Aion kernel.
 */
public class AionGraphicalFrontEnd {

    public static final String GUI_VERSION = "0.1.0";

    public static void main(String args[]) {
        System.out.println("Starting Aion Kernel GUI v" + GUI_VERSION);

        CfgAion cfg = CfgAion.inst();
        // Initialize logging.  Borrowed from Aion CLI program.
        ServiceLoader.load(AionLoggerFactory.class);
        // Outputs relevant logger configuration
        // TODO the info/error println messages should be presented via GUI
        if (!cfg.getLog().getLogFile()) {
            System.out
                .println("Logger disabled; to enable please check log settings in config.xml\n");
        } else if (!cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            System.out.println("File path is invalid; please check log setting in config.xml\n");
            System.exit(1);
        } else if (cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            System.out.println("Logger file path: '" + cfg.getLog().getLogPath() + "'\n");
        }
        AionLoggerFactory
            .init(cfg.getLog().getModules(), cfg.getLog().getLogFile(), cfg.getLog().getLogPath());

        // Load the UI
        javafx.application.Application.launch(MainWindow.class, args);
    }
}
