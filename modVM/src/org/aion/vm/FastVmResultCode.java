package org.aion.vm;

import java.util.HashMap;
import java.util.Map;
import org.aion.vm.api.interfaces.ResultCode;

/**
 * An enumeration representing the execution status of a transaction.
 */
public enum FastVmResultCode implements ResultCode {

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

    REVERT(7, ResultCategory.FAILED),

    STATIC_MODE_ERROR(8, ResultCategory.FAILED),

    ABORT(11, ResultCategory.FAILED),

    VM_REJECTED(-1, ResultCategory.FATAL),

    VM_INTERNAL_ERROR(-2, ResultCategory.FATAL);

    private enum ResultCategory {SUCCESS, REJECTED, FAILED, FATAL }

    private static Map<Integer, FastVmResultCode> integerMapping = new HashMap<>();
    private ResultCategory category;
    private int value;

    static {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            integerMapping.put(code.value, code);
        }
    }

    FastVmResultCode(int value, ResultCategory category) {
        this.value = value;
        this.category = category;
    }

    @Override
    public boolean isSuccess() {
        return this.category == ResultCategory.SUCCESS;
    }

    @Override
    public boolean isRejected() {
        return this.category == ResultCategory.REJECTED;
    }

    @Override
    public boolean isFailed() {
        return this.category == ResultCategory.FAILED;
    }

    @Override
    public boolean isFatal() {
        return this.category == ResultCategory.FATAL;
    }

    @Override
    public int toInt() {
        return this.value;
    }

    public static FastVmResultCode fromInt(int code) {
        FastVmResultCode result = integerMapping.get(code);
        if (result == null) {
            throw new IllegalArgumentException("No FastVmResultCode whose integer representation is: " + code);
        }
        return result;
    }

}
