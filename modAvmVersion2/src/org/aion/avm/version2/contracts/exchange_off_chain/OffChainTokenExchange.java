package org.aion.avm.version2.contracts.exchange_off_chain;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import java.math.BigInteger;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

/**
 * Basic exchange contract that allows token registration and exchange settlements.
 *
 * This implementation does not contain billing functionality for the exchange.
 *
 * @author Alexandra Roatis
 */
public class OffChainTokenExchange {

    private static final Address owner;
    private static final Address exchange;

    static {
        owner = Blockchain.getCaller();
        exchange = Blockchain.getAddress();
        logDeploy(owner);
    }

    /**
     * Register tokens to be accepted by the exchange. Admin operation.
     *
     * <p>TODO: later we can include a token balance transfer here to ensure the given methods work
     */
    @Callable
    public static void registerToken(Address token, String balanceMethod, String transferMethod) {
        requireNonNull(token);
        requireNonNull(balanceMethod);
        requireNonNull(transferMethod);
        requireNull(getToken(token));
        requireNoValue();

        Address caller = Blockchain.getCaller();
        require(caller.equals(owner));

        putToken(token, new TokenUtility(balanceMethod, transferMethod));
        logTokenAdded(token, balanceMethod, transferMethod);
    }

    /**
     * Deregister tokens. Admin operation. Used when the exchange no longer supports exchange of a
     * type of token.
     */
    @Callable
    public static void deregisterToken(Address tokenContract) {
        requireNonNull(tokenContract);
        requireNonNull(getToken(tokenContract));
        requireNoValue();

        Address caller = Blockchain.getCaller();
        require(caller.equals(owner));

        putToken(tokenContract, null);
        logTokenRemoved(tokenContract);
    }

    /**
     * Performs a trade (where the settlement is a buy operation) between account A and account B
     * using the exchange contract EX as follows:
     *
     * <ul>
     *   <li>execute invokable: account A sends tokens T to account EX
     *   <li>execute invokable: account B sends coins C to account A
     *   <li>execute internal: account EX sends tokens T to account B
     * </ul>
     */
    @Callable
    public static void settleBuy(
            Address seller,
            Address buyer,
            Address token,
            BigInteger amount,
            BigInteger cost,
            byte[] tokenTransfer,
            byte[] coinTransfer) {
        requireNonNull(seller);
        requireNonNull(buyer);
        requireNonNull(token);
        requirePositive(amount);
        requirePositive(cost);
        requireNonNull(tokenTransfer);
        requireNonNull(coinTransfer);
        requireNoValue();
        require(Blockchain.getCaller().equals(owner));

        // ensure the token is registered
        TokenUtility utility = getToken(token);
        requireNonNull(utility);

        BigInteger initialBalanceSeller = Blockchain.getBalance(seller);
        BigInteger initialBalanceBuyer = Blockchain.getBalance(buyer);
        require(initialBalanceBuyer.compareTo(cost) >= 0);

        BigInteger initialTokensSeller = getTokenBalance(seller, token, utility);
        BigInteger initialTokensExchange = getTokenBalance(exchange, token, utility);
        BigInteger initialTokensBuyer = getTokenBalance(buyer, token, utility);
        require(initialTokensSeller.compareTo(amount) >= 0);

        // invoke the token transfer and ensure success
        secureInvoke(tokenTransfer, Blockchain.getEnergyLimit());
        BigInteger currentTokensSeller = getTokenBalance(seller, token, utility);
        BigInteger currentTokensExchange = getTokenBalance(exchange, token, utility);
        require(initialTokensSeller.subtract(currentTokensSeller).equals(amount));
        require(currentTokensExchange.subtract(initialTokensExchange).equals(amount));

        // invoke the coin transfer and ensure success
        secureInvoke(coinTransfer, Blockchain.getEnergyLimit());
        BigInteger currentBalanceSeller = Blockchain.getBalance(seller);
        BigInteger currentBalanceBuyer = Blockchain.getBalance(buyer);
        require(currentBalanceSeller.subtract(initialBalanceSeller).equals(cost));
        require(initialBalanceBuyer.subtract(currentBalanceBuyer).equals(cost));

        // perform the final token transfer
        byte[] transferData = utility.callForTransfer(exchange, buyer, amount);
        secureCall(token, BigInteger.ZERO, transferData, Blockchain.getEnergyLimit());
        initialTokensExchange = currentTokensExchange;
        currentTokensExchange = getTokenBalance(exchange, token, utility);
        BigInteger currentTokensBuyer = getTokenBalance(buyer, token, utility);
        require(initialTokensExchange.subtract(currentTokensExchange).equals(amount));
        require(currentTokensBuyer.subtract(initialTokensBuyer).equals(amount));

        // log that transfer was successful
        logSealedTransfer(seller, buyer, token, amount, cost);
    }

