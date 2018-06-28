package org.aion.precompiled.contracts.TRS;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.type.StatefulPrecompiledContract;

/**
 * The purpose of this abstract class is mostly as a place to store important constants and methods
 * that may be useful to multiple concrete subclasses.
 */
public abstract class AbstractTRS extends StatefulPrecompiledContract {
    // grab AION from CfgAion later
    static final Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    static final byte TRS_PREFIX = (byte) 0xC0;

    // Codes are unique prefixes for keys in the database so that we can look up keys belonging to
    // a specific category easily.
    static final byte OWNER_CODE = (byte) 0xF0;
    static final byte SPECS_CODE = (byte) 0xE0;
    static final byte FUNDS_CODE = (byte) 0xD0;
    static final byte BALANCE_CODE = (byte) 0xB0;
    static final byte PREV_DEPOSITOR_CODE = (byte) 0xAF;
    static final byte TTL_BAL_CODE = (byte) 0x90;
    static final byte BONUS_CODE = (byte) 0x80;
    static final byte DEPOSITS_CODE = (byte) 0x70;

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

}
