package org.aion.wallet.account;

import io.github.novacrypto.bip39.MnemonicGenerator;
import io.github.novacrypto.bip39.Words;
import io.github.novacrypto.bip39.wordlists.English;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.model.dto.BalanceDto;
import org.aion.gui.util.BalanceUtils;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.account.KeystoreFormat;
import org.aion.mcf.account.KeystoreItem;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.crypto.MasterKey;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.dto.TransactionDTO;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.storage.WalletStorage;
import org.aion.wallet.util.AddressUtils;
import org.aion.wallet.util.CryptoUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AccountManager {
    private final WalletStorage walletStorage = WalletStorage.getInstance();
    private final Map<String, AccountDTO> addressToAccount = new HashMap<>();
    private final Map<String, byte[]> addressToKeystoreContent = Collections.synchronizedMap(new HashMap<>());
    private final KeystoreFormat keystoreFormat = new KeystoreFormat();
    private final BalanceDto balanceProvider;
    private final Supplier<String> currencySupplier;

    private MasterKey root;

    private boolean isWalletLocked = false;

    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());

    public AccountManager(final BalanceDto balanceProvider, final Supplier<String> currencySupplier) {
        this.balanceProvider = balanceProvider;
        this.currencySupplier = currencySupplier;
        for (String address : Keystore.list()) {
            addressToAccount.put(address, getNewAccount(address));
        }

    }

    public String createMasterAccount(final String password, final String name) throws ValidationException {
        final StringBuilder mnemonicBuilder = new StringBuilder();
        final byte[] entropy = new byte[Words.TWELVE.byteLength()];
        new SecureRandom().nextBytes(entropy);
        new MnemonicGenerator(English.INSTANCE).createMnemonic(entropy, mnemonicBuilder::append);
        final String mnemonic = mnemonicBuilder.toString();

        final AccountDTO account = processMasterAccount(mnemonic, password);
        if (account == null) {
            return null;
        }
        account.setName(name);
        storeAccountName(account.getPublicAddress(), name);
        return mnemonic;
    }

    public void importMasterAccount(final String mnemonic, final String password) throws ValidationException {
        try {
            processMasterAccount(mnemonic, password);
        } catch (final Exception e) {
            throw new ValidationException(e);
        }
    }

    private AccountDTO processMasterAccount(final String mnemonic, final String password) throws ValidationException {
        final ECKey rootEcKey = CryptoUtils.getBip39ECKey(mnemonic);

        root = new MasterKey(rootEcKey);
        walletStorage.setMasterAccountMnemonic(mnemonic, password);
        final AccountDTO accountDTO = addInternalAccount();
        EventPublisher.fireAccountAdded(accountDTO);
        return accountDTO;
    }

    public void unlockMasterAccount(final String password) throws ValidationException {
        if (!walletStorage.hasMasterAccount()) {
            return;
        }
        isWalletLocked = false;

        final ECKey rootEcKey = CryptoUtils.getBip39ECKey(walletStorage.getMasterAccountMnemonic(password));
        root = new MasterKey(rootEcKey);

        final int accountDerivations = walletStorage.getMasterAccountDerivations();
        Set<String> recoveredAddresses = new LinkedHashSet<>(accountDerivations);
        for (int i = 0; i < accountDerivations; i++) {
            final String address = unlockInternalAccount(i);
            if (address != null) {
                recoveredAddresses.add(address);
            }
        }
        EventPublisher.fireAccountsRecovered(recoveredAddresses);
    }

    public boolean isMasterAccountUnlocked() {
        return root != null;
    }

    public void createAccount() throws ValidationException {
        EventPublisher.fireAccountAdded(addInternalAccount());
    }

    public AccountDTO importKeystore(final byte[] file, final String password, final boolean shouldKeep) throws ValidationException {
        try {
            ECKey key = KeystoreFormat.fromKeystore(file, password);
            if (key == null) {
                throw new ValidationException("Could Not extract ECKey from keystore file");
            }
            return addExternalAccount(key, file, password, shouldKeep);
        } catch (final Exception e) {
            throw new ValidationException(e);
        }
    }

    public AccountDTO importPrivateKey(final byte[] raw, final String password, final boolean shouldKeep) throws ValidationException {
        try {
            ECKey key = ECKeyFac.inst().fromPrivate(raw);
            final byte[] keystoreContent = keystoreFormat.toKeystore(key, password);
            return addExternalAccount(key, keystoreContent, password, shouldKeep);
        } catch (final Exception e) {
            throw new ValidationException(e);
        }
    }

    private String unlockInternalAccount(final int derivationIndex) throws ValidationException {
        if (root == null) {
            return null;
        }
        final ECKey derivedKey = getEcKeyFromRoot(derivationIndex);
        final String address = TypeConverter.toJsonHex(derivedKey.computeAddress(derivedKey.getPubKey()));
        AccountDTO recoveredAccount = addressToAccount.get(address);
        if (recoveredAccount != null) {
            recoveredAccount.setPrivateKey(derivedKey.getPrivKeyBytes());
        } else {
            recoveredAccount = createAccountWithPrivateKey(address, derivedKey.getPrivKeyBytes(), false, derivationIndex);
        }
        return recoveredAccount == null ? null : address;
    }

    private AccountDTO addInternalAccount(final int derivationIndex) throws ValidationException {
        if (root == null) {
            return null;
        }
        final ECKey derivedKey = getEcKeyFromRoot(derivationIndex);
        final String address = TypeConverter.toJsonHex(derivedKey.computeAddress(derivedKey.getPubKey()));
        return createAccountWithPrivateKey(address, derivedKey.getPrivKeyBytes(), false, derivationIndex);
    }

    private ECKey getEcKeyFromRoot(final int derivationIndex) throws ValidationException {
        return root.deriveHardened(new int[]{44, 425, 0, 0, derivationIndex});
    }

    private AccountDTO addInternalAccount() throws ValidationException {
        AccountDTO dto = addInternalAccount(walletStorage.getMasterAccountDerivations());
        walletStorage.incrementMasterAccountDerivations();
        return dto;
    }

    private AccountDTO addExternalAccount(final ECKey key, final byte[] fileContent, final String password, final boolean shouldKeep) throws UnsupportedEncodingException, ValidationException {
        String address = TypeConverter.toJsonHex(KeystoreItem.parse(fileContent).getAddress());
        final AccountDTO accountDTO;
        if (shouldKeep) {
            if (!Keystore.exist(address)) {
                address = Keystore.create(password, key);
                if (AddressUtils.isValid(address)) {
                    accountDTO = createImportedAccountFromPrivateKey(address, key.getPrivKeyBytes());
                } else {
                    throw new ValidationException("Failed to save keystore file");
                }
            } else {
                throw new ValidationException("Account already exists!");
            }
        } else {
            if (!addressToAccount.keySet().contains(address)) {
                accountDTO = createImportedAccountFromPrivateKey(address, key.getPrivKeyBytes());
            } else {
                throw new ValidationException("Account already exists!");
            }
        }
        if (accountDTO == null) {
            throw new ValidationException("Failed to create account");
        }
        processAccountAdded(accountDTO, fileContent);
        return accountDTO;
    }

    public void exportAccount(final AccountDTO account, final String password, final String destinationDir) throws ValidationException {
        final ECKey ecKey = CryptoUtils.getECKey(account.getPrivateKey());
        final boolean remembered = account.isImported() && Keystore.exist(account.getPublicAddress());
        if (!remembered) {
            Keystore.create(password, ecKey);
        }
        if (Files.isDirectory(WalletStorage.KEYSTORE_PATH)) {
            final String fileNameRegex = getExportedFileNameRegex(account.getPublicAddress());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(WalletStorage.KEYSTORE_PATH, fileNameRegex)) {
//            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Keystore.PATH, fileNameRegex)) {
                for (Path keystoreFile : stream) {
                    final String fileName = keystoreFile.getFileName().toString();
                    if (remembered) {
                        Files.copy(keystoreFile, Paths.get(destinationDir + File.separator + fileName), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        try {
                            Files.move(keystoreFile, Paths.get(destinationDir + File.separator + fileName), StandardCopyOption.ATOMIC_MOVE);
                        } finally {
                            if (Files.exists(keystoreFile)) {
                                Files.delete(keystoreFile);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new ValidationException(e);
            }
        } else {
            LOG.error("Could not find Keystore directory: " + WalletStorage.KEYSTORE_PATH);
        }

    }

    private String getExportedFileNameRegex(final String publicAddress) {
        return "UTC--*--" + publicAddress.substring(2);
    }

    public Set<TransactionDTO> getTransactions(final String address) {
        return addressToAccount.get(address).getTransactionsSnapshot();
    }

    public void removeTransactions(final String address, final Collection<TransactionDTO> transactions) {
        addressToAccount.get(address).removeTransactions(transactions);
    }

    public void addTransactions(final String address, final Collection<TransactionDTO> transactions) {
        addressToAccount.get(address).addTransactions(transactions);
    }

    public BlockDTO getLastSafeBlock(final String address) {
        return addressToAccount.get(address).getLastSafeBlock();
    }

    public void updateLastSafeBlock(final String address, final BlockDTO lastCheckedBlock) {
        addressToAccount.get(address).setLastSafeBlock(lastCheckedBlock);
    }

    public List<AccountDTO> getAccounts() {
        final Collection<AccountDTO> filteredAccounts = addressToAccount.values().stream().filter(account -> account.isImported() || account.isUnlocked()).collect(Collectors.toList());
        for (AccountDTO account : filteredAccounts) {
            balanceProvider.setAddress(account.getPublicAddress());
            balanceProvider.loadFromApi();
//            account.setBalance(BalanceUtils.formatBalance(balanceProvider.apply(account.getPublicAddress())));
            System.out.println("AccountManager#getAccounts -> account.setBalance -> " + BalanceUtils.formatBalance(balanceProvider.getBalance()));
            account.setBalance(BalanceUtils.formatBalance(balanceProvider.getBalance()));
        }
        List<AccountDTO> accounts = new ArrayList<>(filteredAccounts);
        accounts.sort((AccountDTO o1, AccountDTO o2) -> {
            if (!o1.isImported() && !o2.isImported()) {
                return o1.getDerivationIndex() - o2.getDerivationIndex();
            }
            return o1.isImported() ? 1 : -1;
        });
        return accounts;
    }

    public Set<String> getAddresses() {
        return new HashSet<>(addressToAccount.keySet());
    }

    public AccountDTO getAccount(final String address) {
        return Optional.ofNullable(addressToAccount.get(address)).orElse(getNewAccount(address));
    }

    public void updateAccount(final AccountDTO account) {
        storeAccountName(account.getPublicAddress(), account.getName());
    }

    public void unlockAccount(final AccountDTO account, final String password) throws ValidationException {
        isWalletLocked = false;
        final Optional<byte[]> fileContent = Optional.ofNullable(addressToKeystoreContent.get(account.getPublicAddress()));
        final ECKey storedKey;
        if (fileContent.isPresent()) {
            storedKey = KeystoreFormat.fromKeystore(fileContent.get(), password);
        } else {
            storedKey = Keystore.getKey(account.getPublicAddress(), password);
        }

        if (storedKey != null) {
            account.setActive(true);
            account.setPrivateKey(storedKey.getPrivKeyBytes());
            EventPublisher.fireAccountChanged(account);
        } else {
            throw new ValidationException("The password is incorrect!");
        }

    }

    public List<SendTransactionDTO> getTimedOutTransactions(final String accountAddress) {
        return addressToAccount.get(accountAddress).getTimedOutTransactions();
    }

    public void addTimedOutTransaction(final SendTransactionDTO transaction) {
        addressToAccount.get(transaction.getFrom()).addTimedOutTransaction(transaction);
    }

    public void removeTimedOutTransaction(final SendTransactionDTO transaction) {
        addressToAccount.get(transaction.getFrom()).removeTimedOutTransaction(transaction);
    }

    public void lockAll() {
        if (isWalletLocked) {
            return;
        }
        isWalletLocked = true;
        ConsoleManager.addLog("Wallet has been locked due to inactivity", ConsoleManager.LogType.ACCOUNT);
        root = null;
        for (AccountDTO account : addressToAccount.values()) {
            account.setPrivateKey(null);
            account.setActive(false);
            EventPublisher.fireAccountLocked(account);
        }
    }

    private AccountDTO createImportedAccountFromPrivateKey(final String address, final byte[] privateKeyBytes) {
        return createAccountWithPrivateKey(address, privateKeyBytes, true, -1);
    }

    private AccountDTO createAccountWithPrivateKey(final String address, final byte[] privateKeyBytes, boolean isImported, int derivation) {
        if (address == null) {
            LOG.error("Can't create account with null address");
            return null;
        }
        if (privateKeyBytes == null || privateKeyBytes.length == 0) {
            LOG.error("Can't create account without private key");
            return null;
        }
        AccountDTO account = getNewAccount(address, isImported, derivation);
        account.setPrivateKey(privateKeyBytes);
        account.setActive(true);
        addressToAccount.put(account.getPublicAddress(), account);
        return account;
    }

    private void processAccountAdded(final AccountDTO account, final byte[] keystoreContent) {
        if (account == null || keystoreContent == null) {
            throw new IllegalArgumentException(String.format("account %s ; keystoreContent: %s", account, Arrays.toString(keystoreContent)));
        }
        final String address = account.getPublicAddress();
        addressToKeystoreContent.put(address, keystoreContent);
        EventPublisher.fireAccountAdded(account);
    }

    private String getStoredAccountName(final String publicAddress) {
        return walletStorage.getAccountName(publicAddress);
    }

    private AccountDTO getNewAccount(final String publicAddress, boolean isImported, int derivation) {
        return new AccountDTO(getStoredAccountName(publicAddress),
                publicAddress,
                getFormattedBalance(publicAddress),
                currencySupplier.get(),
                isImported,
                derivation);
    }

    private AccountDTO getNewAccount(final String publicAddress) {
        return getNewAccount(publicAddress, true, -1);
    }

    private String getFormattedBalance(String address) {
        balanceProvider.setAddress(address);
        return BalanceUtils.formatBalance(balanceProvider.getBalance());
    }

    private void storeAccountName(final String address, final String name) {
        if (name.equalsIgnoreCase(getStoredAccountName(address))) {
            return;
        }
        walletStorage.setAccountName(address, name);
    }

    // ----- stuff copied from aion_ui's ApiBlockchainConnector (right place?)

    // XXX Moved this one to TransactionProcessor
    public BlockDTO getOldestSafeBlock(final Set<String> addresses, final Consumer<Iterator<String>> nullSafeBlockFilter) {
        BlockDTO oldestSafeBlock = null;
        final Iterator<String> addressIterator = addresses.iterator();
        while (addressIterator.hasNext()) {
            final String address = addressIterator.next();
            final BlockDTO lastSafeBlock = getLastSafeBlock(address);
            if (lastSafeBlock != null) {
                if (oldestSafeBlock == null || oldestSafeBlock.getNumber() > lastSafeBlock.getNumber()) {
                    oldestSafeBlock = lastSafeBlock;
                }
            } else {
                nullSafeBlockFilter.accept(addressIterator);
            }
        }
        return oldestSafeBlock;
    }



    // END OF stuff copied from aion_ui's ApiBlockchainConnector (right place?)

}
