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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testfx.api.FxAssert.verifyThat;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.model.AccountChangeHandlers;
import org.aion.gui.model.BalanceRetriever;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.TransactionProcessor;
import org.aion.gui.util.DataUpdater;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.connector.dto.TransactionResponseDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.matcher.base.NodeMatchers;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.matcher.control.TextInputControlMatchers;
import org.testfx.util.WaitForAsyncUtils;

public class SendControllerIntegTest extends ApplicationTest {

    private SendController controller;
    private Parent sendPane;
    private ControllerFactory cf;
    private AccountManager accountManager;
    private ConsoleManager consoleManager;
    private TransactionProcessor transactionProcessor;
    private KernelConnection kernelConnection;
    private BalanceRetriever balanceRetriever;
    private EventPublisher eventPublisher;

    private AccountChangeHandlers accountChangeHandlers;

    @Override
    public void init() {
        accountManager = mock(AccountManager.class);
        consoleManager = mock(ConsoleManager.class);
        transactionProcessor = mock(TransactionProcessor.class);
        kernelConnection = mock(KernelConnection.class);
        balanceRetriever = mock(BalanceRetriever.class);
        eventPublisher = new EventPublisher();
    }

    @Override
    public void start(Stage stage) throws Exception {
        cf = new ControllerFactory()
            .withKernelConnection(kernelConnection)
            .withAccountManager(accountManager)
            .withBalanceRetriever(balanceRetriever)
            .withBlockTransactionProcessor(transactionProcessor)
            .withConsoleManager(consoleManager);
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
        verifyThat("#accountBalance", not(NodeMatchers.isVisible()));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(""));
    }

    @Test
    public void testNoUnlockedMasterThenKernelConnects() {
        eventPublisher.fireConnectionEstablished();
        // nothing should change
        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", not(NodeMatchers.isVisible()));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(""));
    }

    @Test
    public void testHaveUnlockedMasterThenKernelConnects() throws Exception {
        String accountAddr = "accountAddress";
        AccountDTO account = new AccountDTO(
            "anyName", accountAddr, "anyBalance", "anyCurrency", false, 0
        );
        when(accountManager.getAccount(account.getPublicAddress())).thenReturn(account);
        eventPublisher.fireAccountsRecovered(Collections.singleton(account.getPublicAddress()));

        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText("anyBalance AION"));
        verifyThat("#accountBalance", NodeMatchers.isVisible());
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(account.getPublicAddress()));

        eventPublisher.fireConnectionEstablished();
        verifyThat("#sendButton", not(NodeMatchers.isDisabled()));
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText("anyBalance AION"));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(account.getPublicAddress()));
    }

    @Test
    public void testKernelConnectedWithMasterLockedThenMasterUnlockedThenAccountLocked() {
        eventPublisher.fireConnectionEstablished();
        verifyThat("#sendButton", NodeMatchers.isDisabled());

        String accountAddr = "accountAddress";
        AccountDTO account = new AccountDTO(
            "anyName", accountAddr, "anyBalance", "anyCurrency", false, 0
        );
        when(accountManager.getAccount(account.getPublicAddress())).thenReturn(account);
        eventPublisher.fireAccountsRecovered(Collections.singleton(account.getPublicAddress()));

        verifyThat("#sendButton", not(NodeMatchers.isDisabled()));
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText("anyBalance AION"));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(account.getPublicAddress()));

        eventPublisher.fireAccountLocked(account);

        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", not(NodeMatchers.isVisible()));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(""));
    }

    @Test
    public void testKernelNotConnectedWithMasterUnlockedThenMasterLocked() {
        String accountAddr = "accountAddress";
        AccountDTO account = new AccountDTO(
            "anyName", accountAddr, "anyBalance", "anyCurrency", false, 0
        );
        when(accountManager.getAccount(account.getPublicAddress())).thenReturn(account);
        eventPublisher.fireAccountsRecovered(Collections.singleton(account.getPublicAddress()));

        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText("anyBalance AION"));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(account.getPublicAddress()));

        eventPublisher.fireAccountLocked(account);

        verifyThat("#sendButton", NodeMatchers.isDisabled());
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", not(NodeMatchers.isVisible()));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(""));
    }

    @Test
    public void testKernelConnectedWithMasterUnlockedThenBalanceChanged() {
        eventPublisher.fireConnectionEstablished();
        verifyThat("#sendButton", NodeMatchers.isDisabled());

        String accountAddr = "accountAddress";
        AccountDTO account = new AccountDTO(
            "anyName", accountAddr, "anyBalance", "anyCurrency", false, 0
        );
        when(accountManager.getAccount(account.getPublicAddress())).thenReturn(account);
        eventPublisher.fireAccountsRecovered(Collections.singleton(account.getPublicAddress()));

        verifyThat("#sendButton", not(NodeMatchers.isDisabled()));
        verifyThat("#toInput", TextInputControlMatchers.hasText(""));
        verifyThat("#accountBalance", TextInputControlMatchers.hasText("anyBalance AION"));
        verifyThat("#accountAddress", TextInputControlMatchers.hasText(account.getPublicAddress()));

        when(balanceRetriever.getBalance(account.getPublicAddress()))
            .thenReturn(new BigInteger("1337"));
        new DataUpdater().run();

        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#accountBalance",
            TextInputControlMatchers.hasText("0.000000000000001337 AION"));
    }

    @Test
    public void testKernelGenerateTransaction() throws Exception {
        eventPublisher.fireConnectionEstablished();
        verifyThat("#sendButton", NodeMatchers.isDisabled());

        String accountAddr = "accountAddress";
        AccountDTO account = new AccountDTO(
            "anyName", accountAddr, "1337", "anyCurrency", false, 0
        );
        when(accountManager.getAccount(account.getPublicAddress())).thenReturn(account);
        eventPublisher.fireAccountsRecovered(Collections.singleton(account.getPublicAddress()));

        TransactionResponseDTO successResponse = new TransactionResponseDTO();
        when(transactionProcessor.sendTransaction(any(SendTransactionDTO.class)))
            .thenReturn(successResponse);

        clickOn("#toInput")
            .write("0xa0c0ffee11111111111111111111111111111111111111111111111111111111");
        doubleClickOn("#nrgInput").write("37");
        doubleClickOn("#nrgPriceInput").write("12000000000000");
        clickOn("#valueInput").write("400");
        clickOn("#sendButton");
        WaitForAsyncUtils.waitForFxEvents();

        ArgumentCaptor<SendTransactionDTO> sendTxDtoCapture = ArgumentCaptor
            .forClass(SendTransactionDTO.class);
        verify(transactionProcessor).sendTransaction(sendTxDtoCapture.capture());
        SendTransactionDTO sendTxDto = sendTxDtoCapture.getValue();
        assertThat(sendTxDto.getFrom(), is(account.getPublicAddress()));
        assertThat(sendTxDto.getTo(),
            is("0xa0c0ffee11111111111111111111111111111111111111111111111111111111"));
        assertThat(sendTxDto.getNrg(), is(37l));
        assertThat(sendTxDto.getNrgPrice(), is(12000000000000l));
        assertThat(sendTxDto.getValue(), is(new BigInteger("400000000000000000000")));

        verifyThat("#txStatusLabel", LabeledMatchers.hasText("Transaction finished"));
    }

    @Test
    public void testTransactionResubmitted() {
        //WIP
    }
}