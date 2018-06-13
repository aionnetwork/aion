package org.aion.precompiled.contracts;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;

/**
 * An N of M implementation of a multi-signature pre-compiled contract.
 *
 * The MultiSignatureContract supports two primary operations: create and send. Create will create a
 * new multi-sig wallet and will set its M immutable owners and an immutable threshold value N <= M
 * that requires N signatures per each send operation. The send operation allows a specified amount
 * to be transferred from the multi-sig wallet to some specified recipient and must be signed by at
 * least N of the wallet's owners.
 *
 * The MultiSignatureContract allows for a total of 10 owners per wallet. Each wallet is given a
 * unique id or address and funds can be transferred to the wallet the same way funds are
 * transferred to any other account address.
 *
 * @author nick nadeau
 */
public final class MultiSignatureContract extends StatefulPrecompiledContract {

    // set to a default cost for now, this will need to be adjusted
    private static final long COST = 21000L;
    private static final int CAP = 10;
    private static final int ADDR_LEN = 32;
    private static final int SIG_LEN = 128;
    private static final int AMOUNT_LEN = 128;
    private Address address;

    /**
     * Constructs a new MultiSignatureContract object. This is not a multi-sig wallet itself, simply
     * an instance of the class that facilitates interaction with the wallet.
     *
     * @param track The repository.
     * @param address The address of the calling account.
     */
    public MultiSignatureContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address address) {

        super(track);
        this.address = address;
    }

    /**
     * The input parameter of this method is a byte array whose bytes should be supplied in the
     * following expected format(s):
     *
     * [<1b - chainId> | <1b - operation> | <arguments>]
     *
     * Where arguments is determined by operation. The following operations and corresponding
     * arguments are supported:
     *
     * operation 0x0 - create a new multi-sig wallet:
     *   arguments: [<1b - threshold> | <64b to 320b - owners>]
     *   total: 67-323 bytes
     *
     * operation 0x1 - send a transaction:
     *   arguments: [<32b - walletId> | <128b to 1280b - signatures> | <128b - amount> | <32b - to>]
     *   total: 322-1,474 bytes
     *
     *
     * Important: the output of a successful call to this method using operation 0x0 will return a
     * ContractExecutionResult whose output field is the address of the newly created wallet. This
     * address is the walletId that is required by operation 0x1 in order to send a transaction
     * successfully.
     *
     * The account address that calls this execute method must be one of the owners of the wallet
     * for all supported operations.
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrg) {
        if (nrg < COST) {
            return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);
        }

        int chainId = input[0];
        int operation = input[1];

        switch (operation) {
            case 0: return createWallet(chainId, input, nrg);
            case 1: return sendTransaction(chainId, input, nrg);
            default: return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Returns a ContractExecutionResult that is the result of the create wallet operation.
     *
     * @param chainId The chain id.
     * @param input The full input byte array.
     * @param nrg The energy to use.
     * @return the result of the create operation.
     */
    private ContractExecutionResult createWallet(int chainId, byte[] input, long nrg) {
        int threshold = input[2];
        Set<Address> owners = extractAddresses(Arrays.copyOfRange(input, 3, input.length));

        if (owners == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if ((owners.size() < 2) || (owners.size() > CAP)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if ((threshold < 1) || (threshold > owners.size())) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // create a wallet address and track it against the threshold & owners.
        Address dummy = new Address("0x00000000000000000000000000000000");
        return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST, dummy.toBytes());
    }

    /**
     * Returns a ContractExecutionResult that is the result of the send transaction operation.
     *
     * @param chainId The chain id.
     * @param input The full input byte array.
     * @param nrg The energy to use.
     * @return the result of the send operation.
     */
    private ContractExecutionResult sendTransaction(int chainId, byte[] input, long nrg) {
        int length = input.length;
        if (length > ADDR_LEN + (SIG_LEN * CAP) + AMOUNT_LEN + ADDR_LEN) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        int walletStart = 2;
        int sigsStart = walletStart + ADDR_LEN - 1;
        int recipientStart = length - ADDR_LEN;
        int amountStart = recipientStart - AMOUNT_LEN;

        if (sigsStart > amountStart) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        Address wallet = new Address(Arrays.copyOfRange(input,walletStart, sigsStart));
        List<byte[]> sigs = extractSignatures(Arrays.copyOfRange(input, sigsStart, amountStart));
        BigInteger amount = new BigInteger(Arrays.copyOfRange(input, amountStart, recipientStart));
        Address recipient = new Address(Arrays.copyOfRange(input, recipientStart, length));

        if (!isOwner(wallet)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if (sigs == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        if (isValidAmount(wallet, amount) && areValidSignatures(wallet, sigs)) {
            // send transaction to recipient & update track
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST);
        } else {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Returns a set of addresses that have been extracted from addresses under the assumption
     * addresses is a byte array consisting only of consecutive owner addresses.
     *
     * Returns null if:
     *   1. addresses is an empty array.
     *   2. The length of addresses is incorrect (not a multiple of ADDR_LEN).
     *   3. The length of addresses is too long (more than ADDR_LEN * CAP).
     *   4. An address appears more than once in addresses.
     *   5. The address of the account that called execute is not in addresses.
     *
     * @param addresses A byte array of consecutive addresses.
     * @return The addresses extracted from the byte array.
     */
    private Set<Address> extractAddresses(byte[] addresses) {
        int length = addresses.length;
        int numAddrs = length / ADDR_LEN;

        if ((length == 0) || (length % ADDR_LEN != 0) || (length > (ADDR_LEN * CAP))) {
            return null;
        }

        Set<Address> result = new HashSet<>(numAddrs);
        Address addr;
        boolean callerIsOwner = false;
        for (int i = 0; i < length; i += ADDR_LEN) {
            addr = new Address(Arrays.copyOfRange(addresses, i, i + ADDR_LEN));
            if (result.contains(addr)) {
                return null;
            }
            if (addr.equals(this.address)) {
                callerIsOwner = true;
            }
            result.add(addr);
        }
        return (callerIsOwner) ? result : null;
    }

    /**
     * Returns a list of signatures (as byte arrays) that have been extracted from signatures under
     * the assumption signatures is a byte array consisting only of consecutive signatures.
     *
     * Returns null if:
     *   1. signatures is an empty array.
     *   2. The length of signatures is incorrect (not a multiple of SIG_LEN).
     *   3. The length of signatures is too long (more than SIG_LEN * CAP).
     *
     * @param signatures A byte array of consecutive signatures.
     * @return The signatures extracted from the byte array.
     */
    private List<byte[]> extractSignatures(byte[] signatures) {
        int length = signatures.length;
        int numSigs = length / SIG_LEN;

        if ((length == 0) || (length % SIG_LEN != 0) || (length > (SIG_LEN * CAP))) {
            return null;
        }

        List<byte[]> result = new ArrayList<>(numSigs);
        for (int i = 0; i < length; i += SIG_LEN) {
            result.add(Arrays.copyOfRange(signatures, i, i + SIG_LEN));
        }
        return result;
    }

    /**
     * Returns true only if the following conditions are met:
     *   1. amount is greater than zero.
     *   2. The multi-sig wallet whose address is wallet has a balance at least equal to amount.
     *
     * Returns false otherwise.
     *
     * @param wallet The address of the multi-sig wallet.
     * @param amount The amount to transfer from wallet.
     * @return true only if this is a valid amount to transfer from wallet.
     */
    private boolean isValidAmount(Address wallet, BigInteger amount) {
        if (amount.compareTo(BigInteger.ONE) < 0) {
            return false;
        }

        // check that wallet has enough balance.
        return true;
    }

    /**
     * Returns true only if the following conditions are met:
     *   1. ALL signatures have been signed by unique signers.
     *   2. ALL signers are owners of the multi-sig wallet whose address is wallet.
     *   3. The number of signatures is at least the threshold value of the multi-sig wallet wallet.
     *
     * Returns false otherwise.
     *
     * @param wallet The address of the multi-sig wallet.
     * @param signatures The signatures on the transaction.
     * @return true only if the signatures are valid for this wallet.
     */
    private boolean areValidSignatures(Address wallet, List<byte[]> signatures) {
        // check the three conditions.
        return true;
    }

    /**
     * Returns true only if the following condition is met:
     *   1. The address of the account that called execute is one of the owners of the multi-sig
     *      wallet whose address is wallet.
     *
     * Returns false otherwise.
     *
     * @param wallet The address of the multi-sig wallet.
     * @return true only if the caller is one of the owners of this wallet.
     */
    private boolean isOwner(Address wallet) {
        // check the caller is one of the owners.
        // might be more efficient to move this into areValidSignatures method.
        return true;
    }

}