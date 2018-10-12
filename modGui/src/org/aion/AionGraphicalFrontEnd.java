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
