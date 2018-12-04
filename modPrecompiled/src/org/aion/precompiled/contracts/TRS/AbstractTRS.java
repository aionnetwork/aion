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

package org.aion.precompiled.contracts.TRS;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.type.StatefulPrecompiledContract;

/**
 * The purpose of this abstract class is mostly as a place to store important constants and methods
 * that may be useful to multiple concrete subclasses.
 */
public abstract class AbstractTRS extends StatefulPrecompiledContract {
    // TODO: grab AION from CfgAion later and preferrably aion prefix too.
    static final Address AION =
            Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    static final long COST = 21000L; // temporary.
    private static final long TEST_DURATION = 1;
    private static final long PERIOD_DURATION = TimeUnit.DAYS.toSeconds(30);
    static final byte AION_PREFIX = (byte) 0xA0;
    static final byte TRS_PREFIX = (byte) 0xC0;
    final Address caller;
    protected final IBlockchain blockchain;

    /*
     * The database keys each have unique prefixes denoting the function of that key. Some keys have
     * mutable bytes following this prefix. In these cases oly the prefixes are provided. Otherwise
     * for unchanging keys we store them as an IDataWord object directly.
     */
    private static final IDataWord OWNER_KEY,
            SPECS_KEY,
            LIST_HEAD_KEY,
            FUNDS_SPECS_KEY,
            TIMESTAMP,
            BONUS_SPECS_KEY,
            OPEN_KEY,
            EXTRA_SPECS_KEY,
            NULL32,
            INVALID;
    private static final byte BALANCE_PREFIX = (byte) 0xB0;
    private static final byte LIST_PREV_PREFIX = (byte) 0x60;
    private static final byte FUNDS_PREFIX = (byte) 0x90;
    private static final byte WITHDRAW_PREFIX = (byte) 0x30;
    private static final byte BONUS_PREFIX = (byte) 0x21;
    private static final byte EXTRA_WITH_PREFIX = (byte) 0x80;
    private static final byte EXTRA_WITH_SPEC_PREFIX = (byte) 0x94;
    private static final byte EXTRA_FUNDS_PREFIX = (byte) 0x92;

    private static final int DOUBLE_WORD_SIZE = DoubleDataWord.BYTES;
    private static final int SINGLE_WORD_SIZE = DataWord.BYTES;
    private static final int MAX_DEPOSIT_ROWS = 16;
    private static final byte NULL_BIT = (byte) 0x80;
    private static final byte VALID_BIT = (byte) 0x40;

    static {
        byte[] singleKey = new byte[SINGLE_WORD_SIZE];
        singleKey[0] = (byte) 0xF0;
        OWNER_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0xE0;
        SPECS_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x93;
        EXTRA_SPECS_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x91;
        FUNDS_SPECS_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x70;
        LIST_HEAD_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x50;
        TIMESTAMP = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x20;
        BONUS_SPECS_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x10;
        OPEN_KEY = toIDataWord(singleKey);

        byte[] value = new byte[DOUBLE_WORD_SIZE];
        value[0] = NULL_BIT;
        NULL32 = toIDataWord(value);

        value[0] = (byte) 0x0;
        INVALID = toIDataWord(value);
    }

    // Constructor.
    AbstractTRS(
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
            Address caller,
            IBlockchain blockchain) {
        super(track);
        if (caller == null) {
            throw new NullPointerException("Construct TRS with null caller.");
        }
        this.caller = caller;
        this.blockchain = blockchain;
    }

    // The execute method for subclasses to implement.
    public abstract TransactionResult execute(byte[] input, long nrgLimit);

    // <-------------------------------------HELPER METHODS---------------------------------------->

    private static final int TEST_OFFSET = 9;
    private static final int DIR_DEPO_OFFSET = 10;
    private static final int PRECISION_OFFSET = 11;
    private static final int PERIODS_OFFSET = 12;
    private static final int LOCK_OFFSET = 14;
    private static final int LIVE_OFFSET = 15;

    /**
     * Returns the contract specifications for the TRS contract whose address is contract if this is
     * a valid contract address.
     *
     * <p>Returns null if contract is not a valid TRS contract address and thus there are no specs.
     *
     * @param contract The TRS contract address to query.
     * @return the contract specifications or null if not a TRS contract.
     */
    public byte[] getContractSpecs(Address contract) {
        if (contract.toBytes()[0] != TRS_PREFIX) {
            return null;
        }
        IDataWord spec = track.getStorageValue(contract, SPECS_KEY);
        return (spec == null) ? null : Arrays.copyOf(spec.getData(), spec.getData().length);
    }

    /**
     * Sets the contract specifications for the TRS contract whose address is contract to record the
     * parameters listed in this method's signature.
     *
     * <p>If percent requires more than 9 bytes to represent it then it will be truncated down to 9
     * bytes.
     *
     * <p>This method only succeeds if the specifications entry for contract is empty. Thus this
     * method can be called successfully at most once per contract.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param isTest true if this is a test contract.
     * @param isDirectDeposit true if users can deposit to the contract directly.
     * @param periods the number of withdrawal periods for this contract.
     * @param percent the percentage of the total balance withdrawable in the special one-off event.
     * @param precision the number of decimal places to left-shift percent.
     */
    void setContractSpecs(
            Address contract,
            boolean isTest,
            boolean isDirectDeposit,
            int periods,
            BigInteger percent,
            int precision) {

        if (track.getStorageValue(contract, SPECS_KEY) != null) {
            return;
        }
        byte[] specs = new byte[SINGLE_WORD_SIZE];
        byte[] percentBytes = percent.toByteArray();
        int len = (percentBytes.length > TEST_OFFSET) ? TEST_OFFSET : percentBytes.length;

        System.arraycopy(percentBytes, percentBytes.length - len, specs, TEST_OFFSET - len, len);
        specs[TEST_OFFSET] = (isTest) ? (byte) 0x1 : (byte) 0x0;
        specs[DIR_DEPO_OFFSET] = (isDirectDeposit) ? (byte) 0x1 : (byte) 0x0;
        specs[PRECISION_OFFSET] = (byte) (precision & 0xFF);
        specs[PERIODS_OFFSET] = (byte) ((periods >> Byte.SIZE) & 0xFF);
        specs[PERIODS_OFFSET + 1] = (byte) (periods & 0xFF);
        specs[LOCK_OFFSET] = (byte) 0x0; // sanity
        specs[LIVE_OFFSET] = (byte) 0x0; // sanity

        track.addStorageRow(contract, SPECS_KEY, toIDataWord(specs));
    }

    /**
     * Returns the owner of the TRS contract whose address is contract or null if contract has no
     * owner.
     *
     * @param contract The TRS contract address to query.
     * @return the owner of the contract or null if not a TRS contract.
     */
    public Address getContractOwner(Address contract) {
        IDataWord owner = track.getStorageValue(contract, OWNER_KEY);
        return (owner == null) ? null : new Address(owner.getData());
    }

    /**
     * Sets the current caller to be the owner of the TRS contract given by contract.
     *
     * <p>This method only succeeds if the owner entry for contract is empty. Thus this method can
     * be called successfully at most once per contract.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract to update.
     */
    void setContractOwner(Address contract) {
        if (track.getStorageValue(contract, OWNER_KEY) != null) {
            return;
        }
        track.addStorageRow(contract, OWNER_KEY, toIDataWord(caller.toBytes()));
    }

    /**
     * Returns the byte array representing the head of the linked list for the TRS contract given by
     * contract or null if the head of the list is null.
     *
     * <p>The returned array will be length 32 and the bytes in the index range [1, 31] will be the
     * last 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>This method throws an exception if there is no head entry for contract. This should never
     * happen and is here only for debugging.
     *
     * @param contract The TRS contract to query.
     * @return a byte array if there is a non-null head or null otherwise.
     * @throws NullPointerException if contract has no linked list.
     */
    public byte[] getListHead(Address contract) {
        IDataWord head = track.getStorageValue(contract, LIST_HEAD_KEY);
        if (head == null) {
            throw new NullPointerException("Contract has no list: " + contract);
        }
        byte[] headData = head.getData();
        return ((headData[0] & NULL_BIT) == NULL_BIT)
                ? null
                : Arrays.copyOf(headData, headData.length);
    }

