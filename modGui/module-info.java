module aion.boot {
    requires aion.log;
    requires aion.mcf;
    requires aion.zero.impl;

    requires slf4j.api;

    requires javafx.fxml;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires java.desktop;

    exports org.aion;
}
