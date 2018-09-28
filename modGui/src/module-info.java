module aion.gui {
    requires aion.log;
    requires aion.mcf;
    requires aion.zero.impl;
    requires aion.base;
    requires aion.crypto;
    requires aion.api.client;

    requires slf4j.api;

    requires javafx.fxml;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.swing;
    requires java.desktop;
    requires java.management;

    requires BIP39;

    exports org.aion;
}
