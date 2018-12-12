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
import org.aion.base.type.AionAddress;
import org.aion.vm.FastVmResultCode;
import org.aion.vm.FastVmTransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.base.vm.IDataWord;

/**
 * An N of M implementation of a multi-signature pre-compiled contract.
 *
 * <p>The MultiSignatureContract supports two primary operations: create and send. Create will
 * create a new multi-sig wallet and will set its M immutable owners and an immutable threshold
 * value N <= M that requires N signatures per each send operation. The send operation allows a
 * specified amount to be transferred from the multi-sig wallet to some specified recipient and must
 * be signed by at least N of the wallet's owners.
 *
 * <p>The MultiSignatureContract allows for a total of 10 owners per wallet. Each wallet is given a
 * unique id or address and funds can be transferred to the wallet the same way funds are
 * transferred to any other account address.
 *
 * @author nick nadeau
 */
public final class MultiSignatureContract extends StatefulPrecompiledContract {
    private static final long COST = 21000L; // default cost for now; will need to be adjusted.
    private static final byte AION_PREFIX = (byte) 0xa0;
    private static final int AMOUNT_LEN = 128;
    private static final int SIG_LEN = 96;
    private static final int ADDR_LEN = 32;
    private final AionAddress caller;

    public static final int MAX_OWNERS = 10;
    public static final int MIN_OWNERS = 2;
    public static final int MIN_THRESH = 1;

    /**
     * Constructs a new MultiSignatureContract object. This is not a multi-sig wallet itself, simply
     * an instance of the class that facilitates interaction with the wallet.
     *
     * @param track The repository.
     * @param caller The address of the calling account.
     * @throws IllegalArgumentException if track or caller are null.
     */
    public MultiSignatureContract(
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
            AionAddress caller) {

        super(track);
        if (caller == null) {
            throw new IllegalArgumentException("Null caller.");
        }
        this.caller = caller;
    }

    /**
     * Returns a properly formatted byte array to be provided as the input parameter of the
     * contract's execute method when the desired operation to be performed is to create a new
     * multi-sig wallet.
     *
     * @param threshold The proposed threshold of the new multi-sig wallet.
     * @param owners The proposed owners of the new multi-sig wallet.
     * @return a byte array for executing a create-wallet operation.
     */
    public static byte[] constructCreateWalletInput(long threshold, List<AionAddress> owners) {
        int len = 1 + Long.BYTES + (owners.size() * AionAddress.SIZE);
        byte[] input = new byte[len];

        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.put(new byte[] {(byte) 0x0});
        buffer.putLong(threshold);
        for (AionAddress addr : owners) {
            buffer.put(addr.toBytes());
        }

        buffer.flip();
        buffer.get(input);
        return input;
    }

    /**
     * Returns a properly formatted byte array to be provided as the input parameter of the
     * contract's execute method when the desired operation to be performed is to send a transaction
     * from a multi-sig wallet.
     *
     * @param wallet The address of the multi-sig wallet.
     * @param signatures The signatures for the proposed transaction.
     * @param amount The proposed amount to transfer from the wallet.
     * @param nrgPrice The proposed energy price for the transaction.
     * @param to The proposed recipient for the transaction.
     * @return a byte array for executing a send-transaction operation.
     */
    public static byte[] constructSendTxInput(
            AionAddress wallet,
            List<ISignature> signatures,
            BigInteger amount,
            long nrgPrice,
            AionAddress to) {

        int len =
                1
                        + (AionAddress.SIZE * 2)
                        + (signatures.size() * SIG_LEN)
                        + AMOUNT_LEN
                        + Long.BYTES;
        byte[] input = new byte[len];

        int index = 0;
        input[index] = (byte) 0x1;
        index++;
        System.arraycopy(wallet.toBytes(), 0, input, index, AionAddress.SIZE);
        index += AionAddress.SIZE;

        for (ISignature sig : signatures) {
            byte[] sigBytes = sig.toBytes();
            System.arraycopy(sigBytes, 0, input, index, sigBytes.length);
            index += sigBytes.length;
        }

        byte[] amt;
        if (amount.compareTo(BigInteger.ZERO) < 0) {
            amt = handleNegativeBigInt(amount.toByteArray());
        } else {
            amt = new byte[AMOUNT_LEN];
            byte[] amtBytes = amount.toByteArray();
            System.arraycopy(amtBytes, 0, amt, AMOUNT_LEN - amtBytes.length, amtBytes.length);
        }
        System.arraycopy(amt, 0, input, index, AMOUNT_LEN);
        index += AMOUNT_LEN;

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(nrgPrice);
        buffer.flip();
        byte[] nrg = new byte[Long.BYTES];
        buffer.get(nrg);
        System.arraycopy(nrg, 0, input, index, Long.BYTES);
        index += Long.BYTES;

        System.arraycopy(to.toBytes(), 0, input, index, AionAddress.SIZE);
        return input;
    }

