package org.aion.precompiled.contracts.TRS;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.type.StatefulPrecompiledContract;

/**
 * The purpose of this abstract class is mostly as a place to store important constants and methods
 * that may be useful to multiple concrete subclasses.
 */
public abstract class AbstractTRS extends StatefulPrecompiledContract {
    // grab AION from CfgAion later and preferrably aion prefix too.
    static final Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    static final byte AION_PREFIX = (byte) 0xA0;
    static final byte TRS_PREFIX = (byte) 0xC0;

    // Codes are unique prefixes for keys in the database so that we can look up keys belonging to
    // a specific category easily.
    //TODO: better to make these entire keys rather than just codes.
    static final byte OWNER_CODE = (byte) 0xF0;
    static final byte SPECS_CODE = (byte) 0xE0;
    static final byte BALANCE_CODE = (byte) 0xB0;
    static final byte TTL_BAL_SPECS_CODE = (byte) 0x91;
    static final byte TTL_BAL_CODE = (byte) 0x90;
    static final byte BONUS_CODE = (byte) 0x80;
    static final byte LINKED_LIST_CODE = (byte) 0x70;
    static final byte LIST_PREV_CODE = (byte) 0x60;

    static final int DIR_DEPO_OFFSET = 10;
    static final int LOCK_OFFSET = 14;
    static final int LIVE_OFFSET = 15;

    final Address caller;

    /**
     * Constructs a new AbstractTRS object.
     *
     * @param track The database cache.
     * @param caller The address of the caller.
     */
    AbstractTRS(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address caller) {

        super(track);
        if (caller == null) {
            throw new NullPointerException("Construct TRS with null caller.");
        }
        this.caller = caller;
    }

    abstract public ContractExecutionResult execute(byte[] input, long nrgLimit);

    /**
     * Returns true only if the is-locked bit is set in the byte array specs -- assumption: specs is
     * the byte array representing a contract's specifications.
     *
     * @param specs The specifications of some TRS contract.
     * @return true if the specs indicate the contract is locked.
     */
    boolean isContractLocked(byte[] specs) {
        return specs[LOCK_OFFSET] == (byte) 0x1;
    }

    /**
     * Returns true only if the is-live bit is set in the byte array specs -- assumption: specs is
     * the byte array representing a contract's specifications.
     *
     * @param specs The specifications of some TRS contract.
     * @return true if the specs indicate the contract is live.
     */
    boolean isContractLive(byte[] specs) {
        return specs[LIVE_OFFSET] == (byte) 0x1;
    }

    /**
     * Returns the contract specifications for the TRS contract whose address is contract if this is
     * a valid contract address.
     *
     * Returns null if contract is not a valid TRS contract address and thus there are no specs to
     * fetch.
     *
     * @param contract The TRS contract address.
     * @return a DataWord wrapper of the contract specifications or null if not a TRS contract.
     */
    IDataWord fetchContractSpecs(Address contract) {
        if (contract.toBytes()[0] != TRS_PREFIX) { return null; }

        byte[] specKey = new byte[DoubleDataWord.BYTES];
        specKey[0] = SPECS_CODE;
        return track.getStorageValue(contract, new DoubleDataWord(specKey));
    }

    /**
     * Returns the owner of the TRS contract whose address is contract if contract is a valid
     * contract address.
     *
     * Returns null if contract is not a valid TRS contract address and thus there is no owner to
     * fetch.
     *
     * @param contract The TRS contract address.
     * @return the owner of the contract or null if not a TRS contract.
     */
    Address fetchContractOwner(Address contract) {
        if (contract.toBytes()[0] != TRS_PREFIX) { return null; }

        byte[] key = new byte[DoubleDataWord.BYTES];
        key[0] = OWNER_CODE;
        IDataWord owner = track.getStorageValue(contract, new DoubleDataWord(key));
        if (owner == null) { return null; }

        return new Address(owner.getData());
    }

    /**
     * Returns true only if the is-direct-deposits-enabled bit is set in specs, where we assume that
     * specs is the specifications byte array corresponding to some TRS contract.
     *
     * @param specs The specifications of some TRS contract.
     * @return true only if direct deposits are enabled.
     */
    boolean fetchIsDirDepositsEnabled(byte[] specs) {
        return specs[DIR_DEPO_OFFSET] == (byte) 0x1;
    }

    /**
     * Adds the data data as the head entry data (for the linked list) corresponding to the TRS
     * contract given by the address contract.
     *
     * This method does not flush.
     *
     * @param contract The TRS contract.
     * @param data The head entry data to add.
     */
    void addHeadData(Address contract, byte[] data) {
        byte[] listKey = new byte[DoubleDataWord.BYTES];
        listKey[0] = LINKED_LIST_CODE;
        track.addStorageRow(contract, new DoubleDataWord(listKey), new DoubleDataWord(data));
    }

