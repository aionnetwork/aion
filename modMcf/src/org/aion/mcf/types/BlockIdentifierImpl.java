package org.aion.mcf.types;

import static org.aion.util.bytes.ByteUtil.byteArrayToLong;

import java.math.BigInteger;

import org.aion.interfaces.block.BlockIdentifier;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.util.conversions.Hex;

/** Block identifier holds block hash and number <br> */
public class BlockIdentifierImpl implements BlockIdentifier {

    /** Block hash */
    private byte[] hash;

    /** Block number */
    private long number;

    public BlockIdentifierImpl(RLPList rlp) {
        this.hash = rlp.get(0).getRLPData();
        this.number = byteArrayToLong(rlp.get(1).getRLPData());
    }

    public BlockIdentifierImpl(byte[] hash, long number) {
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
        return "BlockIdentifierImpl {" + "hash=" + Hex.toHexString(hash) + ", number=" + number + '}';
    }
}
