package org.aion.avm.version2.contracts.exchange_on_chain;

import static org.aion.avm.version2.contracts.exchange_on_chain.OnChainTokenExchangeStorage.getToken;
import static org.aion.avm.version2.contracts.exchange_on_chain.OnChainTokenExchangeStorage.putToken;
import static org.aion.avm.version2.contracts.exchange_on_chain.OnChainTokenExchangeStorage.removeToken;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import java.math.BigInteger;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.version2.contracts.exchange_on_chain.OnChainTokenExchangeObjects.TokenUtility;

/**
 * Basic exchange contract that manages internal accounts for all its users.
 *
 * @author Alexandra Roatis
 */
public class OnChainTokenExchange {

    private static final Address owner;

    static {
        owner = Blockchain.getCaller();
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
        requireNull(getToken(token));

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

        Address caller = Blockchain.getCaller();
        require(caller.equals(owner));

        removeToken(tokenContract);
        logTokenRemoved(tokenContract);
    }

    /** Deposit tokens for use withing the exchange. */
    @Callable
    public static void deposit(
            Address from, Address token, BigInteger amount, byte[] tokenTransfer) {
        requireNonNull(from);
        requireNonNull(token);
        requirePositive(amount);
        requireNonNull(tokenTransfer);

        // ensure the token is registered
        TokenUtility utility = getToken(token);
        requireNonNull(utility);

        Address self = Blockchain.getAddress();
        BigInteger initialTokensAccount = getTokenBalance(from, token, utility);
        BigInteger initialTokensExchange = getTokenBalance(self, token, utility);
        require(initialTokensAccount.compareTo(amount) >= 0);

        // invoke the token transfer and ensure success
        secureInvoke(tokenTransfer, Blockchain.getEnergyLimit());
        BigInteger currentTokensAccount = getTokenBalance(from, token, utility);
        BigInteger currentTokensExchange = getTokenBalance(self, token, utility);
        require(initialTokensAccount.subtract(currentTokensAccount).equals(amount));
        require(currentTokensExchange.subtract(initialTokensExchange).equals(amount));

        // TODO: add account

        // log that transfer was successful
        logDeposit(from, token, amount);
    }

    private static BigInteger getTokenBalance(
            Address account, Address token, TokenUtility utility) {
        byte[] getBalanceData = utility.callForBalance(account);
        byte[] r = secureCall(token, BigInteger.ZERO, getBalanceData, Blockchain.getEnergyLimit());
        return new ABIDecoder(r).decodeOneBigInteger();
    }

    /** Withdraw tokens the caller owns from the exchange. */
    @Callable
    public static void withdraw(Address from, Address token, BigInteger amount) {
        requireNonNull(from);
        requireNonNull(token);
        requirePositive(amount);

        // ensure the token is registered
        TokenUtility utility = getToken(token);
        requireNonNull(utility);

        Address self = Blockchain.getAddress();
        BigInteger initialTokensExchange = getTokenBalance(self, token, utility);
        BigInteger initialTokensAccount = getTokenBalance(from, token, utility);
        require(initialTokensExchange.compareTo(amount) >= 0);

        // perform the final token transfer
        byte[] transferData = utility.callForTransfer(self, from, amount);
        secureCall(token, BigInteger.ZERO, transferData, Blockchain.getEnergyLimit());
        BigInteger currentTokensExchange = getTokenBalance(self, token, utility);
        BigInteger currentTokensAccount = getTokenBalance(from, token, utility);
        require(initialTokensExchange.subtract(currentTokensExchange).equals(amount));
        require(currentTokensAccount.subtract(initialTokensAccount).equals(amount));

        // TODO: update account

        // log that transfer was successful
        logWithdraw(from, token, amount);
    }

    /** Submit a trade order to the exchange. */
    @Callable
    public static void order(
            Address owner, Address tokenContract, BigInteger amountToTrade, BigInteger price) {
        // TODO
    }

    /** Match an existing trade order from exchange. */
    @Callable
    public static void match(
            Address owner, Address tokenContract, BigInteger amountToTrade, BigInteger price) {
        // TODO
    }

    @Callable
    public static void finalizeMatch(long id) {
        requireNoValue();
        // TODO
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

    private static byte[] secureCall(
            Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
        return result.getReturnData();
    }

    private static void secureInvoke(byte[] transaction, long energyLimit) {
        Result result = Blockchain.invokeTransaction(transaction, energyLimit);
        require(result.isSuccess());
    }

    protected static void logDeploy(Address owner) {
        Blockchain.log("Deployed".getBytes(), owner.toByteArray());
    }

    protected static void logTokenAdded(
            Address tokenAddress, String balanceMethod, String transferMethod) {
        Blockchain.log(
                "TokenAdded".getBytes(),
                tokenAddress.toByteArray(),
                balanceMethod.getBytes(),
                transferMethod.getBytes());
    }

    protected static void logTokenRemoved(Address tokenAddress) {
        Blockchain.log("TokenRemoved".getBytes(), tokenAddress.toByteArray());
    }

    protected static void logDeposit(Address from, Address token, BigInteger amount) {
        Blockchain.log(
                "Deposit".getBytes(),
                from.toByteArray(),
                token.toByteArray(),
                amount.toByteArray());
    }

    protected static void logWithdraw(Address from, Address token, BigInteger amount) {
        Blockchain.log(
                "Withdraw".getBytes(),
                from.toByteArray(),
                token.toByteArray(),
                amount.toByteArray());
    }
}
