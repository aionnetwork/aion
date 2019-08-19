package org.aion.mcf.vm.types;

import java.math.BigInteger;
import org.aion.util.types.ByteArrayWrapper;

public interface DataWord {

    byte[] getData();

    byte[] getNoLeadZeroesData();

    BigInteger value();

    DataWord copy();

    boolean isZero();

    ByteArrayWrapper toWrapper();
}
