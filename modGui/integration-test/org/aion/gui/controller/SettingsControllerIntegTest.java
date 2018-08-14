package org.aion.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.WindowControlsEvent;
import org.aion.gui.model.ApplyConfigResult;
import org.aion.gui.model.ConfigManipulator;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.views.XmlArea;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testfx.api.FxAssert.verifyThat;

public class SettingsControllerIntegTest extends ApplicationTest {
    private ControllerFactory cf;
    private ConfigManipulator configManipulator;
    private SettingsController controller;
    private KernelConnection kc;
    private String configFileContents = "<fake><config><content><here>";
    private String configFileName = "/tmp/absolute/path/to/the/Config/File/Name";
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
        when(configManipulator.configFile()).thenReturn(new File(configFileName));
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
                SettingsController.class.getResource("components/partials/SettingsPane.fxml"),
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

        AnchorPane ap = new AnchorPane(root);
        ap.setPrefWidth(860);
        ap.setPrefHeight(570);

        stage.setScene(new Scene(ap));
        stage.show();
        stage.toFront();

        lookup("#settingsPane").query().setVisible(true);

        EventBusRegistry.INSTANCE.getBus(WindowControlsEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
    }

    /**
     * Use case:
     *  - open settings pane
     *  - config file's contents should be in the text box
     * @throws Exception
     */
    @Test
    public void testInitialState() throws Exception {
        XmlArea xml = lookup("#xmlArea").query();
        assertThat(xml.getText(), is(configFileContents));
        verifyThat(lookup("#editingFileLabel"),
                LabeledMatchers.hasText("Editing " + new File(configFileName).getAbsolutePath()));
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
        clickOn("#resetButton");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(xml.getText(), is(configFileContents));
    }

    /**
     * Use case:
     *  - open settings pane
     *  - input config xml that contains errors
     *  - click apply and save
     *  - should get an error
     * @throws Exception
     */
    @Test
    public void testSaveApplyWhenError() throws Exception {
        String errorMsg = "you're configging it wrong";
        when(configManipulator.applyNewConfig(anyString()))
                .thenReturn(new ApplyConfigResult(false, errorMsg, null));

        XmlArea xml = lookup("#xmlArea").query();
        Platform.runLater( () -> xml.setText("anyText") );

        WaitForAsyncUtils.waitForFxEvents();

        clickOn("#applyAndSaveButton");
        WaitForAsyncUtils.waitForFxEvents();

        Stage alertBox = getAlertBox();
        assertThat("An alert box should pop up", alertBox, is(notNullValue()));
        DialogPane alertDialog = dialogPaneOfAlertBox(alertBox);
        assertThat(alertDialog.getHeaderText(), is("Error"));
        assertThat(alertDialog.getContentText(), is(errorMsg));

        Platform.runLater(() -> ((Button)alertDialog.lookupButton(ButtonType.OK)).fire());
    }

    @Test
    public void testSaveApplyWhenSuccessful() throws Exception {
        String msg = "it worked!";
        when(configManipulator.applyNewConfig(anyString()))
                .thenReturn(new ApplyConfigResult(true, msg, null));

        XmlArea xml = lookup("#xmlArea").query();
        Platform.runLater( () -> xml.setText("anyText") );
        WaitForAsyncUtils.waitForFxEvents();
        clickOn("#applyAndSaveButton");
        WaitForAsyncUtils.waitForFxEvents();

        Stage alertBox = getAlertBox();
        assertThat("An alert box should pop up", alertBox, is(notNullValue()));
        DialogPane alertDialog = dialogPaneOfAlertBox(alertBox);
        assertThat(alertDialog.getHeaderText(), is("Confirmation"));
        assertThat(alertDialog.getContentText(), is(msg));
        Platform.runLater(() -> ((Button)alertDialog.lookupButton(ButtonType.OK)).fire());
    }

    private DialogPane dialogPaneOfAlertBox(Stage alertBox) {
        return (DialogPane) alertBox.getScene().getRoot();
    }

    private Stage getAlertBox() {
        // based on
        // https://stackoverflow.com/questions/48565782/testfx-how-to-test-validation-dialogs-with-no-ids
        final List<Window> allWindows = new ArrayList<>(robotContext().getWindowFinder().listWindows());
        Collections.reverse(allWindows);

        return (Stage) allWindows
                .stream()
                .filter(window -> window instanceof Stage
                        && ((Stage) window).getModality() == Modality.APPLICATION_MODAL)
                .findFirst()
                .orElse(null);
    }
}