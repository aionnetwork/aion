package org.aion.precompiled.contracts.ATB;

import org.aion.vm.ExecutionResult;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public interface Transferrable {
    /**
     *
     * Performs a transfer of value from one account to another, using a method that
     * mimicks to the best of it's ability the {@code CALL} opcode. There are some
     * assumptions that become important for any caller to know:
     *
     * @implNote this method will check that the recipient account has no code. This
     * means that we <b>cannot</b> do a transfer to any contract account.
     *
     * @implNote assumes that the {@code fromValue} derived from the track will never
     * be null.
     *
     * @param to recipient address
     * @param value to be sent (in base units)
     * @return {@code true} if value was performed, {@code false} otherwise
     */
    ExecutionResult transfer(@Nonnull final byte[] to, @Nonnull final BigInteger value);
}