    /**
     * Returns the total deposit balance for the TRS contract given by the address contract.
     *
     * @param contract The TRS contract to query.
     * @return the total balance of the contract.
     */
    BigInteger fetchTotalBalance(Address contract) {
        byte[] ttlSpecKey = new byte[DataWord.BYTES];
        ttlSpecKey[0] = TTL_BAL_SPECS_CODE;
        IDataWord ttlSpec = track.getStorageValue(contract, new DataWord(ttlSpecKey));
        int numRows = ByteBuffer.wrap(Arrays.copyOfRange(
            ttlSpec.getData(), DataWord.BYTES - Integer.BYTES ,DataWord.BYTES)).getInt();
        if (numRows == 0) { return BigInteger.ZERO; }

        byte[] balance = new byte[(numRows * DoubleDataWord.BYTES) + 1];
        for (int i = 0; i < numRows; i++) {
            byte[] ttlKey = makeTotalBalanceKey(i);
            byte[] ttlVal = track.getStorageValue(contract, new DataWord(ttlKey)).getData();
            System.arraycopy(ttlVal, 0, balance, (i * DoubleDataWord.BYTES) + 1, DoubleDataWord.BYTES);
        }
        return new BigInteger(balance);
    }

    /**
     * Sets the total balance of the TRS contract whose address is contract.
     *
     * If the contract does not have a total balance entry this method will create the entries
     * sufficient to hold it and update the total balance specifications corresponding to it.
     *
     * This method assumes that balance is non-negative.
     *
     * This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param balance The total balance to set.
     */
    void setTotalBalance(Address contract, BigInteger balance) {
        byte[] bal = toDoubleWordAlignedArray(balance);
        int numRows = bal.length / DoubleDataWord.BYTES;
        for (int i = 0; i < numRows; i++) {
            byte[] ttlKey = makeTotalBalanceKey(i);
            byte[] ttlVal = new byte[DoubleDataWord.BYTES];
            System.arraycopy(bal, i * DoubleDataWord.BYTES, ttlVal, 0, DoubleDataWord.BYTES);
            track.addStorageRow(contract, new DataWord(ttlKey), new DoubleDataWord(ttlVal));
        }

        // Update total balance specs.
        byte[] ttlSpec = new byte[DataWord.BYTES];
        for (int i = 0; i < Integer.BYTES; i++) {
            ttlSpec[DataWord.BYTES - i - 1] = (byte) ((numRows >> (i * Byte.SIZE)) & 0xFF);
        }
        byte[] ttlSpecKey = new byte[DataWord.BYTES];
        ttlSpecKey[0] = TTL_BAL_SPECS_CODE;
        track.addStorageRow(contract, new DataWord(ttlSpecKey), new DataWord(ttlSpec));
    }

    /**
     * Returns a key for the database to query the total balance entry at row number row of some
     * TRS contract.
     *
     * @param row The total balance row to query.
     * @return the key to access the specified total balance row for some contract.
     */
    private byte[] makeTotalBalanceKey(int row) {
        byte[] ttlKey = new byte[DataWord.BYTES];
        ttlKey[0] = TTL_BAL_CODE;
        for (int i = 0; i < Integer.BYTES; i++) {
            ttlKey[DataWord.BYTES - i - 1] = (byte) ((row >> (i * Byte.SIZE)) & 0xFF);
        }
        return ttlKey;
    }

    /**
     * Returns a byte array representing balance such that the returned array is 32-byte word
     * aligned.
     *
     * None of the 32-byte consecutive sections of the array will consist only of zero bytes. At
     * least 1 byte per such section will be non-zero.
     *
     * @param balance The balance to convert.
     * @return the 32-byte word-aligned byte array representation of balance.
     */
    byte[] toDoubleWordAlignedArray(BigInteger balance) {
        if (balance.equals(BigInteger.ZERO)) { return new byte[DoubleDataWord.BYTES]; }
        byte[] temp = balance.toByteArray();
        boolean chopFirstByte = ((temp.length - 1) % DoubleDataWord.BYTES == 0) && (temp[0] == 0x0);

        byte[] bal;
        if (chopFirstByte) {
            int numRows = (temp.length - 1) / DoubleDataWord.BYTES; // guaranteed a divisor by above.
            bal = new byte[numRows * DoubleDataWord.BYTES];
            System.arraycopy(temp, 1, bal, bal.length - temp.length + 1, temp.length - 1);
        } else {
            int numRows = (int) Math.ceil(((double) temp.length) / DoubleDataWord.BYTES);
            bal = new byte[numRows * DoubleDataWord.BYTES];
            System.arraycopy(temp, 0, bal, bal.length - temp.length, temp.length);
        }
        return bal;
    }

}
