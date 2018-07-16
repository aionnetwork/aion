package org.aion.wallet.storage;

import org.aion.gui.model.ApiType;
import org.aion.wallet.dto.LightAppSettings;
import org.aion.wallet.exception.ValidationException;
import org.slf4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

public class WalletStorage {

    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());

    public static final Path KEYSTORE_PATH;

    private static final String BLANK = "";

    private static final String ACCOUNT_NAME_PROP = ".name";

    private static final String MASTER_DERIVATIONS_PROP = "master.derivations";

    private static final String MASTER_MNEMONIC_PROP = "master.mnemonic";

    private static final String MNEMONIC_ENCRYPTION_ALGORITHM = "Blowfish";

    private static final String MNEMONIC_STRING_CONVERSION_CHARSET_NAME = "ISO-8859-1";

    private static final WalletStorage INST;

    private static final String STORAGE_DIR;

    private static final String ACCOUNTS_FILE;

    private static final String WALLET_FILE;

    static {
        String storageDir = System.getProperty("local.storage.dir");
        if (storageDir == null || storageDir.equalsIgnoreCase("")) {
            storageDir = System.getProperty("user.home") + File.separator + ".aion";
        }
        STORAGE_DIR = storageDir;

        KEYSTORE_PATH = Paths.get(STORAGE_DIR + File.separator + "keystore");

        ACCOUNTS_FILE = STORAGE_DIR + File.separator + "accounts.properties";

        WALLET_FILE = STORAGE_DIR + File.separator + "wallet.properties";
    }

    static {
        try {
            INST = new WalletStorage();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final Properties accountsProperties;

    private final Properties lightAppProperties;

    private WalletStorage() throws IOException {
        final Path dir = Paths.get(STORAGE_DIR);
        ensureExistence(dir, true);
        accountsProperties = getPropertiesFomFIle(ACCOUNTS_FILE);
        lightAppProperties = getPropertiesFomFIle(WALLET_FILE);
    }

    public static WalletStorage getInstance() {
        return INST;
    }

    private Properties getPropertiesFomFIle(final String fullPath) throws IOException {
        final Path filePath = Paths.get(fullPath);
        ensureExistence(filePath, false);
        final InputStream reader = Files.newInputStream(filePath);
        Properties properties = new Properties();
        properties.load(reader);
        return properties;
    }

    private void ensureExistence(final Path path, final boolean isDir) throws IOException {
        if (!Files.exists(path)) {
            if (isDir) {
                Files.createDirectory(path);
            } else {
                Files.createFile(path);
            }
        }
    }

    public void save() {
        saveAccounts();
        saveSettings();
    }

    private void saveAccounts() {
        try (final OutputStream writer = Files.newOutputStream(Paths.get(ACCOUNTS_FILE))) {
            accountsProperties.store(writer, LocalDateTime.now().toString());
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void saveSettings() {
        try (final OutputStream writer = Files.newOutputStream(Paths.get(WALLET_FILE))) {
            lightAppProperties.store(writer, LocalDateTime.now().toString());
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public String getAccountName(final String address) {
        return Optional.ofNullable(accountsProperties.get(address + ACCOUNT_NAME_PROP)).map(Object::toString).orElse(BLANK);
    }

    public void setAccountName(final String address, final String accountName) {
        if (address != null && accountName != null) {
            accountsProperties.setProperty(address + ACCOUNT_NAME_PROP, accountName);
            saveAccounts();
        }
    }

    public String getMasterAccountMnemonic(String password) throws ValidationException {
        if (password == null || password.equalsIgnoreCase("")) {
            throw new ValidationException("Password is not valid");
        }
        String encodedMnemonic = accountsProperties.getProperty(MASTER_MNEMONIC_PROP);
        if (encodedMnemonic == null) {
            throw new ValidationException("No master account present");
        }

        try {
            return decryptMnemonic(encodedMnemonic, password);
        } catch (Exception e) {
            throw new ValidationException("Cannot decrypt your seed");
        }
    }

    public void setMasterAccountMnemonic(final String mnemonic, String password) throws ValidationException {
        try {
            if (mnemonic != null) {
                accountsProperties.setProperty(MASTER_MNEMONIC_PROP, encryptMnemonic(mnemonic, password));
                saveSettings();
            }
        } catch (Exception e) {
            throw new ValidationException("Cannot encode master account key");
        }
    }

    public boolean hasMasterAccount() {
        String mnemonic = accountsProperties.getProperty(MASTER_MNEMONIC_PROP);
        return mnemonic != null && !mnemonic.equalsIgnoreCase("");
    }

    public int getMasterAccountDerivations() {
        return Optional.ofNullable(accountsProperties.getProperty(MASTER_DERIVATIONS_PROP)).map(Integer::parseInt).orElse(0);
    }

    public void incrementMasterAccountDerivations() {
        accountsProperties.setProperty(MASTER_DERIVATIONS_PROP, getMasterAccountDerivations() + 1 + "");
        saveSettings();
    }

    public final LightAppSettings getLightAppSettings(final ApiType type) {
        return new LightAppSettings(lightAppProperties, type);
    }

    public final void saveLightAppSettings(final LightAppSettings lightAppSettings) {
        if (lightAppSettings != null) {
            lightAppProperties.putAll(lightAppSettings.getSettingsProperties());
            saveSettings();
        }
    }

    private String encryptMnemonic(String mnemonic, String password) throws Exception {
        SecretKeySpec key = new SecretKeySpec(password.getBytes(), MNEMONIC_ENCRYPTION_ALGORITHM);
        Cipher cipher = Cipher.getInstance(MNEMONIC_ENCRYPTION_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(mnemonic.getBytes());
        return new String(encrypted, MNEMONIC_STRING_CONVERSION_CHARSET_NAME);
    }

    private String decryptMnemonic(String encryptedMnemonic, String password) throws Exception {
        SecretKeySpec skeyspec = new SecretKeySpec(password.getBytes(), MNEMONIC_ENCRYPTION_ALGORITHM);
        Cipher cipher = Cipher.getInstance(MNEMONIC_ENCRYPTION_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, skeyspec);
        byte[] decrypted = cipher.doFinal(encryptedMnemonic.getBytes(MNEMONIC_STRING_CONVERSION_CHARSET_NAME));
        return new String(decrypted);
    }
}
