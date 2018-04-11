/*******************************************************************************
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
 *
 ******************************************************************************/
package org.aion.mcf.account;

import java.security.*;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.spongycastle.crypto.generators.SCrypt;
import org.spongycastle.util.encoders.Hex;

/**
 *  keystore format class
 */
public class KeystoreFormat {

    public byte[] toKeystore(final ECKey key, String password) {
        try {
            // n,r,p = 2^18, 8, 1 uses 256MB memory and approx 1s CPU time on a
            // modern CPU.
            // final int ScryptN = ((Double) Math.pow(10.0, 18.0)).intValue();
            final int ScryptN = 262144;
            final int ScryptR = 8;
            final int ScryptP = 1;
            final int ScryptDklen = 32;
            // salt
            final byte[] salt = generateRandomBytes(32);

            final byte[] derivedKey = scrypt(password.getBytes(), salt, ScryptN, ScryptR, ScryptP, ScryptDklen);

            // 128-bit initialisation vector for the cipher (16 bytes)
            final byte[] iv = generateRandomBytes(16);
            final byte[] privateKey = key.getPrivKeyBytes();
            final byte[] encryptKey = Arrays.copyOfRange(derivedKey, 0, 16);
            final byte[] cipherText = encryptAes(iv, encryptKey, privateKey);
            final byte[] mac = HashUtil.h256(concat(Arrays.copyOfRange(derivedKey, 16, 32), cipherText));

            final KeystoreItem keystore = new KeystoreItem();
            keystore.address = Hex.toHexString(key.getAddress());
            keystore.id = UUID.randomUUID().toString();
            keystore.version = 3;
            keystore.crypto = new KeystoreCrypto();
            keystore.crypto.setKdf("scrypt");
            keystore.crypto.setMac(Hex.toHexString(mac));
            keystore.crypto.setCipher("aes-128-ctr");
            keystore.crypto.setCipherText(Hex.toHexString(cipherText));
            keystore.crypto.setCipherParams(new CipherParams());
            keystore.crypto.getCipherParams().setIv(Hex.toHexString(iv));
            keystore.crypto.setKdfParams(new KdfParams());
            keystore.crypto.getKdfParams().setN(ScryptN);
            keystore.crypto.getKdfParams().setR(ScryptR);
            keystore.crypto.getKdfParams().setP(ScryptP);
            keystore.crypto.getKdfParams().setDklen(ScryptDklen);
            keystore.crypto.getKdfParams().setSalt(Hex.toHexString(salt));
            byte[] bytes = keystore.toRlp();
            return bytes;

        } catch (Exception e) {

            throw new RuntimeException("Problem storing key. Message: " + e.getMessage(), e);
        }
    }

    private byte[] generateRandomBytes(int size) {
        final byte[] bytes = new byte[size];
        Random random = new SecureRandom();
        random.nextBytes(bytes);
        return bytes;
    }

    public static ECKey fromKeystore(final byte[] content, final String password) {

        try {
            final KeystoreItem keystore = KeystoreItem.parse(content);
            final byte[] cipherKey;

            if (keystore.version != 3) {
                throw new RuntimeException("Keystore version 3 only supported.");
            }

            switch (keystore.getKeystoreCrypto().getKdf()) {
            case "pbkdf2":
                cipherKey = checkMacSha3(keystore, password);
                break;
            case "scrypt":
                cipherKey = checkMacScrypt(keystore, password);
                break;
            default:
                throw new RuntimeException("non valid algorithm " + keystore.getKeystoreCrypto().getCipher());
            }

            byte[] privateKey = decryptAes(Hex.decode(keystore.getKeystoreCrypto().getCipherParams().getIv()),
                    cipherKey, Hex.decode(keystore.getKeystoreCrypto().getCipherText()));
            return ECKeyFac.inst().create().fromPrivate(privateKey);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] decryptAes(byte[] iv, byte[] keyBytes, byte[] cipherText)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        return processAes(iv, keyBytes, cipherText, Cipher.DECRYPT_MODE);
    }

    private byte[] encryptAes(byte[] iv, byte[] keyBytes, byte[] cipherText)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        return processAes(iv, keyBytes, cipherText, Cipher.ENCRYPT_MODE);
    }

    private static byte[] processAes(byte[] iv, byte[] keyBytes, byte[] cipherText, int encryptMode)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // Mode
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

        cipher.init(encryptMode, key, ivSpec);
        return cipher.doFinal(cipherText);
    }

    private static byte[] checkMacSha3(KeystoreItem keystore, String password) throws Exception {
        byte[] salt = Hex.decode(keystore.getKeystoreCrypto().getKdfParams().getSalt());
        int iterations = keystore.getKeystoreCrypto().getKdfParams().getC();
        byte[] part = new byte[16];
        byte[] h = hash(password, salt, iterations);
        byte[] cipherText = Hex.decode(keystore.getKeystoreCrypto().getCipherText());
        System.arraycopy(h, 16, part, 0, 16);

        byte[] actual = HashUtil.h256(concat(part, cipherText));

        if (Arrays.equals(actual, Hex.decode(keystore.getKeystoreCrypto().getMac()))) {
            System.arraycopy(h, 0, part, 0, 16);
            return part;
        }

        throw new RuntimeException("Most probably a wrong passphrase");
    }

    private static byte[] checkMacScrypt(KeystoreItem keystore, String password) throws Exception {
        byte[] part = new byte[16];
        KdfParams params = keystore.getKeystoreCrypto().getKdfParams();
        byte[] h = scrypt(password.getBytes(), Hex.decode(params.getSalt()), params.getN(), params.getR(),
                params.getP(), params.getDklen());
        byte[] cipherText = Hex.decode(keystore.getKeystoreCrypto().getCipherText());
        System.arraycopy(h, 16, part, 0, 16);

        byte[] actual = HashUtil.h256(concat(part, cipherText));

        if (Arrays.equals(actual, Hex.decode(keystore.getKeystoreCrypto().getMac()))) {
            System.arraycopy(h, 0, part, 0, 16);
            return part;
        }

        throw new RuntimeException("Most probably a wrong passphrase");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    private static byte[] scrypt(byte[] pass, byte[] salt, int n, int r, int p, int dkLen)
            throws GeneralSecurityException {
        return SCrypt.generate(pass, salt, n, r, p, dkLen);
    }

    private static byte[] hash(String encryptedData, byte[] salt, int iterations) throws Exception {
        char[] chars = encryptedData.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }

}
