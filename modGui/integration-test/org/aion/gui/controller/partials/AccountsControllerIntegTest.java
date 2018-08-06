package org.aion.gui.controller.partials;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.aion.gui.controller.ControllerFactory;
import org.aion.gui.controller.DashboardController;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.KernelProcEvent;
import org.aion.gui.model.GeneralKernelInfoRetriever;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.os.KernelLauncher;
import org.aion.os.UnixKernelProcessHealthChecker;
import org.aion.wallet.console.ConsoleManager;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.Charset;
import java.util.LinkedList;

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testfx.api.FxAssert.verifyThat;

public class AccountsControllerIntegTest extends ApplicationTest {
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
        ebr = new EventBusRegistry();
        kernelLauncher = mock(KernelLauncher.class);
        kernelConnection = mock(KernelConnection.class);
        kernelUpdateTimer = mock(KernelUpdateTimer.class);
        generalKernelInfoRetriever = mock(GeneralKernelInfoRetriever.class);
        syncInfoDto = mock(SyncInfoDto.class);
        healthChecker = mock(UnixKernelProcessHealthChecker.class);
        consoleManager = mock(ConsoleManager.class);

        cf = new ControllerFactory()
                .withKernelConnection(kernelConnection)
                .withKernelLauncher(kernelLauncher)
                .withTimer(kernelUpdateTimer)
                .withSyncInfoDto(syncInfoDto)
                .withGeneralKernelInfoRetriever(generalKernelInfoRetriever)
                .withHealthChecker(healthChecker)
                .withConsoleManager(consoleManager)
                .withEventBusRegistry(ebr);
        FXMLLoader loader = new FXMLLoader(
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

    @Test
    public void test() {

    }

    @Test
    public void testLaunchKernelThenTerminate() throws Exception {
//        clickOn("#launchKernelButton");
//        WaitForAsyncUtils.waitForFxEvents();
//        verify(kernelLauncher).launch();
//        verifyThat("#launchKernelButton", Node::isDisabled);
//        verifyThat("#kernelStatusLabel", LabeledMatchers.hasText("Starting..."));
        Thread.sleep(1000l);

    }
}