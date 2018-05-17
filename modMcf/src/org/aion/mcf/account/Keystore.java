/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/
package org.aion.mcf.account;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.aion.base.type.Address;
import org.aion.base.util.*;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 *  key store class.
 */
public class Keystore {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
    private static final String KEYSTORE_PATH = System.getProperty("user.dir") + "/keystore";
    private static final Path PATH = Paths.get(KEYSTORE_PATH);
    private static final FileDateTimeComparator COMPARE = new FileDateTimeComparator();

    private static List<File> getFiles() {
        File[] files = PATH.toFile().listFiles();
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }

    public static String create(String password) {
        return create(password, ECKeyFac.inst().create());
    }

    public static String create(String password, ECKey key) {

        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-----");
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);

        if (!Files.exists(PATH)) {
            try {
                Files.createDirectory(PATH, attr);
            } catch (IOException e) {
                LOG.debug("keystore folder create failed!");
                return "";
            }
        }

        String address = ByteUtil.toHexString(key.getAddress());
        if (exist(address)) {
            return "0x";
        } else {
            KeystoreFormat format = new KeystoreFormat();
            byte[] content = format.toKeystore(key, password);
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            df.setTimeZone(tz);
            String iso_date = df.format(new Date(System.currentTimeMillis()));
            String fileName = "UTC--" + iso_date + "--" + address;
            try {
                Path keyFile = PATH.resolve(fileName);
                if (!Files.exists(keyFile))
                    keyFile = Files.createFile(keyFile, attr);
                String path = keyFile.toString();
                FileOutputStream fos = new FileOutputStream(path);
                fos.write(content);
                fos.close();
                return TypeConverter.toJsonHex(address);
            } catch (IOException e) {
                LOG.debug("fail to create keystore");
                return "0x";
            }
        }
    }

    public static Map<Address, ByteArrayWrapper> exportAccount(Map<Address, String> account) {
        if (account == null) {
            throw new NullPointerException();
        }

        Map<Address, ByteArrayWrapper> res = new HashMap<>();
        for (Map.Entry<Address, String> entry : account.entrySet()) {
            ECKey eckey = Keystore.getKey(entry.getKey().toString(), entry.getValue());
            if (eckey != null) {
                res.put(entry.getKey(), ByteArrayWrapper.wrap(eckey.getPrivKeyBytes()));
            }
        }

        return res;
    }

    public static Map<Address, ByteArrayWrapper> backupAccount(Map<Address, String> account) {
        if (account == null) {
            throw new NullPointerException();
        }

        List<File> files = getFiles();
        if (files == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("No key file been stored in the kernel.");
            }
            return new java.util.HashMap<>();
        }

        List<File> matchedFile = files.parallelStream().filter(file -> account.entrySet().parallelStream()
                .filter(ac -> file.getName().contains(ac.getKey().toString())).findFirst().isPresent())
                .collect(Collectors.toList());

        Map<Address, ByteArrayWrapper> res = new HashMap<>();
        for (File file : matchedFile) {
            try {
                String[] frags = file.getName().split("--");
                if (frags.length == 3) {
                    Address addr = Address.wrap(frags[2]);
                    byte[] content = Files.readAllBytes(file.toPath());

                    String pw = account.get(addr);
                    if (pw != null) {
                        ECKey key = KeystoreFormat.fromKeystore(content, pw);
                        if (key != null) {
                            res.put(addr, ByteArrayWrapper.wrap(content));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return res;
    }

    public static String[] list() {
        List<String> addresses = new ArrayList<>();
        List<File> files = getFiles();

        files.forEach((file) -> {
            String[] frags = file.getName().split("--");
            if (frags.length == 3) {
                addresses.add(TypeConverter.toJsonHex(frags[2]));
            }
        });
        return addresses.toArray(new String[addresses.size()]);
    }

    /**
     * Returns a sorted list of account addresses
     *
     * @return
     */
    public static List<String> accountsSorted() {
        List<String> addresses = new ArrayList<>();
        List<File> files = getFiles();

        files.sort(COMPARE);

        files.forEach((file) -> {
            String[] frags = file.getName().split("--");
            if (frags.length == 3) {
                addresses.add(TypeConverter.toJsonHex(frags[2]));
            }
        });
        return addresses;
    }

    public static ECKey getKey(String _address, String _password) {
        if (_address.startsWith("0x")) {
            _address = _address.substring(2);
        }
        ECKey key = null;
        List<File> files = getFiles();
        for (File file : files) {
            if (file.getName().split("--")[2].equals(_address)) {
                try {
                    byte[] content = Files.readAllBytes(file.toPath());
                    key = KeystoreFormat.fromKeystore(content, _password);

                } catch (IOException e) {
                    key = null;
                }
                break;
            }
        }
        return key;
    }

    public static boolean exist(String _address) {
        if (_address.startsWith("0x")) {
            _address = _address.substring(2);
        }
        List<File> files = getFiles();
        boolean flag = false;
        for (File file : files) {
            String s = file.getName().split("--")[2];
            if (file.getName().split("--")[2].equals(_address)) {
                flag = true;
                break;
            }
        }
        return flag;
    }

    public static Set<String> importAccount(Map<String, String> importKey) {
        if (importKey == null) {
            throw new NullPointerException();
        }

        Set<String> rtn = new HashSet<>();
        int count = 0;
        for (Map.Entry<String, String> keySet : importKey.entrySet()) {
            if (count < 100) {
                byte[] raw = Hex
                        .decode(keySet.getKey().startsWith("0x") ? keySet.getKey().substring(2) : keySet.getKey());
                ECKey key = KeystoreFormat.fromKeystore(raw, keySet.getValue());
                String address = Keystore.create(keySet.getValue(), key);
                if (!address.equals("0x")) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("The private key was imported, the address is: {}", address);
                    }

                } else {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Failed to import the private key. Already exists?");
                    }
                    // only return the failed import privateKey.
                    rtn.add(keySet.getKey());
                }
            } else {
                rtn.add(keySet.getKey());
            }
            count++;
        }

        return rtn;
    }
}
