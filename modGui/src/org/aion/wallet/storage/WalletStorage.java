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
package org.aion.wallet.storage;

import com.google.common.annotations.VisibleForTesting;
import org.aion.gui.model.ApiType;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
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
    private final String storageDir;
    private final Path keystorePath;
    private final String accountsFile;
    private final String walletFile;
    private final Properties accountsProperties;
    private final Properties lightAppProperties;

    private static final String BLANK = "";
    private static final String ACCOUNT_NAME_PROP = ".name";
    private static final String MASTER_DERIVATIONS_PROP = "master.derivations";
    private static final String MASTER_MNEMONIC_PROP = "master.mnemonic";
    private static final String MNEMONIC_ENCRYPTION_ALGORITHM = "Blowfish";
    private static final String MNEMONIC_STRING_CONVERSION_CHARSET_NAME = "ISO-8859-1";

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    /** Constructor with default storage locations */
    public WalletStorage(String storageDir) throws IOException {
        this(storageDir, getDefaultKeystorePath(storageDir));
    }

    public WalletStorage(String storageDir,
                         Path keystorePath) throws IOException {
        this(storageDir,
                keystorePath,
                getDefaultAccountsFile(storageDir),
                getDefaultWalletFile(storageDir));
    }

    @VisibleForTesting
    WalletStorage(
            String storageDir,
            Path keystorePath,
            String accountsFile,
            String walletFile) throws IOException {
        this.storageDir = storageDir;
        this.keystorePath = keystorePath;
        this.accountsFile = accountsFile;
        this.walletFile = walletFile;

        final Path dir = Paths.get(this.storageDir);
        ensureExistence(dir, true);
        ensureExistence(keystorePath, true);
        this.accountsProperties = getPropertiesFomFIle(getDefaultAccountsFile(storageDir));
        this.lightAppProperties = getPropertiesFomFIle(getDefaultWalletFile(storageDir));
    }

    public Path getKeystorePath() {
        return keystorePath;
    }

    public void save() {
        saveAccounts();
        saveWalletSettings();
    }

    private void saveAccounts() {
        try (final OutputStream writer = Files.newOutputStream(Paths.get(accountsFile))) {
            accountsProperties.store(writer, LocalDateTime.now().toString());
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void saveWalletSettings() {
        try (final OutputStream writer = Files.newOutputStream(Paths.get(walletFile))) {
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
                saveWalletSettings();
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
        saveAccounts();
    }

    public final LightAppSettings getLightAppSettings(final ApiType type) {
        return new LightAppSettings(lightAppProperties, type);
    }

    public final void saveLightAppSettings(final LightAppSettings lightAppSettings) {
        if (lightAppSettings != null) {
            lightAppProperties.putAll(lightAppSettings.getSettingsProperties());
            saveWalletSettings();
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



    private static Path getDefaultKeystorePath(String storageDir) {
        return Paths.get(storageDir + File.separator + "keystore");
    }

    private static String getDefaultAccountsFile(String storageDir) {
        return storageDir + File.separator + "accounts.properties";
    }

    private static String getDefaultWalletFile(String storageDir) {
        return storageDir + File.separator + "wallet.properties";
    }

    private static Properties getPropertiesFomFIle(final String fullPath) throws IOException {
        final Path filePath = Paths.get(fullPath);
        ensureExistence(filePath, false);
        final InputStream reader = Files.newInputStream(filePath);
        Properties properties = new Properties();
        properties.load(reader);
        return properties;
    }

    private static void ensureExistence(final Path path, final boolean isDir) throws IOException {
        if (!Files.exists(path)) {
            if (isDir) {
                Files.createDirectory(path);
            } else {
                Files.createFile(path);
            }
        }
    }
}
