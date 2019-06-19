package org.aion.mcf.db;

/**
 * This enum lists the VM types accepted by the code base. The VM type indicates which VM was used
 * to deploy a specific contract.
 *
 * @implNote The values for FVM and AVM were specifically chosen to differ the ones from
 *     <i>org.aion.base.TransactionTypes</i> to ensure that the two interpretations are properly
 *     differentiated.
 */
public enum InternalVmType {
    // used when the information cannot be inferred, or was incorrectly set by old kernel versions
    /**
     * @implNote Version 0.3.4 of the kernel sets any read contract to transaction type 1. However,
     *     the database contains contract details for accounts that are not actual contracts. To
     *     clean up the database we interpret value 1 as unknown and force the code to search for
     *     the correct value.
     */
    UNKNOWN((byte) 0x01),
    // accounts that are not contracts can be updated by either VM
    EITHER((byte) 0x0e),
    FVM((byte) 0x0f),
    AVM((byte) 0x0a);

    private final byte code;

    InternalVmType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static InternalVmType getInstance(byte code) {
        switch (code) {
            case 0x0a:
                return AVM;
            case 0x0f:
                return FVM;
            case 0x01:
                return UNKNOWN;
            default:
                return EITHER;
        }
    }

    public boolean isContract() {
        return this == AVM || this == FVM;
    }
}
