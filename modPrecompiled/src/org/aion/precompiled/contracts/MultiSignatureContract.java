/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
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
import org.aion.base.vm.IDataWord;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.zero.impl.AionHub;
import org.aion.zero.types.AionTransaction;

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
    private static final int AMOUNT_LEN = 128;
    private static final int SIG_LEN = 96;
    private static final int ADDR_LEN = 32;
    private final Address address;

    public static final int MAX_OWNERS = 10;
    public static final int MIN_OWNERS = 2;
    public static final int MIN_THRESH = 1;

    /**
     * Constructs a new MultiSignatureContract object. This is not a multi-sig wallet itself, simply
     * an instance of the class that facilitates interaction with the wallet.
     *
     * @param track The repository.
     * @param address The address of the calling account.
     * @throws IllegalArgumentException if track or address are null.
     */
    public MultiSignatureContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address address) {

        super(track);
        if (address == null) { throw new IllegalArgumentException("Null address."); }
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
     *   arguments: [<32b - walletId> | <96b to 960b - signatures> | <128b - amount> |
     *       <8b - nrgPrice> | <32b - to>]
     *   total: 297-1161 bytes
     *
     *
     * Important: the output of a successful call to this method using operation 0x0 will return a
     * ContractExecutionResult whose output field is the address of the newly created wallet. This
     * address is the walletId that is required by operation 0x1 in order to send a transaction
     * successfully.
     *
     * The account address that calls this execute method must be one of the owners of the wallet
     * for all supported operations.
     *
     *
     * The signatures must all sign the exact same message that must obey the following format:
     *
     *   | nonce | recipientAddr | amount | nrgLimit | nrgPrice |
     *
     * where nonce is the nonce of the multi-sig wallet.
     * Use the regular BigInteger toByteArray method and convert a long to a byte array using
     * ByteBuffer, then concatenate all the separate arrays into one and sign that.
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrg) {
        if (nrg < COST) { return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0); }
        if (input == null) { return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0); }
        if (input.length < 1) { return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0); }

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
        if (input.length < 1 + Long.BYTES) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        ByteBuffer thresh = ByteBuffer.allocate(Long.BYTES);
        thresh.put(Arrays.copyOfRange(input, 1, 1 + Long.BYTES));
        thresh.flip();
        long threshold = thresh.getLong();
        Set<Address> owners = extractAddresses(Arrays.copyOfRange(input, 1 + Long.BYTES, input.length));

        if (owners == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if ((owners.size() < MIN_OWNERS) || (owners.size() > MAX_OWNERS)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if ((threshold < MIN_THRESH) || (threshold > owners.size())) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        Address wallet = initNewWallet(owners, threshold);
        track.flush();
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
        if (length > 1 + ADDR_LEN + (SIG_LEN * MAX_OWNERS) + AMOUNT_LEN + Long.BYTES + ADDR_LEN) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        int walletStart = 1;
        int sigsStart = walletStart + ADDR_LEN - 1;
        int recipientStart = length - ADDR_LEN - 1;
        int nrgStart = recipientStart - Long.BYTES - 1;
        int amountStart = nrgStart - AMOUNT_LEN - 1;

        if (sigsStart > amountStart) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        Address wallet = new Address(Arrays.copyOfRange(input,walletStart, sigsStart));
        List<byte[]> sigs = extractSignatures(Arrays.copyOfRange(input, sigsStart, amountStart));
        BigInteger amount = new BigInteger(Arrays.copyOfRange(input, amountStart, recipientStart));
        Address recipient = new Address(Arrays.copyOfRange(input, recipientStart, length));

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(input, nrgStart, recipientStart));
        Long nrgPrice = buffer.getLong();

        if (track.getStorageValue(wallet, new DataWord(getMetaDataKey())) == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if (sigs == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        byte[] msg = reconstructMsg(wallet, Arrays.copyOfRange(input, amountStart, input.length), nrg);
        if (isValidAmount(wallet, amount) && areValidSignatures(wallet, sigs, msg)) {
            AionTransaction tx = constructTx(wallet, recipient, amount.toByteArray(), nrg, nrgPrice);
            AionHub.inst().getPendingState().addPendingTransaction(tx);
            track.flush();
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
     *   3. The length of addresses is too long (more than ADDR_LEN * MAX_OWNERS).
     *   4. An address appears more than once in addresses.
     *   5. The address of the account that called execute is not in addresses.
     *   6. An address does not exist in the repository.
     *   7. An address is the address of a multi-sig wallet.
     *
     * @param addresses A byte array of consecutive addresses.
     * @return The addresses extracted from the byte array.
     */
    private Set<Address> extractAddresses(byte[] addresses) {
        int length = addresses.length;
        int numAddrs = length / ADDR_LEN;

        if ((length == 0) || (length % ADDR_LEN != 0) || (length > (ADDR_LEN * MAX_OWNERS))) {
            return null;
        }

        Set<Address> result = new HashSet<>(numAddrs);
        Address addr;
        boolean callerIsOwner = false;
        for (int i = 0; i < length; i += ADDR_LEN) {
            addr = new Address(Arrays.copyOfRange(addresses, i, i + ADDR_LEN));
            if (!track.hasAccountState(addr)) { return null; }
            if (result.contains(addr)) { return null; }
            if (track.getStorageValue(addr, new DataWord(getMetaDataKey())) != null) { return null; }
            if (addr.equals(this.address)) { callerIsOwner = true; }
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
     *   3. The length of signatures is too long (more than SIG_LEN * MAX_OWNERS).
     *
     * @param signatures A byte array of consecutive signatures.
     * @return The signatures extracted from the byte array.
     */
    private List<byte[]> extractSignatures(byte[] signatures) {
        int length = signatures.length;
        int numSigs = length / SIG_LEN;

        if ((length == 0) || (length % SIG_LEN != 0) || (length > (SIG_LEN * MAX_OWNERS))) {
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
     * This method assumes the follow condition holds: 1 <= threshold <= |owners| <= MAX_OWNERS
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
        byte[] metaKey = getMetaDataKey();
        byte[] metaValue = new byte[DataWord.BYTES];

        ByteBuffer data = ByteBuffer.allocate(Long.BYTES);
        data.putLong(threshold);
        System.arraycopy(data.array(), 0, metaValue, 0, Long.BYTES);

        data.clear();
        data.putLong(numOwners);
        System.arraycopy(data.array(), 0, metaValue, Long.BYTES, Long.BYTES);

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
        byte[] firstKey, firstValue, secondKey, secondValue;
        long count = 0;

        for (Address owner : owners) {
            firstKey = getOwnerDataKey(true, count);
            secondKey = getOwnerDataKey(false, count);

            // set the two values for this owner.
            firstValue = new byte[DataWord.BYTES];
            secondValue = new byte[DataWord.BYTES];
            System.arraycopy(owner.toBytes(), 0, firstValue, 0, DataWord.BYTES);
            System.arraycopy(owner.toBytes(), DataWord.BYTES, secondValue, 0, DataWord.BYTES);

            track.addStorageRow(walletId, new DataWord(firstKey), new DataWord(firstValue));
            track.addStorageRow(walletId, new DataWord(secondKey), new DataWord(secondValue));
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
        if (amount.compareTo(BigInteger.ONE) < 0) { return false; }
        return track.getBalance(wallet).compareTo(amount) >= 0;
    }

    /**
     * Returns true only if the following conditions are met:
     *   1. ALL signatures have been signed by unique signers.
     *   2. ALL signers are owners of the multi-sig wallet whose address is wallet.
     *   3. ALL signatures are valid signatures that sign the current transaction.
     *   4. The number of signatures is at least the threshold value of the multi-sig wallet wallet.
     *   5. The account that called execute is one of the owners of the wallet.
     *
     * Returns false otherwise.
     *
     * @param wallet The address of the multi-sig wallet.
     * @param signatures The signatures on the transaction.
     * @param msg The byte array form of the transaction that each signee had to sign.
     * @return true only if the signatures are valid for this wallet.
     */
    private boolean areValidSignatures(Address wallet, List<byte[]> signatures, byte[] msg) {
        Set<Address> owners = getOwners(wallet);
        if (!owners.contains(this.address)) { return false; }

        Set<Address> txSigners = new HashSet<>();
        Address signer;
        for (byte[] sig : signatures) {
            if (!signatureIsCorrect(sig, msg)) { return false; }
            signer = new Address(AddressSpecs.computeA0Address(sig));
            if (txSigners.contains(signer)) { return false; }
            if (!owners.contains(signer)) { return false; }
            txSigners.add(signer);
        }

        IDataWord metaValue = track.getStorageValue(wallet, new DataWord(getMetaDataKey()));
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(metaValue.getData(), 0, Long.BYTES));
        long threshold = buffer.getLong();
        return signatures.size() >= threshold;
    }

    /**
     * Returns true only if the current transaction for the multi-sig wallet has been signed
     * correctly by some account and that signature is signature. Returns false otherwise.
     *
     * @param signature The signature to verify.
     * @param msg The transaction as a byte array that was supposedly signed.
     * @return true only if signature is valid.
     */
    private boolean signatureIsCorrect(byte[] signature, byte[] msg) {
        ISignature sig = Ed25519Signature.fromBytes(signature);
        return ECKeyEd25519.verify(msg, sig.getSignature(), sig.getPubkey(null));
    }

    /**
     * Re-constructs and returns this transaction as a byte array message.
     * The format of the transaction as a message is defined as:
     *
     *   | nonce | amount | nrgPrice | recipient | nrgLimit |
     *
     * If the transaction is not signed as a byte array obeying the above format it will not be
     * approved.
     *
     * @param amountPriceTo A byte array representing: | amount | nrgPrice | recipient |
     * @param nrgLimit The energy limit.
     * @return the transaction message that was signed.
     */
    private byte[] reconstructMsg(Address walletId, byte[] amountPriceTo, long nrgLimit) {
        BigInteger nonceBI = track.getNonce(walletId);
        byte[] nonce = nonceBI.toByteArray();
        byte[] msg = new byte[nonce.length + amountPriceTo.length + Long.BYTES];

        ByteBuffer buffer = ByteBuffer.allocate(nonce.length + amountPriceTo.length + Long.BYTES);
        buffer.put(nonce);
        buffer.put(amountPriceTo);
        buffer.putLong(nrgLimit);
        buffer.get(msg);
        return msg;
    }

    /**
     * Constructs and returns the transaction that was requested by the execute method.
     *
     * This method assumes that the fields the transaction will be initialized with are all valid.
     *
     * @param walletId The ID of the multi-sig wallet sending the tx.
     * @param to The recipient of the tx.
     * @param amt The amount to transfer.
     * @param nrg The energy limit.
     * @param nrgPrice The energy price.
     * @return the requested transaction.
     */
    private AionTransaction constructTx(Address walletId, Address to, byte[] amt, long nrg,
        long nrgPrice) {

        BigInteger nonceBI = track.getNonce(walletId);
        byte[] data = new byte[]{ 0 };
        return new AionTransaction(nonceBI.toByteArray(), walletId, to, amt, data, nrg, nrgPrice);
    }

    /**
     * Returns a set of all of the addresses that own the multi-sig wallet whose address is walletId.
     *
     * This method assumes that walletId is a valid multi-sig wallet.
     *
     * @param walletId The address of the multi-sig wallet.
     * @return the set of owners.
     */
    private Set<Address> getOwners(Address walletId) {
        Set<Address> owners = new HashSet<>();

        IDataWord metaValue = track.getStorageValue(walletId, new DataWord(getMetaDataKey()));
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(metaValue.getData(), Long.BYTES, DataWord.BYTES));
        long numOwners = buffer.getLong();

        for (long i = 0; i < numOwners; i++) {
            owners.add(getOwner(walletId, i));
        }
        return owners;
    }

    /**
     * Returns the address of the owner whose owner id is ownerId for the multi-sig wallet whose
     * address is walletId.
     *
     * This method assumes that walletId is a valid multi-sig wallet and that ownerId is a valid
     * owner id.
     *
     * @param walletId The address of the multi-sig wallet.
     * @param ownerId The owner id.
     * @return the address of the owner.
     */
    private Address getOwner(Address walletId, long ownerId) {
        byte[] address = new byte[ADDR_LEN];

        byte[] ownerDataKey1 = getOwnerDataKey(true, ownerId);
        IDataWord addrPortion = track.getStorageValue(walletId, new DataWord(ownerDataKey1));
        System.arraycopy(addrPortion.getData(), 0, address, 0, DataWord.BYTES);

        byte[] ownerDataKey2 = getOwnerDataKey(false, ownerId);
        addrPortion = track.getStorageValue(walletId, new DataWord(ownerDataKey2));
        System.arraycopy(addrPortion.getData(), 0, address, DataWord.BYTES, DataWord.BYTES);

        return new Address(address);
    }

    /**
     * Returns the key to query a multi-sig wallet for its meta data value.
     *
     * @return the meta data query key.
     */
    private static byte[] getMetaDataKey() {
        byte[] metaKey = new byte[DataWord.BYTES];
        metaKey[0] = (byte) 0x80;
        return metaKey;
    }

    /**
     * Returns the key to query a multi-sig wallet for its owner data value.
     *
     * @param isFirstHalf True if querying first half of address.
     * @param ownerId The owner id.
     * @return the owner data query key.
     */
    private static byte[] getOwnerDataKey(boolean isFirstHalf, long ownerId) {
        byte[] ownerKey = new byte[DataWord.BYTES];
        if (!isFirstHalf) {
            ownerKey[0] = (byte) 0x40;
        }
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(ownerId);
        System.arraycopy(buffer.array(), 0, ownerKey, DataWord.BYTES - Long.BYTES, Long.BYTES);
        return ownerKey;
    }

}