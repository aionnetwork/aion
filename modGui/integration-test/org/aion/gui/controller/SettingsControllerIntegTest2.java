package org.aion.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.WindowControlsEvent;
import org.aion.gui.model.ConfigManipulator;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.views.XmlArea;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SettingsControllerIntegTest2 extends ApplicationTest {
    private ControllerFactory cf;
    private ConfigManipulator configManipulator;
    private SettingsController controller;
    private KernelConnection kc;
    private String configFileContents = "<fake><config><content><here>";
    private Stage stage;
    private EventBusRegistry ebr;
    private final Map<HeaderPaneButtonEvent.Type, Node> panes = new HashMap<>();
    private AccountManager accountManager;
    private ConsoleManager consoleManager;

    /**
     * Not using Mockito's @Before because JavaFX's start() runs first and that needs member
     * vars already set up.  Will just call this init method from start.
     */
    @Override
    public void init() {
        configManipulator = mock(ConfigManipulator.class);
        when(configManipulator.loadFromConfigFile()).thenReturn(configFileContents);
        when(configManipulator.getLastLoadedContent()).thenReturn(configFileContents);
        kc = mock(KernelConnection.class);
        ebr = new EventBusRegistry();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;

        cf = new ControllerFactory()
                .withConfigManipulator(configManipulator)
                .withKernelConnection(kc)
                .withEventBusRegistry(ebr);
        FXMLLoader loader = new FXMLLoader(
                SettingsController.class.getResource("components/TestWindow.fxml"),
//                SettingsController.class.getResource("MainWindow.fxml"),
                null,
                null,
                cf,
                Charset.forName(DEFAULT_CHARSET_NAME),
                new LinkedList<>());
        loader.setBuilderFactory(new UiSubcomponentsFactory()
                .withAccountManager(accountManager)
                .withConsoleManager(consoleManager)
        );
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root));
        stage.show();
        stage.toFront();

//        panes.put(HeaderPaneButtonEvent.Type.SETTINGS, stage.getScene().lookup("#settingsPane"));

        EventBusRegistry.INSTANCE.getBus(WindowControlsEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
    }

    @Test
    public void testInitialState() throws Exception {
        XmlArea xml = lookup("#xmlArea").query();
        assertThat(xml.getText(), is(configFileContents));
    }

    /**
     * Use case:
     *  - open settings pane
     *  - make a modification in the text box
     *  - click the reset button
     *  - text box should contain original config file contents
     * @throws Exception
     */
    @Test
    public void testReset() throws Exception {

        XmlArea xml = lookup("#xmlArea").query();
        Platform.runLater( () -> xml.setText("something new") );
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(xml.getText(), is("something new"));
        Button resetButton = lookup("#resetButton").query();
        clickOn("#resetButton");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(xml.getText(), is(configFileContents));
    }
}