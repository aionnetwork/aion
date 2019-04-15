package org.aion.mcf.tx;

import java.util.Set;

/** Transaction type values supported by the kernel implementation. */
public class TransactionTypes {
    public static final byte DEFAULT = 0x00;
    public static final byte FVM_CREATE_CODE = 0x01;
    public static final byte AVM_CREATE_CODE = 0x02;

    /** Set of transaction types allowed by any of the Virtual Machine implementations. */
    public static final Set<Byte> ALL = Set.of(DEFAULT, FVM_CREATE_CODE, AVM_CREATE_CODE);

    /** Set of transaction types allowed by the Fast Virtual Machine implementation. */
    public static final Set<Byte> FVM = Set.of(DEFAULT, FVM_CREATE_CODE);

    /** Set of transaction types allowed by the Aion Virtual Machine implementation. */
    public static final Set<Byte> AVM = Set.of(DEFAULT, AVM_CREATE_CODE);
}
