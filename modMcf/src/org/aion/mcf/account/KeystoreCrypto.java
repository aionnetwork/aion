package org.aion.mcf.account;

import java.io.UnsupportedEncodingException;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;

/** keystore crypto class */
public class KeystoreCrypto {
    private String cipher;
    private String cipherText;
    private String kdf;
    private String mac;
    private CipherParams cipherParams;
    private KdfParams kdfParams;

    public byte[] toRlp() {
        byte[] bytesCipher = RLP.encodeString(this.cipher);
        byte[] bytesCipherText = RLP.encodeString(this.cipherText);
        byte[] bytesKdf = RLP.encodeString(this.kdf);
        byte[] bytesMac = RLP.encodeString(this.mac);
        byte[] bytesCipherParams = RLP.encodeElement(this.cipherParams.toRlp());
        byte[] bytesKdfParams = RLP.encodeElement(this.kdfParams.toRlp());
        return RLP.encodeList(
                bytesCipher,
                bytesCipherText,
                bytesKdf,
                bytesMac,
                bytesCipherParams,
                bytesKdfParams);
    }

    public static KeystoreCrypto parse(byte[] bytes) throws UnsupportedEncodingException {
        RLPList list = (RLPList) RLP.decode2(bytes).get(0);
        KeystoreCrypto kc = new KeystoreCrypto();
        kc.setCipher(new String(list.get(0).getRLPData(), "UTF-8"));
        kc.setCipherText(new String(list.get(1).getRLPData(), "US-ASCII"));
        kc.setKdf(new String(list.get(2).getRLPData(), "UTF-8"));
        kc.setMac(new String(list.get(3).getRLPData(), "US-ASCII"));
        kc.setCipherParams(CipherParams.parse(list.get(4).getRLPData()));
        kc.setKdfParams(KdfParams.parse(list.get(5).getRLPData()));
        return kc;
    }

    // setters

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public void setCipherText(String ciphertext) {
        this.cipherText = ciphertext;
    }

    public void setKdf(String kdf) {
        this.kdf = kdf;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public void setCipherParams(CipherParams cipherparams) {
        this.cipherParams = cipherparams;
    }

    public void setKdfParams(KdfParams kdfparams) {
        this.kdfParams = kdfparams;
    }

    // getters

    public String getCipher() {
        return cipher;
    }

    public String getCipherText() {
        return cipherText;
    }

    public String getKdf() {
        return kdf;
    }

    public String getMac() {
        return mac;
    }

    public CipherParams getCipherParams() {
        return cipherParams;
    }

    public KdfParams getKdfParams() {
        return kdfParams;
    }
}
