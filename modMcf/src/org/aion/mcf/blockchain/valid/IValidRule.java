package org.aion.mcf.blockchain.valid;

/** Valication rules interface. */
public interface IValidRule {
    class RuleError {
        public final Class<?> errorClass;
        public final String error;

        public RuleError(Class<?> errorClass, String error) {
            this.errorClass = errorClass;
            this.error = error;
        }
    }
}