    /**
     * This method should be called by the owners of a multi-sig wallet in order to produce a byte
     * array of a transaction to be signed. Each owner should call this method with identical
     * parameters to ensure they are each signing the same transaction. If a byte array with a
     * different format is signed the transaction will be rejected.
     *
     * <p>This method constructs and returns this transaction as a byte array message.
     *
     * @param walletId The address of the multi-sig wallet.
     * @param nonce The nonce of the multi-sig wallet.
     * @param to The address of the recipient.
     * @param amount The amount to transfer.
     * @param nrgPrice The energy price.
     * @return the transaction message that was signed.
     */
    public static byte[] constructMsg(
            AionAddress walletId, BigInteger nonce, AionAddress to, BigInteger amount, long nrgPrice) {

        byte[] nonceBytes = nonce.toByteArray();
        byte[] toBytes = to.toBytes();
        byte[] amountBytes = amount.toByteArray();
        int len =
                AionAddress.SIZE
                        + nonceBytes.length
                        + toBytes.length
                        + amountBytes.length
                        + Long.BYTES;

        byte[] msg = new byte[len];
        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.put(walletId.toBytes());
        buffer.put(nonceBytes);
        buffer.put(toBytes);
        buffer.put(amountBytes);
        buffer.putLong(nrgPrice);
        buffer.flip();
        buffer.get(msg);
        return msg;
    }