    /**
     * Sets the head of the linked list to head, where head is assumed to be correctly formatted so
     * that the first byte of head is 0x80 iff the head is null, and so that the following 31 bytes
     * of head are the 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>If head is null this method will set the head of the linked list to null. If head is not
     * null this method will set the head to head and will ensure that the null bit is not set.
     *
     * <p>This method does nothing if head is not 32 bytes.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param head The head entry data to add.
     */
    void setListHead(Address contract, byte[] head) {
        if (head == null) {
            track.addStorageRow(contract, LIST_HEAD_KEY, NULL32);
        } else if (head.length == DOUBLE_WORD_SIZE) {
            head[0] = 0x0;
            track.addStorageRow(contract, LIST_HEAD_KEY, toIDataWord(head));
        }
    }

    /**
     * Returns the byte array representing account's previous entry in the linked list for the TRS
     * contract given by contract or null if the previous entry is null.
     *
     * <p>The returned array will be length 32 and the bytes in the index range [1, 31] will be the
     * last 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>This method throws an exception if there is no previous entry for account. This should
     * never happen and is here only for debugging.
     *
     * @param contract The TRS contract to query.
     * @return a byte array if there is a non-null head or null otherwise.
     * @throws NullPointerException if contract has no linked list.
     */
    public byte[] getListPrev(Address contract, Address account) {
        byte[] prevKey = new byte[DOUBLE_WORD_SIZE];
        prevKey[0] = LIST_PREV_PREFIX;
        System.arraycopy(account.toBytes(), 1, prevKey, 1, DOUBLE_WORD_SIZE - 1);

        IDataWord prev = track.getStorageValue(contract, toIDataWord(prevKey));
        if (prev == null) {
            throw new NullPointerException("Account has no prev: " + account);
        }
        byte[] prevData = prev.getData();
        return ((prevData[0] & NULL_BIT) == NULL_BIT)
                ? null
                : Arrays.copyOf(prevData, prevData.length);
    }

    /**
     * Sets account's previous entry in the linked list to prev, where prev is assumed to be
     * correctly formatted so that the first byte of prev is 0x80 iff the previous entry is null,
     * and so that the following 31 bytes of prev are the 31 bytes of a valid Aion account address
     * without the Aion prefix.
     *
     * <p>If prev is null this method will set the previous entry for account to null. If prev is
     * not null this method will set account's previous entry to prev and ensure the null bit is not
     * set.
     *
     * <p>This method does nothing if prev is not 32 bytes.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param account The account in contract whose previous entry is being updated.
     * @param prev The previous entry.
     */
    void setListPrevious(Address contract, Address account, byte[] prev) {
        setListPrevious(contract, account.toBytes(), prev);
    }

    /**
     * Sets account's previous entry in the linked list to prev, where prev is assumed to be
     * correctly formatted so that the first byte of prev is 0x80 iff the previous entry is null,
     * and so that the following 31 bytes of prev are the 31 bytes of a valid Aion account address
     * without the Aion prefix.
     *
     * <p>If prev is null this method will set the previous entry for account to null. If prev is
     * not null this method will set account's previous entry to prev and ensure the null bit is not
     * set.
     *
     * <p>This method does nothing if prev is not 32 bytes.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param account The account in contract whose previous entry is being updated.
     * @param prev The previous entry.
     */
    void setListPrevious(Address contract, byte[] account, byte[] prev) {
        byte[] prevKey = new byte[DOUBLE_WORD_SIZE];
        prevKey[0] = LIST_PREV_PREFIX;
        System.arraycopy(account, 1, prevKey, 1, DOUBLE_WORD_SIZE - 1);

        if (prev == null) {
            track.addStorageRow(contract, toIDataWord(prevKey), NULL32);
        } else if (prev.length == DOUBLE_WORD_SIZE) {
            prev[0] = 0x0;
            track.addStorageRow(contract, toIDataWord(prevKey), toIDataWord(prev));
        }
    }

    /**
     * Returns account's next entry in the linked list for the TRS contract contract or null if next
     * is null.
     *
     * <p>The returned array will be length 32 and the bytes in the index range [1, 31] will be the
     * last 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>This method throws an exception if there is no next entry for account. This should never
     * happen and is here only for debugging.
     *
     * @param contract The TRS contract to query.
     * @param account The account in contract whose next entry is being updated.
     * @return account's next entry.
     * @throws NullPointerException if account has no next entry.
     */
    public byte[] getListNext(Address contract, Address account) {
        return getListNext(contract, account.toBytes());
    }

    /**
     * Returns account's next entry in the linked list for the TRS contract contract or null if next
     * is null.
     *
     * <p>The returned array will be length 32 and the bytes in the index range [1, 31] will be the
     * last 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>This method throws an exception if there is no next entry for account. This should never
     * happen and is here only for debugging.
     *
     * @param contract The TRS contract to query.
     * @param account The account in contract whose next entry is being updated.
     * @return account's next entry.
     * @throws NullPointerException if account has no next entry.
     */
    byte[] getListNext(Address contract, byte[] account) {
        IDataWord next = track.getStorageValue(contract, toIDataWord(account));
        if (next == null) {
            throw new NullPointerException("Account has no next: " + ByteUtil.toHexString(account));
        }
        byte[] nextData = next.getData();
        return ((nextData[0] & NULL_BIT) == NULL_BIT)
                ? null
                : Arrays.copyOf(nextData, nextData.length);
    }

    /**
     * Returns account's next entry in the linked list for the TRS contract contract as the full
     * byte array. This method does not return null if the next entry's null bit is set! This is a
     * way of getting the exact byte array back, which is useful since this array also has a valid
     * bit, which the null return value of getListNext may hide.
     *
     * <p>The returned array will be length 32 and the bytes in the index range [1, 31] will be the
     * last 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>This method throws an exception if there is no previous entry for account. This should
     * never happen and is here only for debugging.
     *
     * @param contract The TRS contract to query.
     * @param account The account in contract whose next entry is being updated.
     * @return account's next entry.
     * @throws NullPointerException if account has no next entry.
     */
    public byte[] getListNextBytes(Address contract, Address account) {
        IDataWord next = track.getStorageValue(contract, toIDataWord(account.toBytes()));
        if (next == null) {
            throw new NullPointerException("Account has no next: " + account);
        }
        return Arrays.copyOf(next.getData(), next.getData().length);
    }

    /**
     * Sets account's next entry in the linked list to next, where next is assumed to be correctly
     * formatted so that the first byte of head has its most significant bit set if next is null and
     * its second most significant bit set if it is invalid, and so that the following 31 bytes of
     * head are the 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>If isValid is false then the next entry is set to invalid, indicating that this account
     * has been deleted.
     *
     * <p>If isValid is true and next is null this method will set the next entry for account to
     * null. If isValid is true next is not null then this method will set account's next entry to
     * next and ensure the null bit is not set and that the valid bit is set.
     *
     * <p>The oldMeta parameter is the current (soon to be "old") meta-data about the account's next
     * entry. This is the first byte returned by the getListNextBytes or getListNext methods. This
     * byte contains data about validity and the row counts for the account's balance. This method
     * will persist this meta-data except in the case that we are setting the account to be invalid.
     *
     * <p>This method does nothing if next is not 32 bytes.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param account The account in contract whose next entry is being updated.
     * @param next The next entry.
     * @param isValid True only if the account is to be marked as invalid or deleted.
     */
    void setListNext(
            Address contract, Address account, byte oldMeta, byte[] next, boolean isValid) {
        setListNext(contract, account.toBytes(), oldMeta, next, isValid);
    }

    /**
     * Sets account's next entry in the linked list to next, where next is assumed to be correctly
     * formatted so that the first byte of head has its most significant bit set if next is null and
     * its second most significant bit set if it is invalid, and so that the following 31 bytes of
     * head are the 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * <p>If isValid is false then the next entry is set to invalid, indicating that this account
     * has been deleted.
     *
     * <p>If isValid is true and next is null this method will set the next entry for account to
     * null. If isValid is true next is not null then this method will set account's next entry to
     * next and ensure the null bit is not set and that the valid bit is set.
     *
     * <p>The oldMeta parameter is the current (soon to be "old") meta-data about the account's next
     * entry. This is the first byte returned by the getListNextBytes or getListNext methods. This
     * byte contains data about validity and the row counts for the account's balance. This method
     * will persist this meta-data except in the case that we are setting the account to be invalid.
     *
     * <p>This method does nothing if next is not 32 bytes.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param account The account in contract whose next entry is being updated.
     * @param next The next entry.
     * @param isValid True only if the account is to be marked as invalid or deleted.
     */
    void setListNext(Address contract, byte[] account, byte oldMeta, byte[] next, boolean isValid) {
        if (!isValid) {
            // Mark account invalid and also make it ineligible for special withdrawal.
            track.addStorageRow(contract, toIDataWord(account), INVALID);
            setAccountIneligibleForSpecial(contract, account);
        } else if (next == null) {
            byte[] nullNext = Arrays.copyOf(NULL32.getData(), NULL32.getData().length);
            nullNext[0] |= VALID_BIT;
            nullNext[0] |= oldMeta;
            track.addStorageRow(contract, toIDataWord(account), toIDataWord(nullNext));
        } else if (next.length == DOUBLE_WORD_SIZE) {
            next[0] = VALID_BIT;
            next[0] |= oldMeta;
            next[0] &= ~NULL_BIT;
            track.addStorageRow(contract, toIDataWord(account), toIDataWord(next));
        }
    }

