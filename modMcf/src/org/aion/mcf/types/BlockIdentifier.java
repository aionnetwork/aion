package org.aion.mcf.types;

import static org.aion.base.util.ByteUtil.byteArrayToLong;

import java.math.BigInteger;
import org.aion.base.type.IBlockIdentifier;
import org.aion.base.util.Hex;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;

/** Block identifier holds block hash and number <br> */
public class BlockIdentifier implements IBlockIdentifier {

    /** Block hash */
    private byte[] hash;

    /** Block number */
    private long number;

    public BlockIdentifier(RLPList rlp) {
        this.hash = rlp.get(0).getRLPData();
        this.number = byteArrayToLong(rlp.get(1).getRLPData());
    }

    public BlockIdentifier(byte[] hash, long number) {
        this.hash = hash;
        this.number = number;
    }

    public byte[] getHash() {
        return hash;
    }

    public long getNumber() {
        return number;
    }

    public byte[] getEncoded() {
        byte[] hash = RLP.encodeElement(this.hash);
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));

        return RLP.encodeList(hash, number);
    }

    @Override
    public String toString() {
        return "BlockIdentifier {" + "hash=" + Hex.toHexString(hash) + ", number=" + number + '}';
    }
}
