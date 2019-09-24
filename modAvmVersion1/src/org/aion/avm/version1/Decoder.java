package org.aion.avm.version1;

import java.math.BigInteger;
import org.aion.avm.stub.IDecoder;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.types.AionAddress;

public final class Decoder implements IDecoder {
    private final ABIDecoder decoder;

    public Decoder(byte[] encoding) {
        if (encoding == null) {
            throw new NullPointerException("Cannot decode a null encoding!");
        }
        this.decoder = new ABIDecoder(encoding);
    }

    @Override
    public int decodeOneInteger() {
        return this.decoder.decodeOneInteger();
    }

    @Override
    public BigInteger decodeOneBigInteger() {
        return this.decoder.decodeOneBigInteger();
    }

    @Override
    public String decodeOneString() {
        return this.decoder.decodeOneString();
    }

    @Override
    public AionAddress decodeOneAddress() {
        return new AionAddress(this.decoder.decodeOneAddress().toByteArray());
    }
}
