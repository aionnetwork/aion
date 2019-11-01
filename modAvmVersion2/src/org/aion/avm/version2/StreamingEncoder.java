package org.aion.avm.version2;

import avm.Address;
import java.math.BigInteger;
import org.aion.avm.stub.IStreamingEncoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.types.AionAddress;

public final class StreamingEncoder implements IStreamingEncoder {
    private final ABIStreamingEncoder encoder;

    public StreamingEncoder() {
        this.encoder = new ABIStreamingEncoder();
    }

    @Override
    public IStreamingEncoder encodeOneLong(long longToEncode) {
        this.encoder.encodeOneLong(longToEncode);
        return this;
    }

    @Override
    public IStreamingEncoder encodeOneBigInteger(BigInteger bigIntegerToEncode) {
        this.encoder.encodeOneBigInteger(bigIntegerToEncode);
        return this;
    }

    @Override
    public StreamingEncoder encodeOneString(String string) {
        this.encoder.encodeOneString(string);
        return this;
    }

    @Override
    public IStreamingEncoder encodeOneAddress(AionAddress address) {
        this.encoder.encodeOneAddress(new Address(address.toByteArray()));
        return this;
    }

    @Override
    public IStreamingEncoder encodeOneByteArray(byte[] array) {
        this.encoder.encodeOneByteArray(array);
        return this;
    }

    @Override
    public byte[] getEncoding() {
        return this.encoder.toBytes();
    }
}
