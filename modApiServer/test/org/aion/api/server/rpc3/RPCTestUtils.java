package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import org.aion.base.AionTransaction;
import org.aion.rpc.errors.RPCExceptions.RPCException;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypesConverter.RequestConverter;
import org.mockito.ArgumentMatcher;

public class RPCTestUtils {


    @FunctionalInterface
    public interface ThrowingSupplier<T>{
        T get() throws Exception;
    }

    /**
     * @param request the request object to be executed
     * @param rpcMethods an implementation of {@link RPCServerMethods}
     * @param extractor the decoded
     * @param <T> the type of the RPC result
     * @return the RPC result
     */
    public static <T> T executeRequest(
            Request request, RPCServerMethods rpcMethods, Function<Object, T> extractor) {
        return extractor.apply(
                RPCServerMethods.execute(
                        RequestConverter.decode(
                                RequestConverter.encodeStr(
                                        request)), // encode and decode the request to validate the
                                                   // request object
                        rpcMethods));
    }

    /**
     * Asserts that expect error was thrown
     * @param throwingSupplier
     * @param expected
     */
    public static void assertFails(ThrowingSupplier throwingSupplier, Class<? extends RPCException> expected) {
        try {
            throwingSupplier.get();
            fail("Did not throw an instance of: " + expected.getName());
        } catch (Exception e) {
            assertEquals(expected, e.getClass());
        }
    }

    /**
     * Matches the transaction using the sender, destination, and input data
     * Prefer this over {@link org.mockito.Mockito#<AionTransaction>eq(AionTransaction)}
     * which only looks at the transaction hash. That results in tests failing a nondeterministic way.
     * @param left the transaction to match
     * @return an {@link org.mockito.ArgumentMatcher} wrapped by {@link org.mockito.Mockito#argThat(ArgumentMatcher)}
     */
    public static AionTransaction naiveTxMatcher(AionTransaction left) {
        return argThat(
                (right) ->
                        (right == null && left == null)// check on whether both args are null
                                || (left != null && right != null // first check that both are not null
                                        && Objects.equals(
                                                left.getSenderAddress(), right.getSenderAddress()) // compare senders
                                        && Objects.equals(
                                                left.getDestinationAddress(),
                                                right.getDestinationAddress())// compare destination
                                        && Arrays.equals(left.getData(), right.getData())));// compare input data
    }
}
