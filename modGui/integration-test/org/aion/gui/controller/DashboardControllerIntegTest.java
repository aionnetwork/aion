package org.aion.gui.controller;

import com.google.common.eventbus.EventBus;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.KernelProcEvent;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.events.UnexpectedApiDisconnectedEvent;
import org.aion.gui.model.GeneralKernelInfoRetriever;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.gui.util.DataUpdater;
import org.aion.os.KernelInstanceId;
import org.aion.os.KernelLauncher;
import org.aion.os.UnixKernelProcessHealthChecker;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.verifyThat;

/**
 * Tests integration of {@link DashboardController}; specifically, that it modifies its View
 * correctly and invokes the expected Model-layer classes.
 *
 * Test focuses on the correctness of the UI elements.  Classes from the model layer are mocked
 * and we verify that they get called, but their correctness is not verified here (those are taken
 * care of in the integration and unit tests of the model layer).
 */
public class DashboardControllerIntegTest extends ApplicationTest {
    private ControllerFactory cf;
    private KernelLauncher kernelLauncher;
    private KernelConnection kernelConnection;
    private KernelUpdateTimer kernelUpdateTimer;
    private GeneralKernelInfoRetriever generalKernelInfoRetriever;
    private UnixKernelProcessHealthChecker healthChecker;
    private SyncInfoDto syncInfoDto;
    private EventBus kernelBus = EventBusRegistry.INSTANCE.getBus(EventBusRegistry.KERNEL_BUS);
    private EventBus uiRefreshBus = EventBusRegistry.INSTANCE.getBus(DataUpdater.UI_DATA_REFRESH);
    private DashboardController controller;

    /**
     * Not using Mockito's @Before because JavaFX's start() runs first and that needs member
     * vars already set up.  Will just call this init method from start.
     */
    public void init() {
        kernelLauncher = mock(KernelLauncher.class);
        kernelConnection = mock(KernelConnection.class);
        kernelUpdateTimer = mock(KernelUpdateTimer.class);
        generalKernelInfoRetriever = mock(GeneralKernelInfoRetriever.class);
        syncInfoDto = mock(SyncInfoDto.class);
        healthChecker = mock(UnixKernelProcessHealthChecker.class);
    }

    @Override
    public void start(Stage stage) throws Exception {
        cf = new ControllerFactory()
                .withKernelConnection(kernelConnection)
                .withKernelLauncher(kernelLauncher)
                .withTimer(kernelUpdateTimer)
                .withSyncInfoDto(syncInfoDto)
                .withGeneralKernelInfoRetriever(generalKernelInfoRetriever)
                .withHealthChecker(healthChecker);
        FXMLLoader loader = new FXMLLoader(
                DashboardController.class.getResource("components/Dashboard.fxml"),
                null,
                null,
                cf,
                Charset.forName(DEFAULT_CHARSET_NAME),
                new LinkedList<>());
        Parent dashboardView = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(dashboardView));
        stage.show();
        stage.toFront();
    }

    /**
     * Use case:
     *  - Start Dashboard with no kernel running.  Launch kernel button should be enabled
     *  - Click launch kernel.  Kernel should launch and button should be disabled
     *  - UI elements should update
     *  - Click terminate button. Kernel should terminate; terminate button should close; UI elements
     *    should update
     */
    @Test
    public void testLaunchKernelThenTerminate() throws Exception {
        clickOn("#launchKernelButton");
        verify(kernelLauncher).launch();
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#launchKernelButton", Node::isDisabled);
        verifyThat("#kernelStatusLabel", LabeledMatchers.hasText("Starting..."));

        kernelBus.post(new KernelProcEvent.KernelLaunchedEvent());
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#kernelStatusLabel", LabeledMatchers.hasText("Running"));
        verify(kernelConnection).connect();
        verify(kernelUpdateTimer).start();

        when(kernelLauncher.hasLaunchedInstance()).thenReturn(true);

        clickOn("#terminateKernelButton");
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#kernelStatusLabel", LabeledMatchers.hasText("Terminating..."));
        verify(kernelLauncher).terminate();
        verify(kernelConnection).disconnect();
        verify(kernelUpdateTimer).stop();

        kernelBus.post(new KernelProcEvent.KernelTerminatedEvent());
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#kernelStatusLabel", LabeledMatchers.hasText("Not running"));
    }

    @Test
    public void testUiTimerTick() throws Exception {
        int peerCount = 42;
        long bestNetBlkNumber = 5781;
        long bestChainBlkNumber = 1337;
        boolean isMining = true;
        when(generalKernelInfoRetriever.getPeerCount()).thenReturn(peerCount);
        when(syncInfoDto.getNetworkBestBlkNumber()).thenReturn(bestNetBlkNumber);
        when(syncInfoDto.getChainBestBlkNumber()).thenReturn(bestChainBlkNumber);
        when(generalKernelInfoRetriever.isMining()).thenReturn(isMining);

        uiRefreshBus.post(new RefreshEvent(RefreshEvent.Type.TIMER));
        try {
            controller.getExecutor().awaitTermination(1l, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            fail("Api call took too long.");
        }
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#numPeersLabel", LabeledMatchers.hasText(String.valueOf(peerCount)));
        verifyThat("#blocksLabel", LabeledMatchers.hasText("1337/5781 total blocks"));
        verifyThat("#isMining", LabeledMatchers.hasText(String.valueOf(isMining)));
    }

    @Test
    public void testHandleUnexpectedApiDisconnectWhenKernelDead() throws Exception {
        KernelInstanceId kernelInstanceId = new KernelInstanceId(123);
        when(kernelLauncher.getLaunchedInstance()).thenReturn(kernelInstanceId);
        when(healthChecker.checkIfKernelRunning(123)).thenReturn(false);
        kernelBus.post(new UnexpectedApiDisconnectedEvent());
        WaitForAsyncUtils.waitForFxEvents();
        verify(kernelUpdateTimer).stop();
        verify(kernelConnection).disconnect();
        verify(kernelLauncher).cleanUpDeadProcess();
    }
}