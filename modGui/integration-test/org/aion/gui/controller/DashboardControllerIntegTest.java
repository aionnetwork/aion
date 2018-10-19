/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.gui.controller;

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testfx.api.FxAssert.verifyThat;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
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
import org.aion.os.KernelInstanceId;
import org.aion.os.KernelLauncher;
import org.aion.os.UnixKernelProcessHealthChecker;
import org.aion.wallet.console.ConsoleManager;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Tests integration of {@link DashboardController}; specifically, that it modifies its View
 * correctly and invokes the expected Model-layer classes.
 *
 * <p>Test focuses on the correctness of the UI elements. Classes from the model layer are mocked
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
    private ConsoleManager consoleManager;
    private DashboardController controller;
    private Parent dashboardView;
    private EventBusRegistry ebr;

    @Override
    public void start(Stage stage) throws Exception {
        ebr = new EventBusRegistry();
        kernelLauncher = mock(KernelLauncher.class);
        kernelConnection = mock(KernelConnection.class);
        kernelUpdateTimer = mock(KernelUpdateTimer.class);
        generalKernelInfoRetriever = mock(GeneralKernelInfoRetriever.class);
        syncInfoDto = mock(SyncInfoDto.class);
        healthChecker = mock(UnixKernelProcessHealthChecker.class);
        consoleManager = mock(ConsoleManager.class);

        cf =
                new ControllerFactory()
                        .withKernelConnection(kernelConnection)
                        .withKernelLauncher(kernelLauncher)
                        .withTimer(kernelUpdateTimer)
                        .withSyncInfoDto(syncInfoDto)
                        .withGeneralKernelInfoRetriever(generalKernelInfoRetriever)
                        .withHealthChecker(healthChecker)
                        .withConsoleManager(consoleManager)
                        .withEventBusRegistry(ebr);
        FXMLLoader loader =
                new FXMLLoader(
                        DashboardController.class.getResource("components/Dashboard.fxml"),
                        null,
                        null,
                        cf,
                        Charset.forName(DEFAULT_CHARSET_NAME),
                        new LinkedList<>());
        dashboardView = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(dashboardView));
        stage.show();
        stage.toFront();
    }

    /**
     * Use case: - Start Dashboard with no kernel running. Launch kernel button should be enabled -
     * Click launch kernel. Kernel should launch and button should be disabled - UI elements should
     * update - Click terminate button. Kernel should terminate; terminate button should close; UI
     * elements should update
     */
    @Test
    public void testLaunchKernelThenTerminate() throws Exception {
        clickOn("#launchKernelButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(kernelLauncher).launch();
        verifyThat("#launchKernelButton", Node::isDisabled);
        verifyThat("#kernelStatusLabel", LabeledMatchers.hasText("Starting..."));

        ebr.getBus(EventBusRegistry.KERNEL_BUS).post(new KernelProcEvent.KernelLaunchedEvent());
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

        ebr.getBus(EventBusRegistry.KERNEL_BUS).post(new KernelProcEvent.KernelTerminatedEvent());
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#kernelStatusLabel", LabeledMatchers.hasText("Not running"));
    }

    @Test
    public void testUiTimerTick() throws Exception {
        KernelInstanceId kernelInstanceId = new KernelInstanceId(123);
        when(kernelLauncher.getLaunchedInstance()).thenReturn(kernelInstanceId);
        when(healthChecker.checkIfKernelRunning(123)).thenReturn(false);

        int peerCount = 42;
        long bestNetBlkNumber = 5781;
        long bestChainBlkNumber = 1337;
        boolean isMining = true;
        when(generalKernelInfoRetriever.getPeerCount()).thenReturn(peerCount);
        when(syncInfoDto.getNetworkBestBlkNumber()).thenReturn(bestNetBlkNumber);
        when(syncInfoDto.getChainBestBlkNumber()).thenReturn(bestChainBlkNumber);
        when(generalKernelInfoRetriever.isMining()).thenReturn(isMining);

        ebr.getBus(RefreshEvent.ID).post(new RefreshEvent(RefreshEvent.Type.TIMER));
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
        when(kernelLauncher.hasLaunchedInstance()).thenReturn(true);
        when(kernelLauncher.getLaunchedInstance()).thenReturn(kernelInstanceId);
        when(healthChecker.checkIfKernelRunning(123)).thenReturn(false);
        ebr.getBus(EventBusRegistry.KERNEL_BUS).post(new UnexpectedApiDisconnectedEvent());
        WaitForAsyncUtils.waitForFxEvents();
        verify(kernelUpdateTimer).stop();
        verify(kernelConnection).disconnect();
        verify(kernelLauncher).cleanUpDeadProcess();
    }

    @Test
    public void testUiTimerTickWhenNotConnected() throws Exception {
        String labelInitialValue = "--"; // as defined in the .FXML of Dashboard

        KernelInstanceId kernelInstanceId = new KernelInstanceId(123);
        when(kernelLauncher.getLaunchedInstance()).thenReturn(kernelInstanceId);
        when(healthChecker.checkIfKernelRunning(123)).thenReturn(false);

        when(generalKernelInfoRetriever.getPeerCount()).thenReturn(null);
        when(syncInfoDto.getNetworkBestBlkNumber()).thenReturn(0l);
        when(syncInfoDto.getChainBestBlkNumber()).thenReturn(0l);
        when(generalKernelInfoRetriever.isMining()).thenReturn(null);

        ebr.getBus(RefreshEvent.ID).post(new RefreshEvent(RefreshEvent.Type.TIMER));
        try {
            controller.getExecutor().awaitTermination(1l, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            fail("Api call took too long.");
        }
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat(
                "#numPeersLabel",
                LabeledMatchers.hasText(labelInitialValue)); // should not have been updated
        verifyThat("#blocksLabel", LabeledMatchers.hasText("0/0 total blocks"));
        verifyThat(
                "#isMining",
                LabeledMatchers.hasText(labelInitialValue)); // should not have been updated
    }
}
