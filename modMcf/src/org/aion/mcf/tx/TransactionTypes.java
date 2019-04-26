package org.aion.mcf.tx;

import java.util.Set;

/** Transaction type values supported by the kernel implementation. */
public class TransactionTypes {
    public static final byte DEFAULT = 0x01;
    public static final byte AVM_CREATE_CODE = 0x02;

    /** Set of transaction types allowed by any of the Virtual Machine implementations. */
    public static final Set<Byte> ALL = Set.of(DEFAULT, AVM_CREATE_CODE);
}
