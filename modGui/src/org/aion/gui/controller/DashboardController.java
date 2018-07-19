package org.aion.gui.controller;

import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.KernelProcEvent;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.model.GeneralKernelInfoRetriever;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.gui.util.DataUpdater;
import org.aion.gui.util.SyncStatusFormatter;
import org.aion.log.AionLoggerFactory;
import org.aion.os.KernelLauncher;
import org.aion.wallet.console.ConsoleManager;
import org.slf4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

public class DashboardController extends AbstractController {
    private final KernelLauncher kernelLauncher;
    private final KernelConnection kernelConnection;
    private final KernelUpdateTimer kernelUpdateTimer;

    private final GeneralKernelInfoRetriever generalKernelInfoRetriever;
    private final SyncInfoDto syncInfoDTO;

    @FXML private Button launchKernelButton;
    @FXML private Button terminateKernelButton;

    // These should probably be in their own classes
    @FXML private Label kernelStatusLabel;
    @FXML private Label numPeersLabel;
    @FXML private Label isMining;
    @FXML private Label blocksLabel;

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    public DashboardController(KernelLauncher kernelLauncher,
                               KernelConnection kernelConnection,
                               KernelUpdateTimer kernelUpdateTimer,
                               GeneralKernelInfoRetriever generalKernelInfoRetriever,
                               SyncInfoDto syncInfoDTO) {
        this.kernelConnection = kernelConnection;
        this.kernelLauncher = kernelLauncher;
        this.kernelUpdateTimer = kernelUpdateTimer;
        this.generalKernelInfoRetriever = generalKernelInfoRetriever;
        this.syncInfoDTO = syncInfoDTO;
    }

    @Override
    public void internalInit(final URL location, final ResourceBundle resources) {
    }

    @Override
    protected void registerEventBusConsumer() {
        // TODO: Make injectable
        EventBusRegistry.INSTANCE.getBus(EventBusRegistry.KERNEL_BUS).register(this);
//        EventBusRegistry.INSTANCE.getBus(DataUpdater.UI_DATA_REFRESH).register(this);
        EventBusRegistry.INSTANCE.getBus(RefreshEvent.ID).register(this);

    }
    // -- Handlers for Events coming from Model ---------------------------------------------------
//    @Subscribe
//    private void handleAccountChanged(final AccountDTO account) {
//        LOG.warn("Implement me!");
//    }

    @Subscribe
    private void handleHeaderPaneButtonEvent(final HeaderPaneButtonEvent event) {
        LOG.warn("Implement me!");
    }

    @Subscribe
    private void handleRefreshEvent(final RefreshEvent event){
        LOG.warn("Implement me!");
    }

    @Subscribe
    private void handleUiTimerTick(RefreshEvent event) {
        LOG.trace("DashboardController#handleUiTimerTick");
        // peer count
        final Task<Integer> getPeerCountTask = getApiTask(o -> generalKernelInfoRetriever.getPeerCount(), null);
        runApiTask(
                getPeerCountTask,
                evt -> Platform.runLater(() -> numPeersLabel.setText(String.valueOf(getPeerCountTask.getValue()))),
                getErrorEvent(throwable -> {}, getPeerCountTask),
                getEmptyEvent()
        );
        // sync status
        Task<Void> getSyncInfoTask = getApiTask(o -> syncInfoDTO.loadFromApi(), null);
        runApiTask(
                getSyncInfoTask,
                evt -> Platform.runLater(() -> blocksLabel.setText(String.valueOf(SyncStatusFormatter.formatSyncStatusByBlockNumbers(syncInfoDTO)))),
                getErrorEvent(throwable -> {}, getSyncInfoTask),
                getEmptyEvent()
        );
        // mining status
        Task<Boolean> getMiningStatusTask = getApiTask(o -> generalKernelInfoRetriever.isMining(), null);
        runApiTask(
                getMiningStatusTask,
                evt -> Platform.runLater(() -> isMining.setText(String.valueOf(getMiningStatusTask.getValue()))),
                getErrorEvent(throwable -> {}, getSyncInfoTask),
                getEmptyEvent()
        );
    }

    @Subscribe
    private void handleKernelLaunched(final KernelProcEvent.KernelLaunchedEvent ev) {
        LOG.trace("handleKernelLaunched");
        kernelConnection.connect(); // TODO: what if we launched the process but can't connect?
        kernelUpdateTimer.start();
        Platform.runLater( () -> {
            kernelStatusLabel.setText("Running");
            enableTerminateButton();
        });

    }

    @Subscribe
    private void handleKernelTerminated(final KernelProcEvent.KernelTerminatedEvent ev) {
        Platform.runLater( () -> {
            enableLaunchButton();
            kernelStatusLabel.setText("Not running");
            numPeersLabel.setText("--");
            blocksLabel.setText("--");
            isMining.setText("--");
        });
    }

    // -- Handlers for View components ------------------------------------------------------------
    public void launchKernel(MouseEvent ev) throws Exception {
        disableLaunchTerminateButtons();
        kernelStatusLabel.setText("Starting...");
        try {
            kernelLauncher.launch();
        } catch (RuntimeException ex) {
            enableLaunchButton();
        }
    }

    public void terminateKernel(MouseEvent ev) throws Exception {
        LOG.info("terminateKernel");
        disableLaunchTerminateButtons();
        kernelStatusLabel.setText("Terminating...");

        try {
            if (kernelLauncher.hasLaunchedInstance()
                    || (!kernelLauncher.hasLaunchedInstance() && kernelLauncher.tryResume())) {
                kernelUpdateTimer.stop();
                kernelConnection.disconnect();
                kernelLauncher.terminate();
            }
        } catch (RuntimeException ex) {
            LOG.error("Termination error", ex);
            enableLaunchButton();
//            kernelUpdateTimer.fireImmediatelyAndThenStart();
        }
    }

    public void openConsole() {
        ConsoleManager.show();
    }

    // -- Helpers methods -------------------------------------------------------------------------
    private void enableLaunchButton() {
        launchKernelButton.setDisable(false);
        terminateKernelButton.setDisable(true);
    }

    private void enableTerminateButton() {
        launchKernelButton.setDisable(true);
        terminateKernelButton.setDisable(false);
    }

    private void disableLaunchTerminateButtons() {
        launchKernelButton.setDisable(true);
        terminateKernelButton.setDisable(true);
    }


}