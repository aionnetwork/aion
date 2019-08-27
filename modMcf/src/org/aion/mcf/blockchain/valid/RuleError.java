package org.aion.mcf.blockchain.valid;

public class RuleError {
    public final Class<?> errorClass;
    public final String error;

    public RuleError(Class<?> errorClass, String error) {
        this.errorClass = errorClass;
        this.error = error;
    }
}
