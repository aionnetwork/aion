package org.aion.mcf.tx;

import static org.aion.mcf.tx.TransactionTypes.AVM_CREATE_CODE;
import static org.aion.mcf.tx.TransactionTypes.DEFAULT;

public enum InternalVmType {
    // used when the information cannot be inferred
    UNKNOWN((byte) 0x0),
    // the value for either VM must be distinct from the others
    EITHER((byte) (DEFAULT + AVM_CREATE_CODE)),
    FVM(DEFAULT),
    AVM(AVM_CREATE_CODE);

    private final byte code;

    InternalVmType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static boolean isContract(byte code) {
        switch (code) {
            case DEFAULT:
            case AVM_CREATE_CODE:
                return true;
            default:
                return false;
        }
    }

    public static InternalVmType getInstance(byte code) {
        switch (code) {
            case DEFAULT:
                return FVM;
            case AVM_CREATE_CODE:
                return AVM;
            default:
                return EITHER;
        }
    }
}
