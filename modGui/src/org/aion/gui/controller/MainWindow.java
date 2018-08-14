package org.aion.gui.controller;

import com.google.common.eventbus.Subscribe;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.WindowControlsEvent;
import org.aion.gui.model.AccountChangeHandlers;
import org.aion.gui.model.BalanceRetriever;
import org.aion.gui.model.ConfigManipulator;
import org.aion.gui.model.GeneralKernelInfoRetriever;
import org.aion.gui.model.IApiMsgErrorHandler;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.gui.model.SimpleApiMsgErrorHandler;
import org.aion.gui.model.TransactionProcessor;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.gui.util.AionConstants;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.os.KernelLauncher;
import org.aion.os.UnixKernelProcessHealthChecker;
import org.aion.os.UnixProcessTerminator;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.events.IdleMonitor;
import org.aion.wallet.storage.WalletStorage;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
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
     *
     * EventBus should be used only for the Model layer to send a signal to the Controller layer
     * for something that happened originating from the Model layer; i.e. the model noticed that
     * API has disconnected, then it can send an Event.  It should not be used for when the Model
     * is responding to some request from the Controller.  These should be done by just return
     * values in a Java method call (if call is synchronous) or a callback function given by the
     * Controller (if call is asynchronous).  Furthermore, try to avoid situations where code
     * behaviour is dependent on a sequence of multiple events triggering one another.
     */

    private double xOffset;
    private double yOffset;
    private Stage stage;
    private Scene scene;

    private final UnixKernelProcessHealthChecker unixKernelProcessHealthChecker;
    private final KernelUpdateTimer timer;
    private final KernelLauncher kernelLauncher;
    private final AccountManager accountManager;
    private final KernelConnection kc;
    private final TransactionProcessor transactionProcessor;
    private final AccountChangeHandlers accountChangeHandlers;
    private final ConsoleManager consoleManager;
    private final WalletStorage walletStorage;

    private final Map<HeaderPaneButtonEvent.Type, Node> panes = new HashMap<>();

    private static final String TITLE = "Aion Kernel";
    private static final String MAIN_WINDOW_FXML = "MainWindow.fxml";
    private static final String AION_LOGO = "components/icons/aion_logo.png";

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());
    private Duration lockDelayDuration = Duration.seconds(60);
    private IdleMonitor idleMonitor;


    public MainWindow() {
        consoleManager = new ConsoleManager();
        timer = new KernelUpdateTimer(Executors.newSingleThreadScheduledExecutor());
        unixKernelProcessHealthChecker = new UnixKernelProcessHealthChecker();

        try {
            walletStorage = new WalletStorage(getDefaultStorageDir());
        } catch (IOException ioe) {
            // Should handle this with a nice graphical error message with
            // suggestion for how to fix.  For now just throw an exception
            // that bubbles up to terminate the program.
            throw new IllegalStateException(String.format(
                    "Fatal error: could not allocate wallet storage at location '%s'; exiting.",
                    getDefaultStorageDir()
            ), ioe);
        }

        kernelLauncher = new KernelLauncher(
                CfgAion.inst().getGui().getCfgGuiLauncher(),
                EventBusRegistry.INSTANCE,
                new UnixProcessTerminator(),
                unixKernelProcessHealthChecker,
                new File(getDefaultStorageDir()));
        kc = new KernelConnection(
                CfgAion.inst().getApi(),
                new EventPublisher(),
                consoleManager);

        accountManager = new AccountManager(new BalanceRetriever(kc), () -> AionConstants.CCY, consoleManager, walletStorage);
        transactionProcessor = new TransactionProcessor(kc, accountManager, new BalanceRetriever(kc));
        accountChangeHandlers = new AccountChangeHandlers(accountManager, transactionProcessor);
    }

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

        this.scene = new Scene(root);
        this.scene.setFill(Color.TRANSPARENT);

        stage.setOnCloseRequest(t -> shutDown());

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.setMaxHeight(570);
        stage.setMaxWidth(860);
        stage.show();


        panes.put(HeaderPaneButtonEvent.Type.DASHBOARD, scene.lookup("#overviewPane"));
        panes.put(HeaderPaneButtonEvent.Type.ACCOUNTS, scene.lookup("#accountsPane"));
        panes.put(HeaderPaneButtonEvent.Type.SEND, scene.lookup("#sendPane"));
        panes.put(HeaderPaneButtonEvent.Type.RECEIVE, scene.lookup("#receivePane"));
        panes.put(HeaderPaneButtonEvent.Type.HISTORY, scene.lookup("#historyPane"));
        panes.put(HeaderPaneButtonEvent.Type.SETTINGS, scene.lookup("#settingsPane"));

        registerIdleMonitor(accountManager);

        // Set up event bus
        registerEventBusConsumer();
        kernelLauncher.tryResume();
    }

    private FXMLLoader loader() throws IOException {
        IApiMsgErrorHandler errorHandler = new SimpleApiMsgErrorHandler();
        FXMLLoader loader = new FXMLLoader((getClass().getResource(MAIN_WINDOW_FXML)));
        loader.setControllerFactory(new ControllerFactory()
                .withKernelConnection(kc)
                .withKernelLauncher(kernelLauncher)
                .withTimer(timer)
                .withGeneralKernelInfoRetriever(new GeneralKernelInfoRetriever(kc))
                .withConfigManipulator(new ConfigManipulator(CfgAion.inst(), kernelLauncher))
                .withGeneralKernelInfoRetriever(new GeneralKernelInfoRetriever(kc, errorHandler))
                .withSyncInfoDto(new SyncInfoDto(kc, errorHandler))
                .withConfigManipulator(new ConfigManipulator(CfgAion.inst(), kernelLauncher))
                .withAccountManager(accountManager)
                .withWalletStorage(walletStorage)
                .withBlockTransactionProcessor(transactionProcessor)
                .withConsoleManager(consoleManager)
                .withEventBusRegistry(EventBusRegistry.INSTANCE)
                .withHealthChecker(unixKernelProcessHealthChecker)
                .withBalanceRetriever(new BalanceRetriever(kc))
        );
        loader.setBuilderFactory(new UiSubcomponentsFactory()
                .withAccountManager(accountManager)
                .withConsoleManager(consoleManager)
        );
        return loader;
    }

    private void registerEventBusConsumer() {
        EventBusRegistry.INSTANCE.getBus(WindowControlsEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
    }

    private void handleMouseDragged(final MouseEvent event) {
        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    public void shutDown() {
        LOG.info("Shutting down.");
        Platform.exit();
        Executors.newSingleThreadExecutor().submit(() -> System.exit(0));
        timer.stop();
    }

    void handleMousePressed(final MouseEvent event) {
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


    private void registerIdleMonitor(AccountManager accountManager) {
        if (scene == null || lockDelayDuration == null) {
            return;
        }
        if (idleMonitor != null) {
            idleMonitor.stopMonitoring();
            idleMonitor = null;
        }
        idleMonitor = new IdleMonitor(lockDelayDuration, accountManager::lockAll);
        idleMonitor.register(scene, Event.ANY);
    }

    private static String getDefaultStorageDir() {
        String storageDir = System.getProperty("local.storage.dir");
        if (storageDir == null || storageDir.equalsIgnoreCase("")) {
            storageDir = System.getProperty("user.home") + File.separator + ".aion";
        }
        return storageDir;
    }

}
