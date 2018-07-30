package org.aion.gui.controller;

import com.google.common.eventbus.Subscribe;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.WindowControlsEvent;
import org.aion.gui.model.GeneralKernelInfoRetriever;
import org.aion.gui.model.IApiMsgErrorHandler;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.gui.model.SimpleApiMsgErrorHandler;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgGuiLauncher;
import org.aion.os.KernelLauncher;
import org.aion.os.UnixKernelProcessHealthChecker;
import org.aion.os.UnixProcessTerminator;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;

/**
 * Top-level class of the JavaFX Application.
 */
public class MainWindow extends Application {
    /*
     * Implementation/design notes:
     *
     * The GUI application code uses a variation of MVC.  For us, we'll divide up the
     * responsibilities into the following layers:
     *
     * 1) Model: any class that interfaces with components outside of the GUI; i.e. KernelLauncher
     *    for interface with the OS to launch processes; KernelConnection provides interface to
     *    Aion API.  The state of Aion API lives in the model layer.  The model does not know about
     *    Controller or View, it just publishes events into the EventBus.
     *
     * 2) View: the layer that contains the UI elements; i.e. all of the JavaFX .fxml files.  The
     *    elements in the View (i.e. a Button) have associated handler methods, which are part of
     *    Controller.  Through these associated methods, View knows about Controller and
     *    delegates control to classes/methods in Controller.  The View is very "dumb" and doesn't
     *    have logic to modify itself.
     *
     * 3) Controller: this layer mediates interaction between Model and View by serving two purposes:
     *      (a) Contains handlers to which the View is registered (through the view's FXML).
     *          Usually these handlers manipulate Model through some method call.  The heavy
     *          lifting of the logic should live in Model.
     *      (b) Subscribe to events published by Model and update View accordingly.  Events that it
     *          may need to handle include kernel state change (e.g. # of peers).  EventBus is the
     *          mechanism used to facilitate PubSub pattern.
     *    Generally, each View component has one or zero accompanying classes that are part of the
     *    Controller layer (usually a class name ending with "Controller").  Within it are the
     *    handlers for its corresponding View and also handlers for Model events that would cause a
     *    change in that View component.
     *
     * Depending on how the code develops we can revisit this.  The fact that Controller layer has
     * two purposes may end up getting unwieldy; if so, maybe move (a) into the responsibility of
     * View or call it out as something else ("View controller"?).
     */

    private double xOffset;
    private double yOffset;
    private Stage stage;

    private final UnixKernelProcessHealthChecker unixKernelProcessHealthChecker;
    private final KernelUpdateTimer timer;
    private final KernelLauncher kernelLauncher;

    private final Map<HeaderPaneButtonEvent.Type, Node> panes = new HashMap<>();

    private static final String TITLE = "Aion Kernel";
    private static final String MAIN_WINDOW_FXML = "MainWindow.fxml";
    private static final String AION_LOGO = "components/icons/aion_logo.png";

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    public MainWindow() {
        timer = new KernelUpdateTimer(Executors.newSingleThreadScheduledExecutor());
        unixKernelProcessHealthChecker = new UnixKernelProcessHealthChecker();
        kernelLauncher = new KernelLauncher(
                CfgAion.inst().getGui().getCfgGuiLauncher(),
                EventBusRegistry.INSTANCE,
                new UnixProcessTerminator(),
                unixKernelProcessHealthChecker);
    }

    /** This impl contains start-up code to make the GUI more fancy.  Lifted from aion_ui.  */
    @Override
    public void start(Stage stage) throws Exception {
        // Set up Cfg and Logger
//        CfgAion cfg = CfgAion.inst();
//        initLogger(cfg);
        LOG.info("Starting UI");

        // Set up JavaFX stage and root
        this.stage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AION_LOGO)));

        // Set up root window and stage and register basic UI event handlers
        Parent root = loader().load();
        root.setOnMousePressed(this::handleMousePressed);
        root.setOnMouseDragged(this::handleMouseDragged);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        stage.setOnCloseRequest(t -> shutDown());

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();

        panes.put(HeaderPaneButtonEvent.Type.OVERVIEW, scene.lookup("#overviewPane"));
        panes.put(HeaderPaneButtonEvent.Type.SETTINGS, scene.lookup("#settingsPane"));

        // Set up event bus
        registerEventBusConsumer();

        kernelLauncher.tryResume();
    }

    private FXMLLoader loader() {
        IApiMsgErrorHandler errorHandler = new SimpleApiMsgErrorHandler();
        KernelConnection kc = new KernelConnection(
                CfgAion.inst().getApi(),
                EventBusRegistry.INSTANCE.getBus(EventBusRegistry.KERNEL_BUS));
        FXMLLoader loader = new FXMLLoader((getClass().getResource(MAIN_WINDOW_FXML)));
        loader.setControllerFactory(new ControllerFactory()
                .withKernelConnection(kc)
                .withKernelLauncher(kernelLauncher)
                .withTimer(timer)
                .withGeneralKernelInfoRetriever(new GeneralKernelInfoRetriever(kc, errorHandler))
                .withSyncInfoDto(new SyncInfoDto(kc, errorHandler))
        );
        return loader;
    }

    private void initLogger(CfgAion cfg) {
        // Initialize logging.  Borrowed from Aion CLI program.
        ServiceLoader.load(AionLoggerFactory.class);
        // Outputs relevant logger configuration
        // TODO the info/error println messages should be presented via GUI
        if (!cfg.getLog().getLogFile()) {
            System.out.println("Logger disabled; to enable please check log settings in config.xml\n");
        } else if (!cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            System.out.println("File path is invalid; please check log setting in config.xml\n");
            System.exit(1);
        } else if (cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            System.out.println("Logger file path: '" + cfg.getLog().getLogPath() + "'\n");
        }
        AionLoggerFactory.init(cfg.getLog().getModules(), cfg.getLog().getLogFile(), cfg.getLog().getLogPath());
    }

    private void registerEventBusConsumer() {
        EventBusRegistry.INSTANCE.getBus(WindowControlsEvent.ID).register(this);
//        EventBusRegistry.getBus(HeaderPaneButtonEvent.ID).register(this);
    }

    private void handleMouseDragged(final MouseEvent event) {
        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    public void shutDown() {
        LOG.info("Shutting down.");
        Platform.exit();
        //BlockchainConnector.getInstance().close();
        Executors.newSingleThreadExecutor().submit(() -> System.exit(0));
        timer.stop();
    }

    private void handleMousePressed(final MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    @Subscribe
    private void handleWindowControlsEvent(final WindowControlsEvent event) {
        switch (event.getType()) {
            case MINIMIZE:
                minimize(event);
                break;
            case CLOSE:
                shutDown();
                break;
        }
    }

    @Subscribe
    private void handleHeaderPaneButtonEvent(final org.aion.gui.events.HeaderPaneButtonEvent event) {
        if(stage.getScene() == null) {
            return;
        }
//        log.debug(event.getType().toString());
        // todo: refactor by adding a view controller
        for(Map.Entry<HeaderPaneButtonEvent.Type, Node> entry: panes.entrySet()) {
            if(event.getType().equals(entry.getKey())) {
                entry.getValue().setVisible(true);
            } else {
                entry.getValue().setVisible(false);
            }
        }
    }

    private void minimize(final WindowControlsEvent event) {
        ((Stage) event.getSource().getScene().getWindow()).setIconified(true);
    }

    public Scene getScene() {
        return stage.getScene();
    }
}
