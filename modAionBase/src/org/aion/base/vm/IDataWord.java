package org.aion.base.vm;

import java.math.BigInteger;
import org.aion.base.util.ByteArrayWrapper;

public interface IDataWord {

    byte[] getData();

    byte[] getNoLeadZeroesData();

    BigInteger value();

    IDataWord copy();

    boolean isZero();

    ByteArrayWrapper toWrapper();
}
