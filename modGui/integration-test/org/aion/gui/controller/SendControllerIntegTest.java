package org.aion.gui.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.model.AccountChangeHandlers;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.TransactionProcessor;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.matcher.base.NodeMatchers;
import org.testfx.matcher.control.TextInputControlMatchers;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testfx.api.FxAssert.verifyThat;

public class SendControllerIntegTest extends ApplicationTest {
    private SendController controller;
    private Parent sendPane;
    private ControllerFactory cf;
    private AccountManager accountManager;
    private ConsoleManager consoleManager;
    private TransactionProcessor transactionProcessor;
    private KernelConnection kernelConnection;
    private EventPublisher eventPublisher;

    private AccountChangeHandlers accountChangeHandlers;

    @Override
    public void init() {
        accountManager = mock(AccountManager.class);
        consoleManager = mock(ConsoleManager.class);
        transactionProcessor = mock(TransactionProcessor.class);
        kernelConnection = mock(KernelConnection.class);
        eventPublisher = new EventPublisher();
    }

    @Override
    public void start(Stage stage) throws Exception {
        cf = new ControllerFactory()
                .withKernelConnection(kernelConnection)
                .withAccountManager(accountManager)
                .withBlockTransactionProcessor(transactionProcessor);
        FXMLLoader loader = new FXMLLoader(
                DashboardController.class.getResource("components/partials/SendPane.fxml"),
                null,
                null,
                cf,
                Charset.forName(DEFAULT_CHARSET_NAME),
                new LinkedList<>());
        loader.setBuilderFactory(new UiSubcomponentsFactory()
                .withAccountManager(accountManager)
                .withConsoleManager(consoleManager)
        );
        sendPane = loader.load();
        controller = loader.getController();

        AnchorPane ap = new AnchorPane(sendPane);
        ap.setPrefWidth(860);
        ap.setPrefHeight(570);

        stage.setScene(new Scene(ap));
        stage.show();
        stage.toFront();

        lookup("#sendPane").query().setVisible(true);

        accountChangeHandlers = new AccountChangeHandlers(accountManager, transactionProcessor);
    }

    @Test
    public void testInitialState() {
        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText(""));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(""));
    }

    @Test
    public void testNoUnlockedMasterThenKernelConnects() {
        eventPublisher.fireConnectionEstablished();
        // nothing should change
        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText(""));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(""));
    }

    @Test
    public void testHaveUnlockedMasterThenKernelConnects() throws Exception {
        String accountAddr = "accountAddress";
        AccountDTO account = new AccountDTO(
                "anyName", accountAddr,"anyBalance", "anyCurrency", false, 0
        );
        when(accountManager.getAccount(account.getPublicAddress())).thenReturn(account);
        eventPublisher.fireAccountsRecovered(Collections.singleton(account.getPublicAddress()));

        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText("anyBalance AION"));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(account.getPublicAddress()));

        eventPublisher.fireConnectionEstablished();
        verifyThat("#sendButton", not(NodeMatchers.isDisabled()));
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText("anyBalance AION"));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(account.getPublicAddress()));
    }

    @Test
    public void testKernelConnectedWithMasterLockedThenMasterUnlocked() {
        //WIP
    }

    @Test
    public void testKernelConnectedWithMasterUnlockedThenMasterLocked() {
        //WIP
    }

    @Test
    public void testKernelNotConnectedWithMasterUnlockedThenMasterLocked() {
        //WIP
    }

    @Test
    public void testKernelConnectedWithMasterUnlockedThenBalanceChanged() {
        //WIP
    }

    @Test
    public void testKernelGenerateTransaction() {
        //WIP
    }

    @Test
    public void testTransactionResubmitted() {
        //WIP
    }
}