    /**
     * The input parameter of this method is a byte array whose bytes should be supplied in the
     * below formats depending on the desired operation. It is <b>highly recommended</b> that the
     * constructCreateWalletInput method be used to create the input for creating a wallet and the
     * constructSendTxInput method be used for sending a transaction.
     *
     * <p>The format of the input byte array is as follows:
     *
     * <p>[<1b - operation> | <arguments>]
     *
     * <p>Where arguments is determined by operation. The following operations and corresponding
     * arguments are supported:
     *
     * <p>operation 0x0 - create a new multi-sig wallet (minimum 2 owners): arguments: [<8b -
     * threshold> | <64b to 320b - owners>] total: 66-322 bytes
     *
     * <p>operation 0x1 - send a transaction (minimum 1 signature): arguments: [<32b - walletId> |
     * <96b to 960b - signatures> | <128b - amount> | <8b - nrgPrice> | <32b - to>] total: 297-1161
     * bytes
     *
     * <p>Important: the output of a successful call to this method using operation 0x0 will return
     * a ExecutionResult whose output field is the address of the newly created wallet. This address
     * is the walletId that is required by operation 0x1 in order to send a transaction
     * successfully.
     *
     * <p>The account address that calls this execute method must be one of the owners of the wallet
     * for all supported operations.
     *
     * <p>The signatures must all sign the exact same message that must obey the following format:
     *
     * <p>| nonce | recipientAddr | amount | nrgLimit | nrgPrice |
     *
     * <p>It is <b>highly recommended</b> that the constructMsg method be used to produce this
     * message so that all parties can be sure they are signing identical transactions.
     */
    @Override
    public FastVmTransactionResult execute(byte[] input, long nrg) {
        if (nrg < COST) {
            return new FastVmTransactionResult(FastVmResultCode.OUT_OF_NRG, 0);
        }
        if (input == null) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if (input.length < 1) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        int operation = input[0];

        switch (operation) {
            case 0:
                return createWallet(input, nrg);
            case 1:
                return sendTransaction(input, nrg);
            default:
                return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
    }

    /**
     * Returns a ExecutionResult that is the result of the create wallet operation.
     *
     * @param input The full input byte array.
     * @param nrg The energy to use.
     * @return the result of the create operation.
     */
    private FastVmTransactionResult createWallet(byte[] input, long nrg) {
        if (input.length < 1 + Long.BYTES) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        ByteBuffer thresh = ByteBuffer.allocate(Long.BYTES);
        thresh.put(Arrays.copyOfRange(input, 1, 1 + Long.BYTES));
        thresh.flip();
        long threshold = thresh.getLong();
        Set<AionAddress> owners =
                extractAddresses(Arrays.copyOfRange(input, 1 + Long.BYTES, input.length));

        if (!isValidTxNrg(nrg)) {
            return new FastVmTransactionResult(FastVmResultCode.INVALID_NRG_LIMIT, nrg);
        }
        if (owners == null) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if ((owners.size() < MIN_OWNERS) || (owners.size() > MAX_OWNERS)) {
            // sanity check... owners should be null in both these cases really
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if ((threshold < MIN_THRESH) || (threshold > owners.size())) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        AionAddress wallet = initNewWallet(owners, threshold);
        track.flush();
        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, nrg - COST, wallet.toBytes());
    }

    /**
     * Returns a ExecutionResult that is the result of the send transaction operation.
     *
     * @param input The full input byte array.
     * @param nrg The energy to use.
     * @return the result of the send operation.
     */
    private FastVmTransactionResult sendTransaction(byte[] input, long nrg) {
        int length = input.length;
        if (length > 1 + ADDR_LEN + (SIG_LEN * MAX_OWNERS) + AMOUNT_LEN + Long.BYTES + ADDR_LEN) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        int walletStart = 1;
        int sigsStart = walletStart + ADDR_LEN;
        int recipientStart = length - ADDR_LEN;
        int nrgStart = recipientStart - Long.BYTES;
        int amountStart = nrgStart - AMOUNT_LEN;

        // Ensures input is min expected size except possibly signatures, which is checked below.
        if (sigsStart > amountStart) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        AionAddress wallet = new AionAddress(Arrays.copyOfRange(input, walletStart, sigsStart));
        List<byte[]> sigs = extractSignatures(Arrays.copyOfRange(input, sigsStart, amountStart));
        BigInteger amount = new BigInteger(Arrays.copyOfRange(input, amountStart, nrgStart));
        AionAddress recipient = new AionAddress(Arrays.copyOfRange(input, recipientStart, length));

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(input, nrgStart, recipientStart));
        buffer.flip();
        Long nrgPrice = buffer.getLong();

        if (!isValidTxNrg(nrg)) {
            return new FastVmTransactionResult(FastVmResultCode.INVALID_NRG_LIMIT, nrg);
        }
        if (track.getStorageValue(wallet, new DataWord(getMetaDataKey())) == null) {
            // Then wallet is not the address of a multi-sig wallet.
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if (sigs == null) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        byte[] msg = constructMsg(wallet, track.getNonce(wallet), recipient, amount, nrgPrice);
        if (amount.compareTo(BigInteger.ZERO) < 0) {
            // Attempt to transfer negative amount.
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if (!areValidSignatures(wallet, sigs, msg)) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if (track.getBalance(wallet).compareTo(amount) < 0) {
            // Attempt to transfer more than available balance.
            return new FastVmTransactionResult(FastVmResultCode.INSUFFICIENT_BALANCE, 0);
        }

        track.incrementNonce(wallet);
        track.addBalance(wallet, amount.negate());
        track.addBalance(recipient, amount);
        track.flush();
        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, nrg - COST);
    }

    /**
     * Returns a set of addresses that have been extracted from addresses under the assumption
     * addresses is a byte array consisting only of consecutive owner addresses.
     *
     * <p>Returns null if: 1. addresses is an empty array. 2. The length of addresses is incorrect
     * (not a multiple of ADDR_LEN). 3. The length of addresses is too long (more than ADDR_LEN *
     * MAX_OWNERS). 4. An address appears more than once in addresses. 5. The address of the account
     * that called execute is not in addresses. 6. An address does not exist in the repository. 7.
     * An address is the address of a multi-sig wallet.
     *
     * @param addresses A byte array of consecutive addresses.
     * @return The addresses extracted from the byte array.
     */
    private Set<AionAddress> extractAddresses(byte[] addresses) {
        int length = addresses.length;
        int numAddrs = length / ADDR_LEN;

        if ((length == 0) || (length % ADDR_LEN != 0) || (length > (ADDR_LEN * MAX_OWNERS))) {
            return null;
        }

        Set<AionAddress> result = new HashSet<>(numAddrs);
        AionAddress addr;
        boolean addressIsOwner = false;
        for (int i = 0; i < length; i += ADDR_LEN) {
            addr = new AionAddress(Arrays.copyOfRange(addresses, i, i + ADDR_LEN));
            if (result.contains(addr)) {
                return null;
            }
            if (track.getStorageValue(addr, new DataWord(getMetaDataKey())) != null) {
                return null;
            }
            if (addr.equals(this.caller)) {
                addressIsOwner = true;
            }
            result.add(addr);
        }
        return (addressIsOwner) ? result : null;
    }

    /**
     * Returns a list of signatures (as byte arrays) that have been extracted from signatures under
     * the assumption signatures is a byte array consisting only of consecutive signatures.
     *
     * <p>Returns null if: 1. signatures is an empty array. 2. The length of signatures is incorrect
     * (not a multiple of SIG_LEN). 3. The length of signatures is too long (more than SIG_LEN *
     * MAX_OWNERS).
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
     * <p>Returns the address of the newly created wallet.
     *
     * <p>This method assumes the follow condition holds: 1 <= threshold <= |owners| <= MAX_OWNERS
     *
     * @param owners The owners of the wallet.
     * @param threshold The minimum number of signatures required per transaction.
     * @return the address of the newly created wallet.
     */
    private AionAddress initNewWallet(Set<AionAddress> owners, long threshold) {
        List<byte[]> ownerAddrs = new ArrayList<>();
        List<byte[]> ownerNonces = new ArrayList<>();
        for (AionAddress owner : owners) {
            ownerAddrs.add(owner.toBytes());
            ownerNonces.add(track.getNonce(owner).toByteArray());
        }

        int numOwners = ownerAddrs.size();
        int len = Long.BYTES + (owners.size() * ADDR_LEN);
        for (byte[] ownN : ownerNonces) {
            len += ownN.length;
        }

        byte[] content = new byte[len];
        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.putLong(threshold);
        for (int i = 0; i < numOwners; i++) {
            buffer.put(ownerAddrs.get(i));
            buffer.put(ownerNonces.get(i));
        }
        buffer.flip();
        buffer.get(content);

        byte[] hash = HashUtil.keccak256(content);
        hash[0] = AION_PREFIX;

        AionAddress walletId = new AionAddress(hash);
        track.createAccount(walletId);
        saveWalletMetaData(walletId, threshold, owners.size());
        saveWalletOwners(walletId, owners);
        return walletId;
    }

    /**
     * Saves the wallet's meta-data: its threshold and the number of owners it has. This data is
     * stored in a single 128-bit data word using the following format:
     *
     * <p>| bits 127-65: threshold | bits 63-0: numOwners |
     *
     * <p>where 0 is the least significant bit.
     *
     * <p>The KEY for this entry is a 128-bit data word whose most significant bit is 1 and all
     * other bits are 0.
     *
     * @param walletId The address of the multi-sig wallet.
     * @param threshold The minimum number of signatures required.
     * @param numOwners The number of owners for this wallet.
     */
    private void saveWalletMetaData(AionAddress walletId, long threshold, long numOwners) {
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
     * Saves the wallet's owners. Each 32 byte owner address is split into two 16 byte portions. The
     * 128-bit KEY for each owner has the following format:
     *
     * <p>| bit 127: <zero> | bit 126: portion | bits 125-0: ownerId |
     *
     * <p>where 0 is the least significant bit and bit 127 is always set to 0. If bit 126 is 0 then
     * the corresponding VALUE is the first half of the owner's address. If bit 126 is 1 then the
     * corresponding VALUE is the second half of the owner's address. The remaining bits are the
     * ownerId, which is simply a counter from 0 up to numOwners - 1. The ordering is arbitrary.
     *
     * @param walletId The address of the multi-sig wallet.
     * @param owners The owners of the multi-sig wallet.
     */
    private void saveWalletOwners(AionAddress walletId, Set<AionAddress> owners) {
        byte[] firstKey, firstValue, secondKey, secondValue;
        long count = 0;

        for (AionAddress owner : owners) {
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
     * Returns true only if the following conditions are met: 1. ALL signatures have been signed by
     * unique signers. 2. ALL signers are owners of the multi-sig wallet whose address is wallet. 3.
     * ALL signatures are valid signatures that sign the current transaction. 4. The number of
     * signatures is at least the threshold value of the multi-sig wallet wallet. 5. The account
     * that called execute is one of the owners of the wallet.
     *
     * <p>Returns false otherwise.
     *
     * @param wallet The address of the multi-sig wallet.
     * @param signatures The signatures on the transaction.
     * @param msg The byte array form of the transaction that each signee had to sign.
     * @return true only if the signatures are valid for this wallet.
     */
    private boolean areValidSignatures(AionAddress wallet, List<byte[]> signatures, byte[] msg) {
        Set<AionAddress> owners = getOwners(wallet);
        if (!owners.contains(this.caller)) {
            return false;
        }

        Set<AionAddress> txSigners = new HashSet<>();
        AionAddress signer;
        for (byte[] sig : signatures) {
            if (!signatureIsCorrect(sig, msg)) {
                return false;
            }
            signer = new AionAddress(AddressSpecs.computeA0Address(Arrays.copyOfRange(sig, 0, 32)));
            if (txSigners.contains(signer)) {
                return false;
            }
            if (!owners.contains(signer)) {
                return false;
            }
            txSigners.add(signer);
        }

        IDataWord metaValue = track.getStorageValue(wallet, new DataWord(getMetaDataKey()));
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(metaValue.getData(), 0, Long.BYTES));
        buffer.flip();
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
     * Returns a set of all of the addresses that own the multi-sig wallet whose address is
     * walletId.
     *
     * <p>This method assumes that walletId is a valid multi-sig wallet.
     *
     * @param walletId The address of the multi-sig wallet.
     * @return the set of owners.
     */
    private Set<AionAddress> getOwners(AionAddress walletId) {
        Set<AionAddress> owners = new HashSet<>();

        IDataWord metaValue = track.getStorageValue(walletId, new DataWord(getMetaDataKey()));
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(metaValue.getData(), Long.BYTES, DataWord.BYTES));
        buffer.flip();
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
     * <p>This method assumes that walletId is a valid multi-sig wallet and that ownerId is a valid
     * owner id.
     *
     * @param walletId The address of the multi-sig wallet.
     * @param ownerId The owner id.
     * @return the address of the owner.
     */
    private AionAddress getOwner(AionAddress walletId, long ownerId) {
        byte[] address = new byte[ADDR_LEN];

        byte[] ownerDataKey1 = getOwnerDataKey(true, ownerId);
        IDataWord addrPortion = track.getStorageValue(walletId, new DataWord(ownerDataKey1));
        System.arraycopy(addrPortion.getData(), 0, address, 0, DataWord.BYTES);

        byte[] ownerDataKey2 = getOwnerDataKey(false, ownerId);
        addrPortion = track.getStorageValue(walletId, new DataWord(ownerDataKey2));
        System.arraycopy(addrPortion.getData(), 0, address, DataWord.BYTES, DataWord.BYTES);

        return new AionAddress(address);
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

    /**
     * Copies a byte array representing a negative BigInteger, bigIntBytes, into a byte array of
     * appropriate length and sign-extends the array. This method returns the resulting array.
     *
     * @param bigIntBytes A negative BigInteger as bytes.
     * @return a properly-sized and sign-extended byte array of the same BigInteger.
     */
    private static byte[] handleNegativeBigInt(byte[] bigIntBytes) {
        byte[] result = new byte[AMOUNT_LEN];
        int pushback = AMOUNT_LEN - bigIntBytes.length;
        for (int i = 0; i < AMOUNT_LEN; i++) {
            if ((i < pushback) || (bigIntBytes[i - pushback] == 0)) {
                result[i] = (byte) 0xFF;
            } else {
                result[i] = bigIntBytes[i - pushback];
            }
        }
        return result;
    }
}