    /**
     * Returns true only if the is-valid bit is set in the byte array specs, where it is assumed
     * that spec is the byte array returned by the getListNext method since the account's valid bit
     * is located in that byte array along with the account's next entry.
     *
     * <p>An account marked invalid means that it will deleted from the storage.
     *
     * @param spec The byte array result of getListNext for some account.
     * @return true only if the is-valid bit is set.
     */
    public static boolean accountIsValid(byte[] spec) {
        return ((spec != null) && ((spec[0] & VALID_BIT) == VALID_BIT));
    }

    /**
     * Returns the total deposit balance for the TRS contract given by the address contract.
     *
     * @param contract The TRS contract to query.
     * @return the total balance of the contract.
     */
    public BigInteger getTotalBalance(Address contract) {
        IDataWord ttlSpec = track.getStorageValue(contract, FUNDS_SPECS_KEY);
        int numRows =
                ByteBuffer.wrap(
                                Arrays.copyOfRange(
                                        ttlSpec.getData(),
                                        SINGLE_WORD_SIZE - Integer.BYTES,
                                        SINGLE_WORD_SIZE))
                        .getInt();
        if (numRows == 0) {
            return BigInteger.ZERO;
        }

        byte[] balance = new byte[(numRows * DOUBLE_WORD_SIZE) + 1];
        for (int i = 0; i < numRows; i++) {
            byte[] ttlKey = makeTotalBalanceKey(i);
            byte[] ttlVal = track.getStorageValue(contract, toIDataWord(ttlKey)).getData();
            System.arraycopy(ttlVal, 0, balance, (i * DOUBLE_WORD_SIZE) + 1, DOUBLE_WORD_SIZE);
        }
        return new BigInteger(balance);
    }

    /**
     * Sets the total balance of the TRS contract whose address is contract.
     *
     * <p>If the contract does not have a total balance entry this method will create the entries
     * sufficient to hold it and update the total balance specifications corresponding to it.
     *
     * <p>This method assumes that balance is non-negative.
     *
     * <p>This method does not flush.
     *
     * <p>If balance is negative this method throws an exception. This should never happen and is
     * only here for debugging.
     *
     * @param contract The TRS contract to update.
     * @param balance The total balance to set.
     * @throws IllegalArgumentException if balance is negative.
     */
    void setTotalBalance(Address contract, BigInteger balance) {
        if (balance.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("setTotalBalance to negative balance!");
        }
        byte[] bal = toDoubleWordAlignedArray(balance);
        int numRows = bal.length / DOUBLE_WORD_SIZE;
        for (int i = 0; i < numRows; i++) {
            byte[] ttlKey = makeTotalBalanceKey(i);
            byte[] ttlVal = new byte[DOUBLE_WORD_SIZE];
            System.arraycopy(bal, i * DOUBLE_WORD_SIZE, ttlVal, 0, DOUBLE_WORD_SIZE);
            track.addStorageRow(contract, toIDataWord(ttlKey), toIDataWord(ttlVal));
        }

        // Update total balance specs.
        byte[] ttlSpec = new byte[SINGLE_WORD_SIZE];
        for (int i = 0; i < Integer.BYTES; i++) {
            ttlSpec[SINGLE_WORD_SIZE - i - 1] = (byte) ((numRows >> (i * Byte.SIZE)) & 0xFF);
        }
        track.addStorageRow(contract, FUNDS_SPECS_KEY, toIDataWord(ttlSpec));
    }

    /**
     * Returns the deposit balance for account in the TRS contract given by the address contract.
     *
     * <p>If account does not have a valid entry in this TRS contract, either because it does not
     * exist or its valid bit is unset, then zero is returned.
     *
     * @param contract The TRS contract to query.
     * @param account The account to look up.
     * @return the account's deposit balance for this TRS contract.
     */
    public BigInteger getDepositBalance(Address contract, Address account) {
        IDataWord accountData = track.getStorageValue(contract, toIDataWord(account.toBytes()));
        if (accountData == null) {
            return BigInteger.ZERO;
        }
        if ((accountData.getData()[0] & VALID_BIT) == 0x00) {
            return BigInteger.ZERO;
        }

        int numRows = (accountData.getData()[0] & 0x0F);
        byte[] balance = new byte[(numRows * DOUBLE_WORD_SIZE) + 1];
        for (int i = 0; i < numRows; i++) {
            byte[] balKey = makeBalanceKey(account, i);
            byte[] balVal = track.getStorageValue(contract, toIDataWord(balKey)).getData();
            System.arraycopy(balVal, 0, balance, (i * DOUBLE_WORD_SIZE) + 1, DOUBLE_WORD_SIZE);
        }
        return new BigInteger(balance);
    }

    /**
     * Sets the deposit balance for the account account in the TRS contract given by the address
     * contract to the amount specified by balance and updates the account's 'next' specs to have
     * the number of rows needed to represent this balance.
     *
     * <p>If this is the first deposit for account and account doesn't yet exist then the next entry
     * will have the null bit set, the valid bit unset and the number of rows. Otherwise, for an
     * existing account, the previous setting of the null bit will be persisted and the valid bit
     * will be set and the number of rows will be there along with the address of the next entry.
     *
     * <p>If balance requires more than MAX_DEPOSIT_ROWS storage rows to store, then no update to
     * the account in question will be made and the method will return false.
     *
     * <p>Returns true if the deposit balance was successfully set.
     *
     * <p>This method does not flush.
     *
     * @param contract The TRS contract.
     * @param account The account to update.
     * @param balance The deposit balance to set.
     */
    boolean setDepositBalance(Address contract, Address account, BigInteger balance) {
        if (balance.compareTo(BigInteger.ONE) < 0) {
            return true;
        }
        byte[] bal = toDoubleWordAlignedArray(balance);
        int numRows = bal.length / DOUBLE_WORD_SIZE;
        if (numRows > MAX_DEPOSIT_ROWS) {
            return false;
        }
        for (int i = 0; i < numRows; i++) {
            byte[] balKey = makeBalanceKey(account, i);
            byte[] balVal = new byte[DOUBLE_WORD_SIZE];
            System.arraycopy(bal, i * DOUBLE_WORD_SIZE, balVal, 0, DOUBLE_WORD_SIZE);
            track.addStorageRow(contract, toIDataWord(balKey), toIDataWord(balVal));
        }

        // Update account meta data.
        IDataWord acctData = track.getStorageValue(contract, toIDataWord(account.toBytes()));
        byte[] acctVal;
        if ((acctData == null) || (acctData.equals(INVALID))) {
            // Set null bit and row count but do not set valid bit. Also init withdrawal stats.
            acctVal = new byte[DOUBLE_WORD_SIZE];
            acctVal[0] = (byte) (NULL_BIT | numRows);
            initWithdrawalStats(contract, account);
            initExtraWithdrawalSpecs(contract, account);
        } else {
            // Set valid bit, row count and preserve the previous null bit setting.
            acctVal = acctData.getData();
            acctVal[0] = (byte) ((acctVal[0] & NULL_BIT) | VALID_BIT | numRows);
        }
        track.addStorageRow(contract, toIDataWord(account.toBytes()), toIDataWord(acctVal));
        return true;
    }

    /**
     * Sets the specifications associated with the TRS contract whose address is contract so that
     * the is-locked bit will be set. If the bit is already set this method effectively does
     * nothing.
     *
     * <p>Assumption: contract IS a valid TRS contract address.
     *
     * @param contract The address of the TRS contract.
     */
    void setLock(Address contract) {
        byte[] spec = getContractSpecs(contract);
        spec[LOCK_OFFSET] = (byte) 0x1;
        track.addStorageRow(contract, SPECS_KEY, toIDataWord(spec));
    }

