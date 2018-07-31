package org.aion.wallet.account;

import io.github.novacrypto.bip39.MnemonicGenerator;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ECKey;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.model.BalanceRetriever;
import org.aion.gui.util.AionConstants;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.storage.WalletStorage;
import org.aion.wallet.util.CryptoUtils;
import org.apache.commons.collections4.functors.ExceptionClosure;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;


public class AccountManagerTest {
    private BalanceRetriever balanceProvider;
    private Supplier<String> currencySupplier;
    private ConsoleManager consoleManager;
    private WalletStorage walletStorage;
    private Map<String, AccountDTO> addressToAccount;
    private AccountManager.MnemonicGeneratorWrapper mnemonicGenerator;
    private AccountManager.KeystoreWrapper keystoreWrapper;

    @Before
    public void before() {
        balanceProvider = mock(BalanceRetriever.class);
        currencySupplier = () -> AionConstants.CCY;
        consoleManager = mock(ConsoleManager.class);
        walletStorage = mock(WalletStorage.class);
        addressToAccount = new HashMap<>();
        mnemonicGenerator = mock(AccountManager.MnemonicGeneratorWrapper.class);
        keystoreWrapper = mock(AccountManager.KeystoreWrapper.class);
    }

    @Test
    public void testCtor() {
        String[] accountList = new String[] {
                "0xc0ffee1111111111111111111111111111111111111111111111111111111111",
                "0xdecaf22222222222222222222222222222222222222222222222222222222222",
        };
        when(keystoreWrapper.list()).thenReturn(accountList);
        when(walletStorage.getAccountName(accountList[0])).thenReturn("Coffee!");
        when(walletStorage.getAccountName(accountList[1])).thenReturn("Decaf!");

        AccountManager unit = new AccountManager(balanceProvider, currencySupplier, consoleManager,
                walletStorage, addressToAccount, mnemonicGenerator, keystoreWrapper);
        assertThat(unit.getAccounts().size(), is(2));
        assertThat(unit.getAccounts().stream().map(acc -> acc.getPublicAddress()).collect(Collectors.toList()),
                is(Arrays.asList(accountList)));
        assertThat(unit.getAccounts().get(0).getName(), is("Coffee!"));
        assertThat(unit.getAccounts().get(1).getName(), is("Decaf!"));
    }

    @Test
    public void testCreateMasterAccount() throws Exception {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);

        String mnemonic = "a mnemonic is also called a seed phrase";
        //String mnemonic = "cherry urge reopen cattle leg proof medal garden lumber alert will bench";
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                ((MnemonicGenerator.Target)invocationOnMock.getArguments()[1]).append(mnemonic);
                return null;
            }
        }).when(mnemonicGenerator).createMnemonic(any(byte[].class), any(MnemonicGenerator.Target.class));

        int derivIndex = 0;
        when(walletStorage.getMasterAccountDerivations()).thenReturn(derivIndex); // since we're creating a master account

        String name = "name";
        String password = "pass";
        AccountManager unit = new AccountManager(balanceProvider, currencySupplier, consoleManager,
                walletStorage, addressToAccount, mnemonicGenerator, keystoreWrapper);

        String result = unit.createMasterAccount(password, name);

        // verify return value
        assertThat(result, is(mnemonic));
        // verify master ("root") key creation
        assertThat(unit.getRoot().getEcKey().getPubKey(), is(CryptoUtils.getBip39ECKey(mnemonic).getPubKey()));
        assertThat(unit.getRoot().getEcKey().getPrivKeyBytes(), is(CryptoUtils.getBip39ECKey(mnemonic).getPrivKeyBytes()));
        // verify creation of new accountDTO and its derived key
        ECKey derivedKey = unit.getRoot().deriveHardened(new int[]{44, 425, 0, 0, derivIndex}); // TODO why 44,425,0,0
        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getName(), is(name));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(0));
        assertThat(unit.getAccounts().get(0).getPrivateKey(),
                is(derivedKey.getPrivKeyBytes()));
        assertThat(unit.getAccounts().get(0).getPublicAddress(),
                is(TypeConverter.toJsonHex(derivedKey.computeAddress(derivedKey.getPubKey()))));
        // verify master account is stored
        verify(walletStorage).setMasterAccountMnemonic(mnemonic, password);
        verify(walletStorage).incrementMasterAccountDerivations();
        verify(walletStorage).setAccountName(unit.getAccounts().get(0).getPublicAddress(), name);
        // TODO verify EventPublisher.fireAccountAdded(accountDTO) happened
    }

    @Test
    public void testImportMasterAccount() throws Exception {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        String mnemonic = "my cool mnemonic";
        String password = "pw";

        AccountManager unit = new AccountManager(balanceProvider, currencySupplier, consoleManager,
                walletStorage, addressToAccount, mnemonicGenerator, keystoreWrapper);
        unit.importMasterAccount(mnemonic, password);

        // verify master ("root") key creation
        assertThat(unit.getRoot().getEcKey().getPubKey(), is(CryptoUtils.getBip39ECKey(mnemonic).getPubKey()));
        assertThat(unit.getRoot().getEcKey().getPrivKeyBytes(), is(CryptoUtils.getBip39ECKey(mnemonic).getPrivKeyBytes()));
        // verify creation of new accountDTO and its derived key
        int derivIndex = 0;
        ECKey derivedKey = unit.getRoot().deriveHardened(new int[]{44, 425, 0, 0, derivIndex}); // TODO why 44,425,0,0
        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getName(), is(nullValue()));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(0));
        assertThat(unit.getAccounts().get(0).getPrivateKey(),
                is(derivedKey.getPrivKeyBytes()));
        assertThat(unit.getAccounts().get(0).getPublicAddress(),
                is(TypeConverter.toJsonHex(derivedKey.computeAddress(derivedKey.getPubKey()))));
        // verify master account is stored
        verify(walletStorage).setMasterAccountMnemonic(mnemonic, password);
        verify(walletStorage).incrementMasterAccountDerivations();
        // TODO verify EventPublisher.fireAccountAdded(accountDTO) happened
    }

    @Test
    public void testUnlockMasterAccountWhenMasterAccountDoesNotExist() throws Exception  {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        when(walletStorage.hasMasterAccount()).thenReturn(false);
        AccountManager unit = new AccountManager(balanceProvider, currencySupplier, consoleManager,
                walletStorage, addressToAccount, mnemonicGenerator, keystoreWrapper);

        unit.unlockMasterAccount("anything");

        verify(keystoreWrapper).list();
        verify(walletStorage).hasMasterAccount();
        verifyNoMoreInteractions(balanceProvider, consoleManager,
                walletStorage, mnemonicGenerator, keystoreWrapper);
    }

    @Test
    public void testUnlockMasterAccountWhenMasterAccountExists() throws Exception {
        String mnemonic = "some mnemonic";
        when(walletStorage.getMasterAccountMnemonic("password")).thenReturn(mnemonic);
        when(walletStorage.getMasterAccountDerivations()).thenReturn(2);

        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        when(walletStorage.hasMasterAccount()).thenReturn(false);
        AccountManager unit = new AccountManager(balanceProvider, currencySupplier, consoleManager,
                walletStorage, addressToAccount, mnemonicGenerator, keystoreWrapper);

        unit.unlockMasterAccount("anything");


    }
}