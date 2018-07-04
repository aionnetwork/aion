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
    // TODO: grab AION from CfgAion later and preferrably aion prefix too.
    static final Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    static final byte AION_PREFIX = (byte) 0xA0;
    static final byte TRS_PREFIX = (byte) 0xC0;
    static final int DOUBLE_WORD_SIZE = DoubleDataWord.BYTES;
    static final int SINGLE_WORD_SIZE = DataWord.BYTES;
    final Address caller;

    /*
     * The database keys each have unique prefixes denoting the function of that key. Some keys have
     * mutable bytes following this prefix. In these cases oly the prefixes are provided. Otherwise
     * for immutable keys we store them as an IDataWord object directly.
     */
    static final IDataWord OWNER_KEY, SPECS_KEY, LIST_HEAD_KEY, FUNDS_SPECS_KEY, NULL32;
    static final byte BALANCE_PREFIX = (byte) 0xB0;
    static final byte LIST_PREV_PREFIX = (byte) 0x60;
    static final byte FUNDS_PREFIX = (byte) 0x90;

    static {
        byte[] singleKey = new byte[SINGLE_WORD_SIZE];
        singleKey[0] = (byte) 0xF0;
        OWNER_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0xE0;
        SPECS_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x91;
        FUNDS_SPECS_KEY = toIDataWord(singleKey);

        singleKey[0] = (byte) 0x70;
        LIST_HEAD_KEY = toIDataWord(singleKey);

        byte[] value = new byte[DOUBLE_WORD_SIZE];
        value[0] = (byte) 0x80;
        NULL32 = toIDataWord(value);
    }

    // Constructor.
    AbstractTRS(IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address caller) {
        super(track);
        if (caller == null) { throw new NullPointerException("Construct TRS with null caller."); }
        this.caller = caller;
    }

    // The execute method for subclasses to implement.
    abstract public ContractExecutionResult execute(byte[] input, long nrgLimit);


    // <-------------------------------------HELPER METHODS---------------------------------------->

    // Some important indices for the contract specs.
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
     * Returns null if contract is not a valid TRS contract address and thus there are no specs.
     *
     * @param contract The TRS contract address to query.
     * @return a DataWord wrapper of the contract specifications or null if not a TRS contract.
     */
    IDataWord getContractSpecs(Address contract) {
        if (contract.toBytes()[0] != TRS_PREFIX) { return null; }
        return track.getStorageValue(contract, SPECS_KEY);
    }

    /**
     * Sets the contract specifications for the TRS contract whose address is contract to record the
     * parameters listed in this method's signature.
     *
     * If percent requires more than 9 bytes to represent it then it will be truncated down to 9
     * bytes.
     *
     * This method only succeeds if the specifications entry for contract is empty. Thus this method
     * can be called successfully at most once per contract.
     *
     * This method does not flush.
     *
     * @param contract The TRS contract to update.
     * @param isTest true if this is a test contract.
     * @param isDirectDeposit true if users can deposit to the contract directly.
     * @param periods the number of withdrawal periods for this contract.
     * @param percent the percentage of the total balance withdrawable in the special one-off event.
     * @param precision the number of decimal places to left-shift percent.
     */
    void setContractSpecs(Address contract, boolean isTest, boolean isDirectDeposit, int periods,
        BigInteger percent, int precision) {

        if (track.getStorageValue(contract, SPECS_KEY) != null) { return; }
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
    Address getContractOwner(Address contract) {
        IDataWord owner = track.getStorageValue(contract, OWNER_KEY);
        return  (owner == null) ? null : new Address(owner.getData());
    }

    /**
     * Sets the current caller to be the owner of the TRS contract given by contract.
     *
     * This method only succeeds if the owner entry for contract is empty. Thus this method can be
     * called successfully at most once per contract.
     *
     * This method does not flush.
     *
     * @param contract The TRS contract to update.
     */
    void setContractOwner(Address contract) {
        if (track.getStorageValue(contract, OWNER_KEY) != null) { return; }
        track.addStorageRow(contract, OWNER_KEY, toIDataWord(caller.toBytes()));
    }

    /**
     * Returns the byte array representing the head of the linked list for the TRS contract given by
     * contract or null if the head of the list is null.
     *
     * The returned array will be length 32 and the bytes in the index range [1, 31] will be the
     * last 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * This method throws an exception if there is no head entry for contract. This should never
     * happen and is here only for debugging.
     *
     * @param contract The TRS contract to query.
     * @return a byte array if there is a non-null head or null otherwise.
     * @throws NullPointerException if contract has no linked list.
     */
    byte[] getListHead(Address contract) {
        IDataWord head = track.getStorageValue(contract, LIST_HEAD_KEY);
        if (head == null) { throw new NullPointerException("Contract has no list: " + contract); }
        byte[] headData = head.getData();
        return ((headData[0] & 0x80) == 0x80) ? null : head.getData();
    }

    /**
     * Sets the head of the linked list to head, where head is assumed to be correctly formatted so
     * that the first byte of head is 0x80 iff the head is null, and so that the following 31 bytes
     * of head are the 31 bytes of a valid Aion account address without the Aion prefix.
     *
     * If head is null this method will set the head of the linked list to null.
     *
     * This method does nothing if head is not 32 bytes.
     *
     * This method does not flush.
     *
     * @param contract The TRS contract.
     * @param head The head entry data to add.
     */
    void setListHead(Address contract, byte[] head) {
        if (head == null) {
            track.addStorageRow(contract, LIST_HEAD_KEY, NULL32);
        } else if (head.length == DOUBLE_WORD_SIZE) {
            track.addStorageRow(contract, LIST_HEAD_KEY, toIDataWord(head));
        }
    }

    /**
     * Returns the total deposit balance for the TRS contract given by the address contract.
     *
     * @param contract The TRS contract to query.
     * @return the total balance of the contract.
     */
    BigInteger fetchTotalBalance(Address contract) {
        IDataWord ttlSpec = track.getStorageValue(contract, FUNDS_SPECS_KEY);
        int numRows = ByteBuffer.wrap(Arrays.copyOfRange(
            ttlSpec.getData(), SINGLE_WORD_SIZE - Integer.BYTES, SINGLE_WORD_SIZE)).getInt();
        if (numRows == 0) { return BigInteger.ZERO; }

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
     * Returns a key for the database to query the total balance entry at row number row of some
     * TRS contract.
     *
     * @param row The total balance row to query.
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
     * Saves a new public-facing TRS contract whose contract address is contract and whose direct
     * deposit option is isDirectDeposit. The new contract will have periods number of withdrawable
     * periods and the percentage that is derived from percent and precision as outlined in create()
     * is the percentage of total funds withdrawable in the one-off special event. The isTest
     * parameter tells this method whether to save the new contract as a test contract (using 30
     * second periods) or not (using 30 day periods). The owner of the contract is caller.
     *
     * All preconditions on the parameters are assumed already verified.
     *
     * @param contract The address of the new TRS contract.
     * @param isTest True only if this new TRS contract is a test contract.
     * @param isDirectDeposit True only if direct depositing is enabled for this TRS contract.
     * @param periods The number of withdrawable periods this TRS contract is live for.
     * @param percent The percent of total funds withdrawable in the one-off event.
     * @param precision The number of decimal places to put the decimal point in percent.
     */
    void saveNewContract(Address contract, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        //TODO if contract already exists, stop this
        track.createAccount(contract);
        setContractOwner(contract);

        // Save the "specifications" data for this contract.
        setContractSpecs(contract, isTest, isDirectDeposit, periods, percent, precision);

        // Save the data for the head of the linked list; head null bit is set.
        setListHead(contract, null);

        // Save the total balance for this contract, currently as zero.
        setTotalBalance(contract, BigInteger.ZERO);
        track.flush();
    }

    /**
     * Updates the specifications associated with the TRS contract whose address is contract so that
     * the is-locked bit will be set. If the bit is already set this method effectively does nothing.
     *
     * Assumption: contract IS a valid TRS contract address.
     *
     * @param contract The address of the TRS contract.
     */
    void setLock(Address contract) {
        byte[] specValue = getContractSpecs(contract).getData();
        specValue[LOCK_OFFSET] = (byte) 0x1;
        track.addStorageRow(contract, SPECS_KEY, toIDataWord(specValue));
        track.flush();
    }

    /**
     * Updates the specifications associated with the TRS contract whose address is contract so that
     * the is-live bit will be set. If the bit is already set this method effectively does nothing.
     *
     * Assumption: contract IS a valid TRS contract address.
     *
     * @param contract The address of the TRS contract.
     */
    void setLive(Address contract) {
        byte[] specValue = getContractSpecs(contract).getData();
        specValue[LIVE_OFFSET] = (byte) 0x1;

        track.addStorageRow(contract, SPECS_KEY, toIDataWord(specValue));
        track.flush();
    }

    /**
     * Returns true only if the is-locked bit is set in the byte array specs -- assumption: specs is
     * the byte array representing a contract's specifications.
     *
     * @param specs The specifications of some TRS contract.
     * @return true if the specs indicate the contract is locked.
     */
    public static boolean isContractLocked(byte[] specs) {
        return specs[LOCK_OFFSET] == (byte) 0x1;
    }

    /**
     * Returns true only if the is-live bit is set in the byte array specs -- assumption: specs is
     * the byte array representing a contract's specifications.
     *
     * @param specs The specifications of some TRS contract.
     * @return true if the specs indicate the contract is live.
     */
    public static boolean isContractLive(byte[] specs) {
        return specs[LIVE_OFFSET] == (byte) 0x1;
    }

    /**
     * Returns true only if the is-direct-deposits-enabled bit is set in specs, where we assume that
     * specs is the specifications byte array corresponding to some TRS contract.
     *
     * @param specs The specifications of some TRS contract.
     * @return true only if direct deposits are enabled.
     */
    public static boolean fetchIsDirDepositsEnabled(byte[] specs) {
        return specs[DIR_DEPO_OFFSET] == (byte) 0x1;
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
        if (balance.equals(BigInteger.ZERO)) { return new byte[DOUBLE_WORD_SIZE]; }
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
     * Returns an IDataWord object that wraps word with the correctly sized IDataWord implementation.
     *
     * @param word The word to wrap.
     * @return the word as an IDataWord.
     */
    static IDataWord toIDataWord(byte[] word) {
        if (word.length == SINGLE_WORD_SIZE) {
            return new DataWord(word);
        } else if (word.length == DOUBLE_WORD_SIZE) {
            return new DoubleDataWord(word);
        } else {
            throw new IllegalArgumentException("Incorrect word size: " + word.length);
        }
    }

}
