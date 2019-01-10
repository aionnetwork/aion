package org.aion.mcf.account;

import java.io.UnsupportedEncodingException;
import org.aion.base.util.ByteUtil;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;

/** keystore item class. */
public class KeystoreItem {

    public String id;
    public int version;
    public String address;
    public KeystoreCrypto crypto;

    // rlp

    public byte[] toRlp() {
        byte[] bytesId = RLP.encodeString(this.id);
        byte[] bytesVersion = RLP.encodeInt(this.version);
        byte[] bytesAddress = RLP.encodeString(this.address);
        byte[] bytesCrypto = RLP.encodeElement(this.crypto.toRlp());
        return RLP.encodeList(bytesId, bytesVersion, bytesAddress, bytesCrypto);
    }

    public static KeystoreItem parse(byte[] bytes) throws UnsupportedEncodingException {
        RLPList list = (RLPList) RLP.decode2(bytes).get(0);
        KeystoreItem ki = new KeystoreItem();
        ki.setId(new String(list.get(0).getRLPData(), "UTF-8"));
        ki.setVersion(ByteUtil.byteArrayToInt(list.get(1).getRLPData()));
        ki.setAddress(new String(list.get(2).getRLPData(), "US-ASCII"));
        ki.setKeystoreCrypto(KeystoreCrypto.parse(list.get(3).getRLPData()));
        return ki;
    }

    // setters

    public void setId(String id) {
        this.id = id;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setKeystoreCrypto(KeystoreCrypto crypto) {
        this.crypto = crypto;
    }

    // getters

    public String getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    public String getAddress() {
        return address;
    }

    public KeystoreCrypto getKeystoreCrypto() {
        return crypto;
    }
}