    /**
     * Performs a trade (where the settlement is a sell operation) between account A and account B
     * using the exchange contract EX as follows:
     *
     * <ul>
     *   <li>execute invokable: account B sends coins C to account EX
     *   <li>execute invokable: account A sends tokens T to account B
     *   <li>execute internal: account EX sends coins C to account A
     * </ul>
     */
    @Callable
    public static void settleSell(
            Address seller,
            Address buyer,
            Address token,
            BigInteger amount,
            BigInteger cost,
            byte[] coinTransfer,
            byte[] tokenTransfer) {
        requireNonNull(seller);
        requireNonNull(buyer);
        requireNonNull(token);
        requirePositive(amount);
        requirePositive(cost);
        requireNonNull(tokenTransfer);
        requireNonNull(coinTransfer);
        requireNoValue();
        require(Blockchain.getCaller().equals(owner));

        // ensure the token is registered
        TokenUtility utility = getToken(token);
        requireNonNull(utility);

        BigInteger initialBalanceSeller = Blockchain.getBalance(seller);
        BigInteger initialBalanceExchange = Blockchain.getBalance(exchange);
        BigInteger initialBalanceBuyer = Blockchain.getBalance(buyer);
        require(initialBalanceBuyer.compareTo(cost) >= 0);

        BigInteger initialTokensSeller = getTokenBalance(seller, token, utility);
        BigInteger initialTokensBuyer = getTokenBalance(buyer, token, utility);
        require(initialTokensSeller.compareTo(amount) >= 0);

        // invoke the coin transfer and ensure success
        secureInvoke(coinTransfer, Blockchain.getEnergyLimit());
        BigInteger currentBalanceBuyer = Blockchain.getBalance(buyer);
        BigInteger currentBalanceExchange = Blockchain.getBalance(exchange);
        require(initialBalanceBuyer.subtract(currentBalanceBuyer).equals(cost));
        require(currentBalanceExchange.subtract(initialBalanceExchange).equals(cost));

        // invoke the token transfer and ensure success
        secureInvoke(tokenTransfer, Blockchain.getEnergyLimit());
        BigInteger currentTokensSeller = getTokenBalance(seller, token, utility);
        BigInteger currentTokensBuyer = getTokenBalance(buyer, token, utility);
        require(initialTokensSeller.subtract(currentTokensSeller).equals(amount));
        require(currentTokensBuyer.subtract(initialTokensBuyer).equals(amount));

        // perform the final coin transfer
        secureCall(seller, cost, new byte[0], Blockchain.getEnergyLimit());
        BigInteger currentBalanceSeller = Blockchain.getBalance(seller);
        initialBalanceExchange = currentBalanceExchange;
        currentBalanceExchange = Blockchain.getBalance(exchange);
        require(initialBalanceExchange.subtract(currentBalanceExchange).equals(cost));
        require(currentBalanceSeller.subtract(initialBalanceSeller).equals(cost));

        // log that transfer was successful
        logSealedTransfer(seller, buyer, token, amount, cost);
    }