    /**
     * Sets the specifications associated with the TRS contract whose address is contract so that
     * the is-live bit will be set. If the bit is already set this method effectively does nothing.
     *
     * <p>Assumption: contract IS a valid TRS contract address.
     *
     * @param contract The address of the TRS contract.
     */
    void setLive(Address contract) {
        byte[] spec = getContractSpecs(contract);
        spec[LIVE_OFFSET] = (byte) 0x1;
        track.addStorageRow(contract, SPECS_KEY, toIDataWord(spec));
    }

    /**
     * Returns the percentage of the total funds in a TRS contract that are available for withdrawal
     * during the one-off special withdrawal event.
     *
     * <p>Assumption: specs is a byte array returned from the getContractSpecs method.
     *
     * @param specs The specifications of some TRS contract.
     * @return the withdrawal percetage for the one-off event.
     */
    public static BigDecimal getPercentage(byte[] specs) {
        if (specs == null) {
            return BigDecimal.ZERO;
        }
        BigInteger raw = new BigInteger(Arrays.copyOfRange(specs, 0, TEST_OFFSET));
        return new BigDecimal(raw).movePointLeft((int) specs[PRECISION_OFFSET]);
    }

    /**
     * Returns the number of periods for the TRS contract whose specifications are given by specs.
     *
     * <p>Assumption: specs is a byte array returned from the getContractSpecs method.
     *
     * @param specs The specifications of some TRS contract.
     * @return the number of periods for the contract.
     */
    public static int getPeriods(byte[] specs) {
        int periods = specs[PERIODS_OFFSET];
        periods <<= Byte.SIZE;
        periods |= (specs[PERIODS_OFFSET + 1] & 0xFF);
        return periods;
    }

    /**
     * Sets the timestamp for the TRS contract given by contract to timestamp.
     *
     * <p>If contract already has a timestamp set then this method does nothing. Thus the timestamp
     * can only be set once.
     *
     * <p>Assumption: timestamp is the result of System.currentTimeMillis converted to seconds.
     *
     * @param contract The TRS contract to update.
     * @param timestamp The timestamp value to set.
     */
    public void setTimestamp(Address contract, long timestamp) {
        if (track.getStorageValue(contract, TIMESTAMP) != null) {
            return;
        }
        byte[] value = new byte[DataWord.BYTES];

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(timestamp);
        System.arraycopy(buffer.array(), 0, value, DataWord.BYTES - Long.BYTES, Long.BYTES);
        track.addStorageRow(contract, TIMESTAMP, toIDataWord(value));
    }

    /**
     * Returns a non-negative timestamp value associated with the TRS contract given by contract.
     * This is the timestamp that was placed on this contract when it was created.
     *
     * <p>Returns a negative timestamp if contract has no timestamp set. The TRS logic guarantees
     * this happens only when contract is not a valid TRS contract address.
     *
     * @param contract The TRS contract to query.
     * @return The timestamp for the TRS contract contract.
     */
    public long getTimestamp(Address contract) {
        IDataWord value = track.getStorageValue(contract, TIMESTAMP);
        if (value == null) {
            return -1;
        }
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(
                Arrays.copyOfRange(value.getData(), DataWord.BYTES - Long.BYTES, DataWord.BYTES));
        buffer.flip();
        return buffer.getLong();
    }

    /**
     * Returns true if account was able to withdraw a non-zero amount from the TRS contract contract
     * into its account. Returns false otherwise.
     *
     * <p>If this method returns true, signalling a successful withdrawal, then F tokens will have
     * been transferred from the contract into the account and the account will have withdrawn all
     * possible funds it was eligible to withdraw over the period of time from the moment the
     * contract went live until the current time. Thus, the account will be unable to withdraw funds
     * until the next period.
     *
     * <p>If the contract is in its final period then a call to this method will withdraw all
     * remaining funds that account has in the contract.
     *
     * <p>We define F as follows for all periods except the final period (where F is defined
     * trivially as outlined above):
     *
     * <p>F = S + NQ
     *
     * <p>where S is the special one-off withdrawal amount. The first time account tries to withdraw
     * funds S will be possibly non-zero (depending on the one-off withdrawal amount set by the
     * owner) but all subsequent withdrawal attempts we will have S = 0.
     *
     * <p>N is the number of periods account is behind by. So that if the last period account had
     * withdrawn funds during was period T and the contract is current at period P then we have N =
     * P - T.
     *
     * <p>Q is the amount of funds account is eligible to withdraw per each period. This amount does
     * not consider the special one-off event. If the account has a balance of B when the contract
     * goes live and the contract has a total of A periods, and if C is the total amount of bonus
     * tokens in the contract, then the account is eligible to receive a proportional share of C,
     * call this amount D, so that the ratio of D to C is the same as the ratio of B to the
     * contract's total balance when it went live. Then we have Q = ((B + D - |S|) / A)
     *
     * <p>here we define |S| as S above except we assume that the account is always eligible to use
     * the special one-off amount.
     *
     * @param contract The TRS contract to update.
     * @param account The account to withdraw funds from.
     * @return true only if a non-zero amount was withdrawn from the contract into account.
     */
    boolean makeWithdrawal(Address contract, Address account) {
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return false;
        }

        // Grab period here since computations are dependent upon it and a new block may arrive,
        // changing the period mid-computation.
        int currPeriod = calculatePeriod(contract, specs, blockchain.getBestBlock().getTimestamp());
        boolean inFinalPeriod = (getPeriods(specs) == currPeriod);
        BigDecimal fraction = getDepositorFraction(contract, account);

        BigInteger amount, extras;
        if (isAccountDoneWithdrawing(contract, account)) {
            extras = computeExtraFundsToWithdraw(contract, account, fraction, currPeriod);
            if (extras.compareTo(BigInteger.ZERO) > 0) {
                track.addBalance(account, extras);
                updateAccountLastWithdrawalPeriod(contract, account, currPeriod);
                setExtraWithdrawalBalance(
                        contract,
                        account,
                        getExtraWithdrawalBalance(contract, account).add(extras));
                setAccountIneligibleForSpecial(contract, account.toBytes());
                return true;
            }
            return false;
        }

        // Calculate withdrawal amount. If we are in last period or the contract has its funds open,
        // then account can withdraw all outstanding funds.
        if ((inFinalPeriod) || isOpenFunds(contract)) {
            amount = computeOutstadingOwings(contract, account);
            extras = computeExtraFundsToWithdraw(contract, account, fraction, currPeriod);
            setAccountIsDoneWithdrawing(contract, account);
        } else {
            BigInteger specialAmt = computeSpecialWithdrawalAmount(contract, account);
            BigInteger fundsPerPeriod = computeAmountWithdrawPerPeriod(contract, account);
            int numPeriodsBehind = computeNumberPeriodsBehind(contract, account, currPeriod);
            amount =
                    (fundsPerPeriod.multiply(BigInteger.valueOf(numPeriodsBehind))).add(specialAmt);
            extras = computeExtraFundsToWithdraw(contract, account, fraction, currPeriod);
        }

