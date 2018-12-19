module aion.gui {
    requires aion.log;
    requires aion.mcf;
    requires aion.zero.impl;
    requires aion.base;
    requires aion.crypto;
    requires aion.api.client;
    requires aion.vm.api;

    uses org.aion.log.AionLoggerFactory;

    requires slf4j.api;
    requires javafx.fxml;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.swing;
    requires java.desktop;
    requires java.management;
    requires com.google.common;
    requires BIP39;
    requires richtextfx;
    requires flowless;
    requires libnsc;
    requires core;

    exports org.aion;
    exports org.aion.gui.controller;

    opens org.aion.gui.model to
            com.google.common;
    opens org.aion.gui.controller to
            com.google.common,
            javafx.fxml;
    opens org.aion.gui.controller.partials to
            com.google.common,
            javafx.fxml;
    opens org.aion.gui.views to
            javafx.fxml;
}
