package org.aion.base.vm;

import java.math.BigInteger;

public interface IDataWord {

    byte[] getData();

    byte[] getNoLeadZeroesData();

    BigInteger value();

    IDataWord copy();

    boolean isZero();

}