    private static BigInteger getTokenBalance(Address account, Address token, TokenUtility utility) {
        byte[] getBalanceData = utility.callForBalance(account);
        byte[] r = secureCall(token, BigInteger.ZERO, getBalanceData, Blockchain.getEnergyLimit());
        return new ABIDecoder(r).decodeOneBigInteger();
    }

    @Fallback
    public static void fallback() {
        Blockchain.revert();
    }

    private static void require(boolean condition) {
        // now implements as un-catchable
        Blockchain.require(condition);
    }

    private static void requirePositive(BigInteger num) {
        require(num.signum() == 1);
    }

    private static void requireNonNull(Object obj) {
        require(obj != null);
    }

    private static void requireNull(Object obj) {
        require(obj == null);
    }

    private static void requireNoValue() {
        require(Blockchain.getValue().equals(BigInteger.ZERO));
    }

    private static byte[] secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
        return result.getReturnData();
    }

    private static void secureInvoke(byte[] transaction, long energyLimit) {
        Result result = Blockchain.invokeTransaction(transaction, energyLimit);
        require(result.isSuccess());
    }

    static class TokenUtility {

        String balanceMethodName;
        String transferMethodName;

        protected TokenUtility(String balanceMethodName, String transferMethodName) {
            this.balanceMethodName = balanceMethodName;
            this.transferMethodName = transferMethodName;
        }

        protected byte[] toBytes() {
            byte[] v1 = balanceMethodName.getBytes();
            byte[] v2 = transferMethodName.getBytes();
            int length = v1.length + v2.length;
            return AionBuffer.allocate(length).put(v1).put(v2).getArray();
        }

        protected static TokenUtility fromBytes(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new TokenUtility(new String(buffer.getArray()), new String(buffer.getArray()));
        }

        public byte[] callForBalance(Address account) {
            return new ABIStreamingEncoder()
                    .encodeOneString(balanceMethodName)
                    .encodeOneAddress(account)
                    .toBytes();
        }

        public byte[] callForTransfer(Address from, Address to, BigInteger amount) {
            return new ABIStreamingEncoder()
                    .encodeOneString(transferMethodName)
                    .encodeOneAddress(from)
                    .encodeOneAddress(to)
                    .encodeOneBigInteger(amount)
                    .toBytes();
        }
    }

    protected static void putToken(Address tokenAddress, TokenUtility details) {
        byte[] key = tokenAddress.toByteArray();
        byte[] value = details == null ? null : details.toBytes();
        Blockchain.putStorage(key, value);
    }

    protected static TokenUtility getToken(Address tokenAddress) {
        byte[] key = tokenAddress.toByteArray();
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : TokenUtility.fromBytes(value);
    }

    protected static void logDeploy(Address owner) {
        Blockchain.log("Deployed".getBytes(), owner.toByteArray());
    }

    protected static void logTokenAdded(Address tokenAddress, String balanceMethod, String transferMethod) {
        Blockchain.log(
                "TokenAdded".getBytes(),
                tokenAddress.toByteArray(),
                balanceMethod.getBytes(),
                transferMethod.getBytes());
    }

    protected static void logTokenRemoved(Address tokenAddress) {
        Blockchain.log("TokenRemoved".getBytes(), tokenAddress.toByteArray());
    }

    protected static void logSealedTransfer(Address seller, Address buyer, Address token, BigInteger amount, BigInteger cost) {
        byte[] trade =
                new ABIStreamingEncoder()
                        .encodeOneBigInteger(amount)
                        .encodeOneString(" -> ")
                        .encodeOneBigInteger(cost)
                        .toBytes();

        Blockchain.log(
                "SealedTransfer".getBytes(),
                seller.toByteArray(),
                buyer.toByteArray(),
                token.toByteArray(),
                trade);
    }
}
