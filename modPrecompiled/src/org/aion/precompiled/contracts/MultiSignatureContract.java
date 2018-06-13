package org.aion.precompiled.contracts;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ISignature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
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
    private static final long COST = 21000L; // default cost for now; will need to be adjusted.
    private static final int CAP = 10;
    private static final int AMOUNT_LEN = 128;
    private static final int SIG_LEN = 96;
    private static final int ADDR_LEN = 32;
    private static final int THRESH_LEN = 8;
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
     * [<1b - operation> | <arguments>]
     *
     * Where arguments is determined by operation. The following operations and corresponding
     * arguments are supported:
     *
     * operation 0x0 - create a new multi-sig wallet (minimum 2 owners):
     *   arguments: [<8b - threshold> | <64b to 320b - owners>]
     *   total: 66-322 bytes
     *
     * operation 0x1 - send a transaction (minimum 1 signature):
     *   arguments: [<32b - walletId> | <96b to 960b - signatures> | <128b - amount> | <32b - to>]
     *   total: 289-1153 bytes
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

        int operation = input[0];

        switch (operation) {
            case 0: return createWallet(input, nrg);
            case 1: return sendTransaction(input, nrg);
            default: return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Returns a ContractExecutionResult that is the result of the create wallet operation.
     *
     * @param input The full input byte array.
     * @param nrg The energy to use.
     * @return the result of the create operation.
     */
    private ContractExecutionResult createWallet(byte[] input, long nrg) {
        ByteBuffer thresh = ByteBuffer.allocate(Long.BYTES);
        thresh.put(Arrays.copyOfRange(input, 2, 2 + THRESH_LEN));
        long threshold = thresh.getLong();
        Set<Address> owners = extractAddresses(Arrays.copyOfRange(input, 2 + THRESH_LEN, input.length));

        if (owners == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if ((owners.size() < 2) || (owners.size() > CAP)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if ((threshold < 1) || (threshold > owners.size())) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        Address wallet = initNewWallet(owners, threshold);
        return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST, wallet.toBytes());
    }

    /**
     * Returns a ContractExecutionResult that is the result of the send transaction operation.
     *
     * @param input The full input byte array.
     * @param nrg The energy to use.
     * @return the result of the send operation.
     */
    private ContractExecutionResult sendTransaction(byte[] input, long nrg) {
        int length = input.length;
        if (length > 1 + ADDR_LEN + (SIG_LEN * CAP) + AMOUNT_LEN + ADDR_LEN) {
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
     * Initializes a new multi-sig wallet whose set of owners is owners and whose minimum signature
     * threshold is threshold.
     *
     * Returns the address of the newly created wallet.
     *
     * This method assumes the follow condition holds: 1 <= threshold <= |owners| <= CAP
     *
     * @param owners The owners of the wallet.
     * @param threshold The minimum number of signatures required per transaction.
     * @return the address of the newly created wallet.
     */
    private Address initNewWallet(Set<Address> owners, long threshold) {
        Address walletId = new Address(ECKeyFac.inst().create().getAddress());
        track.createAccount(walletId);
        saveWalletMetaData(walletId, threshold, owners.size());
        saveWalletOwners(walletId, owners);
        return walletId;
    }

    /**
     * Saves the wallet's meta-data: its threshold and the number of owners it has.
     * This data is stored in a single 128-bit data word using the following format:
     *
     *   | bits 127-65: threshold | bits 63-0: numOwners |
     *
     * where 0 is the least significant bit.
     *
     * The KEY for this entry is a 128-bit data word whose most significant bit is 1 and all other
     * bits are 0.
     *
     * @param walletId The address of the multi-sig wallet.
     * @param threshold The minimum number of signatures required.
     * @param numOwners The number of owners for this wallet.
     */
    private void saveWalletMetaData(Address walletId, long threshold, long numOwners) {
        byte[] metaKey = new byte[DataWord.BYTES];
        metaKey[0] = (byte) 0x80;   // set bit 127

        byte[] metaValue = new byte[DataWord.BYTES];
        ByteBuffer data = ByteBuffer.allocate(Long.BYTES);
        data.putLong(threshold);
        System.arraycopy(data.array(), 0, metaValue, 0, DataWord.BYTES);
        data.clear();
        data.putLong(numOwners);
        System.arraycopy(data.array(), 0, metaValue, DataWord.BYTES, DataWord.BYTES);

        track.addStorageRow(walletId, new DataWord(metaKey), new DataWord(metaValue));
    }

    /**
     * Saves the wallet's owners. Each 32 byte owner address is split into two 16 byte portions.
     * The 128-bit KEY for each owner has the following format:
     *
     *   | bit 127: <zero> | bit 126: portion | bits 125-0: ownerId |
     *
     * where 0 is the least significant bit and bit 127 is always set to 0.
     * If bit 126 is 0 then the corresponding VALUE is the first half of the owner's address.
     * If bit 126 is 1 then the corresponding VALUE is the second half of the owner's address.
     * The remaining bits are the ownerId, which is simply a counter from 0 up to numOwners - 1.
     * The ordering is arbitrary.
     *
     * @param walletId The address of the multi-sig wallet.
     * @param owners The owners of the multi-sig wallet.
     */
    private void saveWalletOwners(Address walletId, Set<Address> owners) {
        ByteBuffer data = ByteBuffer.allocate(Long.BYTES);
        byte[] firstKey, firstValue, secondKey, secondValue;
        long count = 0;

        for (Address owner : owners) {
            firstKey = new byte[DataWord.BYTES];
            firstValue = new byte[DataWord.BYTES];
            secondKey = new byte[DataWord.BYTES];
            secondValue = new byte[DataWord.BYTES];

            // set the two keys for this owner.
            secondKey[0] = (byte) 0x40; // set bit 126
            data.putLong(count);
            System.arraycopy(data.array(), 0, firstKey, DataWord.BYTES - Long.BYTES, Long.BYTES);
            System.arraycopy(data.array(), 0, secondKey, DataWord.BYTES - Long.BYTES, Long.BYTES);

            // set the two values for this owner.
            System.arraycopy(owner.toBytes(), 0, firstValue, 0, DataWord.BYTES);
            System.arraycopy(owner.toBytes(), DataWord.BYTES, secondValue, 0, DataWord.BYTES);

            track.addStorageRow(walletId, new DataWord(firstKey), new DataWord(firstValue));
            track.addStorageRow(walletId, new DataWord(secondKey), new DataWord(secondValue));
            data.clear();
            count++;
        }
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
        return track.getBalance(wallet).compareTo(amount) >= 0;
    }

    /**
     * Returns true only if the following conditions are met:
     *   1. ALL signatures have been signed by unique signers.
     *   2. ALL signers are owners of the multi-sig wallet whose address is wallet.
     *   3. ALL signatures are valid signatures that sign the current transaction.
     *   4. The number of signatures is at least the threshold value of the multi-sig wallet wallet.
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