        // If amount is non-zero then transfer the funds and update account's last withdrawal
        // period.
        BigInteger claimed = amount.add(extras);
        if (claimed.compareTo(BigInteger.ZERO) > 0) {
            track.addBalance(account, claimed);
            updateAccountLastWithdrawalPeriod(contract, account, currPeriod);
            setExtraWithdrawalBalance(
                    contract, account, getExtraWithdrawalBalance(contract, account).add(extras));
            setAccountIneligibleForSpecial(contract, account.toBytes());
            return true;
        }
        return false;
    }

    /**
     * Initializes the withdrawal stats associated with account in the TRS contract given by
     * contract. The initial withdrawal stats set the account as eligible for the one-off special
     * withdrawal and set its most recent withdrawal period to period 0.
     *
     * <p>This method will override the current stats if any exist and it should therefore only be
     * called when an account is first depositing and entering the contract.
     *
     * @param contract The TRS contract to update.
     * @param account The account whose withdrawal stats will be initialized.
     */
    private void initWithdrawalStats(Address contract, Address account) {
        byte[] stats = new byte[SINGLE_WORD_SIZE];
        stats[0] = 0x1; // set is-eligible.
        stats[SINGLE_WORD_SIZE - 1] = 0x0; // sanity. Set is-done to false (is done withdrawing)
        track.addStorageRow(contract, toIDataWord(makeWithdrawalKey(account)), toIDataWord(stats));
    }

    /**
     * Sets account as finished withdrawing funds from contract. Once this method is called and this
     * value is set it cannot be unset, and once this value is set the account will no longer be
     * able to withdraw from the contract. This method should only be called in one place: when the
     * account makes a withdrawal in the final withdrawal period. Nowhere else.
     *
     * @param contract The TRS contract to update.
     * @param account The account to update.
     */
    private void setAccountIsDoneWithdrawing(Address contract, Address account) {
        IDataWord stats = track.getStorageValue(contract, toIDataWord(makeWithdrawalKey(account)));
        if (stats == null) {
            return;
        }
        byte[] statsBytes = stats.getData();
        statsBytes[statsBytes.length - 1] = 0x1; // set is-done flag.
        track.addStorageRow(
                contract, toIDataWord(makeWithdrawalKey(account)), toIDataWord(statsBytes));
    }

    /**
     * Returns true only if account is done withdrawing from contract. False otherwise.
     *
     * <p>If this method returns true then account must be prohibited from withdrawing any positive
     * amount of tokens from the contract.
     *
     * <p>If contract or account are invalid this method returns true.
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return true only if account is done withdrawing funds from contract.
     */
    public boolean isAccountDoneWithdrawing(Address contract, Address account) {
        IDataWord stats = track.getStorageValue(contract, toIDataWord(makeWithdrawalKey(account)));
        if (stats == null) {
            return true;
        }
        return stats.getData()[stats.getData().length - 1] == 0x1;
    }

    /**
     * Sets the account in contract as ineligible to use the special one-off withdrawal event. Once
     * an account is set ineligible it can only be made eligible again if that account re-enters the
     * contract. Once the contract is live this is impossible.
     *
     * <p>This method should only ever be called in two cases: 1. The account has been fully
     * refunded and therefore removed from the contract. 2. The account has used the special one-off
     * withdrawal event.
     *
     * @param contract The TRS contract to update.
     * @param account The account to be made ineligible for the special withdrawal.
     */
    private void setAccountIneligibleForSpecial(Address contract, byte[] account) {
        IDataWord stats = track.getStorageValue(contract, toIDataWord(makeWithdrawalKey(account)));
        byte[] statsBytes = (stats == null) ? new byte[SINGLE_WORD_SIZE] : stats.getData();
        statsBytes[0] = 0x0; // unset is-eligible.
        track.addStorageRow(
                contract, toIDataWord(makeWithdrawalKey(account)), toIDataWord(statsBytes));
    }

    /**
     * Sets the period that account most recently withdrew funds from in to period.
     *
     * <p>This method throws an exception if account has no withdrawal stats. This should never
     * happen and is here for debugging.
     *
     * @param contract The TRS contract to update.
     * @param account The account whose most recent period is to be updated.
     * @throws NullPointerException if account has no withdrawal stats.
     */
    private void updateAccountLastWithdrawalPeriod(Address contract, Address account, int period) {
        IDataWord stats = track.getStorageValue(contract, toIDataWord(makeWithdrawalKey(account)));
        if (stats == null) {
            throw new NullPointerException("Account has no withdrawal stats!");
        }
        byte[] statsBytes = stats.getData();
        statsBytes[2] = (byte) (period & 0xFF);
        statsBytes[1] = (byte) ((period >>> Byte.SIZE) & 0xFF);
        track.addStorageRow(
                contract, toIDataWord(makeWithdrawalKey(account)), toIDataWord(statsBytes));
    }

    /**
     * Returns the last period in which account made a withdrawal in the TRS contract contract.
     *
     * <p>If contract is not a TRS contract or if account has no balance in contract then -1 is
     * returned. Otherwise the return value is in the range [0, P], where P is the number of periods
     * the contract has.
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the last period in which account has withdrawn from the contract.
     */
    public int getAccountLastWithdrawalPeriod(Address contract, Address account) {
        IDataWord stats = track.getStorageValue(contract, toIDataWord(makeWithdrawalKey(account)));
        if ((stats == null) || (!accountIsValid(getListNextBytes(contract, account)))) {
            return -1;
        }
        byte[] statsBytes = stats.getData();
        int lastPeriod = statsBytes[1];
        lastPeriod <<= Byte.SIZE;
        lastPeriod |= statsBytes[2];
        return lastPeriod;
    }

    /**
     * Returns true only if the account in the TRS contract given by contract is eligible to use the
     * special one-off withdrawal event.
     *
     * <p>Returns false otherwise.
     *
     * @param contract The TRS contract to query.
     * @param account The account whose special withdrawal eligibility is to be queried.
     * @return true only if the account is eligible for the special withdrawal event.
     */
    public boolean accountIsEligibleForSpecial(Address contract, Address account) {
        IDataWord stats = track.getStorageValue(contract, toIDataWord(makeWithdrawalKey(account)));
        return ((stats != null) && (stats.getData()[0] == 0x1));
    }

    /**
     * Returns the amount of tokens that account is eligible to withdraw from contract using the
     * special one-off event.
     *
     * <p>If account is ineligible to make a special withdrawal then this method returns zero. If
     * account is eligible to make a special withdrawal then this method returns S = MT,
     *
     * <p>where T is the total amount that the contract owes to account over the lifetime of the
     * contract and M is a multiplier in the range [0,1] as defined by the contract owner which
     * signifies the fraction of account's balance that account can claim in the one-off event.
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the amount of tokens account is eligible to withdraw in special one-off event.
     */
    private BigInteger computeSpecialWithdrawalAmount(Address contract, Address account) {
        if (accountIsEligibleForSpecial(contract, account)) {
            return computeRawSpecialAmount(contract, account);
        } else {
            return BigInteger.ZERO;
        }
    }

    /**
     * Returns the amount that account is eligible to withdraw in the special one-off withdrawal
     * event. This method does not care whether or not special withdrawal has occurred or not, it
     * merely reports the amount that can be withdrawn in that event.
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the amount account is eligible to withdraw in the special event.
     */
    BigInteger computeRawSpecialAmount(Address contract, Address account) {
        BigDecimal owed = new BigDecimal(computeTotalOwed(contract, account));
        BigDecimal percent = getPercentage(getContractSpecs(contract)).movePointLeft(2);
        return owed.multiply(percent).toBigInteger();
    }

    /**
     * Returns the number of withdrawal periods that account is behind currPeriod by. That is, if
     * the last period in which account had withdrawn funds was period Q then this method returns:
     *
     * <p>currPeriod - Q
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @param currPeriod The current period the contract is in.
     * @return the number of withdrawal periods account is behind currPeriod by.
     */
    private int computeNumberPeriodsBehind(Address contract, Address account, int currPeriod) {
        return currPeriod - getAccountLastWithdrawalPeriod(contract, account);
    }

    /**
     * Returns the amount of funds that account is eligible to withdraw from contract per each
     * withdrawal period excluding the special withdrawal event. More precisely...
     *
     * <p>Let O be the total amount that account is "owed" by the contract - this is the amount
     * account will receive once it has withdrawn on every period. Let S be the amount account is
     * initially eligible to withdraw for the special one-off withdrawal event. Let P be the total
     * number of periods the contract has.
     *
     * <p>Then the account will be eligible to receive the following amount per each period:
     *
     * <p>(O - S) / P
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the amount of funds account is eligible to withdraw each period, excluding special
     *     funds.
     */
    BigInteger computeAmountWithdrawPerPeriod(Address contract, Address account) {
        BigDecimal owedWithoutSpecial =
                new BigDecimal(
                        computeTotalOwed(contract, account)
                                .subtract(computeRawSpecialAmount(contract, account)));
        BigDecimal totalPeriods = new BigDecimal(getPeriods(getContractSpecs(contract)));
        return owedWithoutSpecial.divide(totalPeriods, 18, RoundingMode.HALF_DOWN).toBigInteger();
    }

    /**
     * Returns the total amount of funds that is owed to account in the TRS contract contract. This
     * is the amount that account will receive once account has successfully withdrawn funds for
     * every period that the contract has.
     *
     * <p>This amount is B + A, where B is the deposit balance in account at the time the contract
     * goes live and A is the share of bonus tokens that account is entitled to.
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the total amount of funds owed to account over the lifetime of the contract.
     */
    public BigInteger computeTotalOwed(Address contract, Address account) {
        return getDepositBalance(contract, account).add(computeBonusShare(contract, account));
    }

    /**
     * Returns the total amount of bonus tokens that account is entitled to receive from the TRS
     * contract contract.
     *
     * <p>If the contract balance is zero then this method should never be called -- we only get
     * here once contract is locked and live, but contract must have non-zero balance to lock --
     * thus an exception is thrown in this case to help with debugging.
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the share of bonus tokens account is entitled to receive.
     * @throws IllegalStateException if contract has no total balance.
     */
    public BigInteger computeBonusShare(Address contract, Address account) {
        BigDecimal bonusFunds = new BigDecimal(getBonusBalance(contract));
        BigDecimal fraction = getDepositorFraction(contract, account);
        BigDecimal share = fraction.multiply(bonusFunds);
        return share.toBigInteger();
    }

    /**
     * Returns the fraction of the total deposits in contract that account owns. This fraction
     * determines the fraction of bonus and extra funds that account is entitled to.
     *
     * @param contract The TRS contract.
     * @param account The account.
     * @return the fraction of the total deposits account owns.
     */
    private BigDecimal getDepositorFraction(Address contract, Address account) {
        BigDecimal acctBalance = new BigDecimal(getDepositBalance(contract, account));
        BigInteger totalBalance = getTotalBalance(contract);
        if (totalBalance.compareTo(BigInteger.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalBalanceDec = new BigDecimal(totalBalance);
        return acctBalance.divide(totalBalanceDec, 18, RoundingMode.HALF_DOWN);
    }

    /**
     * Returns the amount of funds that is still owed to account in the TRS contract contract. That
     * is, if the account is owed T tokens over the lifetime of the contract and the account has
     * already withdrawn R tokens then this method returns:
     *
     * <p>T - R
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the amount of unclaimed tokens account has yet to withdraw from their total owings.
     */
    private BigInteger computeOutstadingOwings(Address contract, Address account) {
        int lastPeriod = getAccountLastWithdrawalPeriod(contract, account);
        BigInteger amtPerPeriod = computeAmountWithdrawPerPeriod(contract, account);
        BigInteger specialAmt = computeRawSpecialAmount(contract, account);

        // If account is eligible for special withdrawal then it has withdrawn zero funds in the
        // special event. Otherwise it has withdrawn specialAmt.
        specialAmt =
                (accountIsEligibleForSpecial(contract, account)) ? BigInteger.ZERO : specialAmt;
        BigInteger totalWithdrawn =
                (amtPerPeriod.multiply(BigInteger.valueOf(lastPeriod))).add(specialAmt);
        BigInteger owings = computeTotalOwed(contract, account);
        return owings.subtract(totalWithdrawn);
    }

    /**
     * Returns the amount of extra funds that account is eligible to withdraw for the current
     * period. Let's assume that: P = total number of periods in the contract E(t) = total amount of
     * extra funds in the contract at some time t F = fraction of total deposits owned by account W
     * = amount of extra funds already withdrawn by account
     *
     * <p>Then this method returns the following when currPeriod < P:
     *
     * <p>((currPeriod x F x E(t))/P) - W
     *
     * <p>This is the total amount of extra funds owed to account over periods 0 to currPeriod minus
     * the amount that account has already claimed of this sum. This formula ensures that as E(t)
     * possibly changes over time that whatever uncollected difference remains is always collected.
     *
     * <p>When currPeriod == P this method returns:
     *
     * <p>(F x E(t)) - W
     *
     * <p>(where x denotes multiplication, not a variable)
     *
     * @param contract The TRS contract.
     * @param account The account.
     * @param currPeriod The current period we are in.
     * @return the extra funds account is able to withdraw.
     */
    public BigInteger computeExtraFundsToWithdraw(
            Address contract, Address account, BigDecimal fraction, int currPeriod) {

        BigInteger share =
                fraction.multiply(new BigDecimal(getExtraFunds(contract))).toBigInteger();
        int periods = getPeriods(getContractSpecs(contract));
        if (currPeriod == periods) {
            return share.subtract(getExtraWithdrawalBalance(contract, account));
        } else {
            BigDecimal owed = new BigDecimal(share.multiply(BigInteger.valueOf(currPeriod)));
            BigDecimal portion =
                    owed.divide(BigDecimal.valueOf(periods), 18, RoundingMode.HALF_DOWN);
            return portion.toBigInteger().subtract(getExtraWithdrawalBalance(contract, account));
        }
    }

    /**
     * Sets the bonus balance for the TRS contract contract to the balance that that account has at
     * the moment this method is called. By "the balance that that account has" we mean the amount
     * of balance directly associated with the address contract in the database, and not the total
     * deposit balance that the contract itself stores over multiple storage rows in the database.
     *
     * <p>If the bonus balance has already been set for the contract then this method does nothing.
     * The bonus balance can only be set once in a contract's lifetime and this method should only
     * be called in exactly two places: when the contract first is started / goes live; when the
     * contrat's funds are opened.
     *
     * <p>Throws an exception if contract has no balance, which should only happen if contract does
     * not exist in the database, here for debugging.
     *
     * @param contract The TRS contract to update.
     * @throws IllegalStateException if contract has no balance.
     */
    void setBonusBalance(Address contract) {
        if (track.getStorageValue(contract, BONUS_SPECS_KEY) != null) {
            return;
        }
        BigInteger balance = track.getBalance(contract);
        if (balance == null) {
            throw new IllegalStateException("Contract has no balance!");
        }

        byte[] bal = toDoubleWordAlignedArray(balance);
        int numRows = bal.length / DOUBLE_WORD_SIZE;
        for (int i = 0; i < numRows; i++) {
            byte[] bonusKey = makeBonusKey(i);
            byte[] bonusVal = new byte[DOUBLE_WORD_SIZE];
            System.arraycopy(bal, i * DOUBLE_WORD_SIZE, bonusVal, 0, DOUBLE_WORD_SIZE);
            track.addStorageRow(contract, toIDataWord(bonusKey), toIDataWord(bonusVal));
        }

        // Update bonus balance specs.
        byte[] bonusSpec = new byte[SINGLE_WORD_SIZE];
        for (int i = 0; i < Integer.BYTES; i++) {
            bonusSpec[SINGLE_WORD_SIZE - i - 1] = (byte) ((numRows >>> (i * Byte.SIZE)) & 0xFF);
        }
        track.addStorageRow(contract, BONUS_SPECS_KEY, toIDataWord(bonusSpec));
    }

    /**
     * Returns the bonus balance that the TRS contract contract has. If no bonus balance has been
     * set yet or contract does not exist, this method returns zero.
     *
     * @param contract The TRS contract to query.
     * @return the bonus balance of the TRS contract contract.
     */
    public BigInteger getBonusBalance(Address contract) {
        IDataWord bonusSpec = track.getStorageValue(contract, BONUS_SPECS_KEY);
        if (bonusSpec == null) {
            return BigInteger.ZERO;
        }
        int numRows =
                ByteBuffer.wrap(
                                Arrays.copyOfRange(
                                        bonusSpec.getData(),
                                        SINGLE_WORD_SIZE - Integer.BYTES,
                                        SINGLE_WORD_SIZE))
                        .getInt();
        if (numRows == 0) {
            return BigInteger.ZERO;
        }

        byte[] balance = new byte[(numRows * DOUBLE_WORD_SIZE) + 1];
        for (int i = 0; i < numRows; i++) {
            byte[] bonusVal =
                    track.getStorageValue(contract, toIDataWord(makeBonusKey(i))).getData();
            System.arraycopy(bonusVal, 0, balance, (i * DOUBLE_WORD_SIZE) + 1, DOUBLE_WORD_SIZE);
        }
        return new BigInteger(balance);
    }

    /**
     * Initializes the withdrawal specifications of account for the TRS contract contract. The
     * initial specifications are set so that the number of rows that the amount of extra funds
     * withdrawn is represented by zero rows (that is, this amount is zero).
     *
     * <p>This method must only be called in exactly one place: in setDepositBalance, when a new
     * account is depositing for the first time (perhaps after being removed prior).
     *
     * @param contract The TRS contract to update.
     * @param account The account to update.
     */
    private void initExtraWithdrawalSpecs(Address contract, Address account) {
        byte[] specs = new byte[SINGLE_WORD_SIZE];
        specs[0] = 0x0;
        track.addStorageRow(contract, toIDataWord(makeExtraSpecsKey(account)), toIDataWord(specs));
    }

    /**
     * Returns the total amount of extra funds account has already withdrawn from the contract so
     * far.
     *
     * <p>If contract is invalid or account has no deposit balance in contract then zero is
     * returned.
     *
     * @param contract The TRS contract to query.
     * @param account The account to query.
     * @return the extra funds account has already withdrawn from contract.
     */
    public BigInteger getExtraWithdrawalBalance(Address contract, Address account) {
        IDataWord extraSpecs =
                track.getStorageValue(contract, toIDataWord(makeExtraSpecsKey(account)));
        if (extraSpecs == null) {
            return BigInteger.ZERO;
        }
        int numRows = extraSpecs.getData()[0];
        if (numRows == 0) {
            return BigInteger.ZERO;
        }

        byte[] extraFunds = new byte[(numRows * DOUBLE_WORD_SIZE) + 1];
        for (int i = 0; i < numRows; i++) {
            byte[] extraKey = makeExtraWithdrawnKey(account, i);
            byte[] extraVal = track.getStorageValue(contract, toIDataWord(extraKey)).getData();
            System.arraycopy(extraVal, 0, extraFunds, (i * DOUBLE_WORD_SIZE) + 1, DOUBLE_WORD_SIZE);
        }
        return new BigInteger(extraFunds);
    }

    /**
     * Sets the amount of extra funds that account has already withdrawn from contract to amount.
     *
     * @param contract The TRS contract to update.
     * @param account The TRS account to update.
     */
    private void setExtraWithdrawalBalance(Address contract, Address account, BigInteger amount) {
        if (amount.compareTo(BigInteger.ONE) < 0) {
            return;
        }
        byte[] bal = toDoubleWordAlignedArray(amount);
        int numRows = bal.length / DOUBLE_WORD_SIZE;
        if (numRows > MAX_DEPOSIT_ROWS) {
            return;
        }
        for (int i = 0; i < numRows; i++) {
            byte[] extraKey = makeExtraWithdrawnKey(account, i);
            byte[] extraVal = new byte[DOUBLE_WORD_SIZE];
            System.arraycopy(bal, i * DOUBLE_WORD_SIZE, extraVal, 0, DOUBLE_WORD_SIZE);
            track.addStorageRow(contract, toIDataWord(extraKey), toIDataWord(extraVal));
        }

        // Update extra funds withdrawn specs.
        byte[] extraSpec = new byte[SINGLE_WORD_SIZE];
        extraSpec[0] = (byte) (numRows & 0x0F);
        track.addStorageRow(
                contract, toIDataWord(makeExtraSpecsKey(account)), toIDataWord(extraSpec));
    }

    /**
     * Returns the total extra funds available to the TRS contract whose address is contract.
     *
     * @param contract The TRS contract to query.
     * @return the amount of extra funds contract has.
     */
    public BigInteger getExtraFunds(Address contract) {
        IDataWord extraSpecs = track.getStorageValue(contract, EXTRA_SPECS_KEY);
        if (extraSpecs == null) {
            return BigInteger.ZERO;
        }
        int numRows =
                ByteBuffer.wrap(
                                Arrays.copyOfRange(
                                        extraSpecs.getData(),
                                        SINGLE_WORD_SIZE - Integer.BYTES,
                                        SINGLE_WORD_SIZE))
                        .getInt();
        if (numRows == 0) {
            return BigInteger.ZERO;
        }

        byte[] extraFunds = new byte[(numRows * DOUBLE_WORD_SIZE) + 1];
        for (int i = 0; i < numRows; i++) {
            byte[] extraKey = makeExtraKey(i);
            byte[] extraVal = track.getStorageValue(contract, toIDataWord(extraKey)).getData();
            System.arraycopy(extraVal, 0, extraFunds, (i * DOUBLE_WORD_SIZE) + 1, DOUBLE_WORD_SIZE);
        }
        return new BigInteger(extraFunds);
    }

    /**
     * Sets the total extra funds available in the TRS contract whose address is contract to amount.
     * If amount is negative or if it requires more than MAX_DEPOSIT_ROWS rows to be represented
     * then this method does nothing.
     *
     * <p>This method should only ever be called by the updateTotal operation and nowhere else.
     *
     * @param contract The TRS contract to update.
     * @param amount The amount of extra funds contract will now have.
     */
    void setExtraFunds(Address contract, BigInteger amount) {
        if (amount.compareTo(BigInteger.ONE) < 0) {
            return;
        }
        byte[] bal = toDoubleWordAlignedArray(amount);
        int numRows = bal.length / DOUBLE_WORD_SIZE;
        if (numRows > MAX_DEPOSIT_ROWS) {
            return;
        }
        for (int i = 0; i < numRows; i++) {
            byte[] extraKey = makeExtraKey(i);
            byte[] extraVal = new byte[DOUBLE_WORD_SIZE];
            System.arraycopy(bal, i * DOUBLE_WORD_SIZE, extraVal, 0, DOUBLE_WORD_SIZE);
            track.addStorageRow(contract, toIDataWord(extraKey), toIDataWord(extraVal));
        }

        // Update extra funds specs.
        byte[] extraSpec = new byte[SINGLE_WORD_SIZE];
        for (int i = 0; i < Integer.BYTES; i++) {
            extraSpec[SINGLE_WORD_SIZE - i - 1] = (byte) ((numRows >> (i * Byte.SIZE)) & 0xFF);
        }
        track.addStorageRow(contract, EXTRA_SPECS_KEY, toIDataWord(extraSpec));
    }

    /**
     * Initializes the open funds data so that the is-open-funds bit is not set. If an open funds
     * entry in the database already exists then this method does nothing.
     */
    void initOpenFunds(Address contract) {
        if (track.getStorageValue(contract, OPEN_KEY) != null) {
            return;
        }

        byte[] fundsVal = new byte[SINGLE_WORD_SIZE];
        fundsVal[0] = 0x0;
        track.addStorageRow(contract, OPEN_KEY, toIDataWord(fundsVal));
    }

    /**
     * Sets the is-open-funds bit to true for the specified contract. Setting this bit true has the
     * following effect: the contract can never again be locked or live (both of these states are
     * undefined from this point onwards), rendering the contract effectively dead, and any
     * depositor with a positive deposit balance in the contract is able to withdraw that full
     * balance with their next usage of the withdraw (or even bulkWithdraw) operation(s).
     *
     * <p>Once this bit is set it cannot be unset. Care must be taken. This method should be called
     * from only one place: in the openFunds operation logic. Nowhere else.
     *
     * <p>If contract does not exist this method throws an exception for debugging. This should
     * never happen.
     */
    void setIsOpenFunds(Address contract) {
        IDataWord value = track.getStorageValue(contract, OPEN_KEY);
        if (value == null) {
            throw new IllegalStateException(
                    "contract does not exist: " + ByteUtil.toHexString(contract.toBytes()));
        }
        byte[] valueBytes = value.getData();
        valueBytes[0] = 0x1;
        track.addStorageRow(contract, OPEN_KEY, toIDataWord(valueBytes));
    }

    /**
     * Returns true if and only if the is-open-funds bit is set. Returns false otherwise.
     *
     * @return true only if the contract's funds are open and the contract has been killed.
     */
    public boolean isOpenFunds(Address contract) {
        IDataWord value = track.getStorageValue(contract, OPEN_KEY);
        return ((value != null) && (value.getData()[0] == 0x1));
    }

    /**
     * Returns the period that the TRS contract given by the address contract is in at the time
     * specified by currTime.
     *
     * <p>All contracts have some fixed number of periods P and this method returns a value in the
     * range [0, P] only.
     *
     * <p>If currTime is less than the contract's timestamp then zero is returned, otherwise a
     * positive number is returned.
     *
     * <p>Once this method returns P for some currTime value it will return P for all values greater
     * or equal to that currTime.
     *
     * <p>Assumption: contract is the address of a valid TRS contract that has a timestamp.
     *
     * @param contract The TRS contract to query.
     * @param specs The result of the getContractSpecs method.
     * @param currTime The time at which the contract is being queried for.
     * @return the period the contract is in at currTime.
     */
    public int calculatePeriod(Address contract, byte[] specs, long currTime) {
        long timestamp = getTimestamp(contract);
        if (timestamp > currTime) {
            return 0;
        }

        long periods = getPeriods(specs);
        long diff = currTime - timestamp;
        long scale = (isTestContract(specs)) ? TEST_DURATION : PERIOD_DURATION;

        long result = (diff / scale) + 1;
        return (int) ((result > periods) ? periods : result);
    }

    /**
     * Returns true only if contract is locked.
     *
     * @param contract The TRS contract to query.
     * @return true if contract is locked.
     */
    public boolean isContractLocked(Address contract) {
        if (isOpenFunds(contract)) {
            return false;
        }
        IDataWord specs = track.getStorageValue(contract, SPECS_KEY);
        return ((specs != null) && (specs.getData()[LOCK_OFFSET] == (byte) 0x1));
    }

    /**
     * Returns true only if contract is live.
     *
     * @param contract The TRS contract to query.
     * @return true if contract is live.
     */
    public boolean isContractLive(Address contract) {
        if (isOpenFunds(contract)) {
            return false;
        }
        IDataWord specs = track.getStorageValue(contract, SPECS_KEY);
        return ((specs != null) && (specs.getData()[LIVE_OFFSET] == (byte) 0x1));
    }

    /**
     * Returns true only if contract has direct depositing enabled.
     *
     * @param contract The TRS contract to query.
     * @return true only if direct deposits are enabled.
     */
    public boolean isDirDepositsEnabled(Address contract) {
        if (isOpenFunds(contract)) {
            return false;
        }
        IDataWord specs = track.getStorageValue(contract, SPECS_KEY);
        return ((specs != null) && (specs.getData()[DIR_DEPO_OFFSET] == (byte) 0x1));
    }

    /**
     * Returns true only if the is-test bit in specs is set.
     *
     * <p>Assumption: specs is a byte array returned from the getContractSpecs method.
     *
     * @param specs The specifications of some TRS contract.
     * @return true if the specs indicate the contract is for testing.
     */
    public static boolean isTestContract(byte[] specs) {
        return ((specs != null) && (specs[TEST_OFFSET] == (byte) 0x1));
    }

    /**
     * Returns a key for the database to query the total bonus balance entry at row number row of
     * some TRS contract.
     *
     * @param row The bonus balance row to make the key for.
     * @return the key to access the specified bonus balance row for some contract.
     */
    private byte[] makeBonusKey(int row) {
        byte[] bonusKey = new byte[SINGLE_WORD_SIZE];
        bonusKey[0] = BONUS_PREFIX;
        for (int i = 0; i < Integer.BYTES; i++) {
            bonusKey[SINGLE_WORD_SIZE - i - 1] = (byte) ((row >>> (i * Byte.SIZE)) & 0xFF);
        }
        return bonusKey;
    }

    /**
     * Returns a key for the database to query the total extra funds for the contract at a row
     * number of some TRS contract.
     *
     * @param row The extra funds row to make the key for.
     * @return the key to access the specified extra funds row for some contract.
     */
    private byte[] makeExtraKey(int row) {
        byte[] extraKey = new byte[SINGLE_WORD_SIZE];
        extraKey[0] = EXTRA_FUNDS_PREFIX;
        for (int i = 0; i < Integer.BYTES; i++) {
            extraKey[SINGLE_WORD_SIZE - i - 1] = (byte) ((row >> (i * Byte.SIZE)) & 0xFF);
        }
        return extraKey;
    }

    /**
     * Returns a key for the database to query the extra withdrawal specifications entry for
     * account.
     *
     * @param account The account whose withdrawal specs this key is for.
     * @return the key to access the specified withdrawal specifications entry.
     */
    private byte[] makeExtraSpecsKey(Address account) {
        byte[] extraSpecKey = new byte[DOUBLE_WORD_SIZE];
        extraSpecKey[0] = EXTRA_WITH_SPEC_PREFIX;
        System.arraycopy(account.toBytes(), 1, extraSpecKey, 1, Address.ADDRESS_LEN - 1);
        return extraSpecKey;
    }

    /**
     * Returns a key for the database to query the extra funds withdrawn entry for a specific
     * account at the specified row.
     *
     * @param account The account whose extras-withdrawn entry this key is for.
     * @param row The row of the extras-withdrawn amount this key is for.
     * @return the key to access the specified extras-withdrawn entry.
     */
    private byte[] makeExtraWithdrawnKey(Address account, int row) {
        byte[] extraWithKey = new byte[DOUBLE_WORD_SIZE];
        extraWithKey[0] = EXTRA_WITH_PREFIX;
        extraWithKey[0] |= (row & 0x0F);
        System.arraycopy(account.toBytes(), 1, extraWithKey, 1, Address.ADDRESS_LEN - 1);
        return extraWithKey;
    }

    /**
     * Returns a key for the database to query the total balance entry at row number row of some TRS
     * contract.
     *
     * @param row The total balance row to make the key for.
     * @return the key to access the specified total balance row for some contract.
     */
    private byte[] makeTotalBalanceKey(int row) {
        byte[] fundsKey = new byte[SINGLE_WORD_SIZE];
        fundsKey[0] = FUNDS_PREFIX;
        for (int i = 0; i < Integer.BYTES; i++) {
            fundsKey[SINGLE_WORD_SIZE - i - 1] = (byte) ((row >> (i * Byte.SIZE)) & 0xFF);
        }
        return fundsKey;
    }

    /**
     * Returns a key for the database to query the balance entry at row number row for the account
     * account. All valid row numbers are in the range [0, 15] and we assume row is valid here.
     *
     * @param account The account to look up.
     * @param row The balance row to query.
     * @return the key to access the specified balance row for the account in contract.
     */
    private byte[] makeBalanceKey(Address account, int row) {
        if (account == null) {
            return DataWord.ZERO.getData();
        }
        byte[] balKey = new byte[DOUBLE_WORD_SIZE];
        balKey[0] = (byte) (BALANCE_PREFIX | row);
        System.arraycopy(account.toBytes(), 1, balKey, 1, DOUBLE_WORD_SIZE - 1);
        return balKey;
    }

    /**
     * Returns a key for the database to query account's withdrawal stats, which include whether or
     * not the account is eligible for the special one-off withdrawal and the latest period at which
     * the account has withdrawn funds.
     *
     * @param account The account to look up.
     * @return the key to access the account's withdrawal stats.
     */
    private byte[] makeWithdrawalKey(Address account) {
        if (account == null) {
            return DataWord.ZERO.getData();
        }
        return makeWithdrawalKey(account.toBytes());
    }

    /**
     * Returns a key for the database to query account's withdrawal stats, which include whether or
     * not the account is eligible for the special one-off withdrawal and the latest period at which
     * the account has withdrawn funds.
     *
     * @param account The account to look up.
     * @return the key to access the account's withdrawal stats.
     */
    private byte[] makeWithdrawalKey(byte[] account) {
        if (account == null) {
            return DataWord.ZERO.getData();
        }
        byte[] withKey = new byte[DOUBLE_WORD_SIZE];
        withKey[0] = WITHDRAW_PREFIX;
        System.arraycopy(account, 1, withKey, 1, DOUBLE_WORD_SIZE - 1);
        return withKey;
    }

    /**
     * Returns a byte array representing balance such that the returned array is 32-byte word
     * aligned.
     *
     * <p>None of the 32-byte consecutive sections of the array will consist only of zero bytes. At
     * least 1 byte per such section will be non-zero.
     *
     * @param balance The balance to convert.
     * @return the 32-byte word-aligned byte array representation of balance.
     */
    private byte[] toDoubleWordAlignedArray(BigInteger balance) {
        if (balance.equals(BigInteger.ZERO)) {
            return new byte[DOUBLE_WORD_SIZE];
        }
        byte[] temp = balance.toByteArray();
        boolean chopFirstByte = ((temp.length - 1) % DOUBLE_WORD_SIZE == 0) && (temp[0] == 0x0);

        byte[] bal;
        if (chopFirstByte) {
            int numRows = (temp.length - 1) / DOUBLE_WORD_SIZE; // guaranteed a divisor by above.
            bal = new byte[numRows * DOUBLE_WORD_SIZE];
            System.arraycopy(temp, 1, bal, bal.length - temp.length + 1, temp.length - 1);
        } else {
            int numRows = (int) Math.ceil(((double) temp.length) / DOUBLE_WORD_SIZE);
            bal = new byte[numRows * DOUBLE_WORD_SIZE];
            System.arraycopy(temp, 0, bal, bal.length - temp.length, temp.length);
        }
        return bal;
    }

    /**
     * Returns an IDataWord object that wraps word with the correctly sized IDataWord
     * implementation.
     *
     * @param word The word to wrap.
     * @return the word as an IDataWord.
     */
    private static IDataWord toIDataWord(byte[] word) {
        if (word.length == SINGLE_WORD_SIZE) {
            return new DataWord(word);
        } else if (word.length == DOUBLE_WORD_SIZE) {
            return new DoubleDataWord(word);
        } else {
            throw new IllegalArgumentException("Incorrect word size: " + word.length);
        }
    }
}
