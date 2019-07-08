package org.aion.precompiled;

import java.util.HashMap;
import java.util.Map;

/** An enumeration representing the execution status of a transaction. */
public enum PrecompiledResultCode {
    SUCCESS(0, ResultCategory.SUCCESS),

    INVALID_NONCE(101, ResultCategory.REJECTED),

    INVALID_NRG_LIMIT(102, ResultCategory.REJECTED),

    INSUFFICIENT_BALANCE(103, ResultCategory.REJECTED),

    FAILURE(1, ResultCategory.FAILED),

    OUT_OF_NRG(2, ResultCategory.FAILED),

    BAD_INSTRUCTION(3, ResultCategory.FAILED),

    BAD_JUMP_DESTINATION(4, ResultCategory.FAILED),

    STACK_OVERFLOW(5, ResultCategory.FAILED),

    STACK_UNDERFLOW(6, ResultCategory.FAILED),

    REVERT(7, ResultCategory.REVERT),

    STATIC_MODE_ERROR(8, ResultCategory.FAILED),

    INCOMPATIBLE_CONTRACT_CALL(9, ResultCategory.FAILED),

    ABORT(11, ResultCategory.FAILED),

    VM_REJECTED(-1, ResultCategory.FATAL),

    VM_INTERNAL_ERROR(-2, ResultCategory.FATAL);

    private enum ResultCategory {
        SUCCESS,
        REJECTED,
        FAILED,
        FATAL,
        REVERT
    }

    private static Map<Integer, PrecompiledResultCode> integerMapping = new HashMap<>();
    private ResultCategory category;
    private int value;

    static {
        for (PrecompiledResultCode code : PrecompiledResultCode.values()) {
            integerMapping.put(code.value, code);
        }
    }

    PrecompiledResultCode(int value, ResultCategory category) {
        this.value = value;
        this.category = category;
    }

    public boolean isSuccess() {
        return this.category == ResultCategory.SUCCESS;
    }

    public boolean isRejected() {
        return this.category == ResultCategory.REJECTED;
    }

    public boolean isFailed() {
        return ((this.category == ResultCategory.FAILED)
                || (this.category == ResultCategory.REVERT));
    }

    public boolean isFatal() {
        return this.category == ResultCategory.FATAL;
    }

    public boolean isRevert() {
        return this.category == ResultCategory.REVERT;
    }

    public int toInt() {
        return this.value;
    }

    public static PrecompiledResultCode fromInt(int code) {
        PrecompiledResultCode result = integerMapping.get(code);
        if (result == null) {
            throw new IllegalArgumentException(
                    "No FastVmResultCode whose integer representation is: " + code);
        }
        return result;
    }
}
