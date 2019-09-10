package org.aion.base;

import static org.aion.base.EmptyTrieUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.crypto.HashUtil;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.util.conversions.Hex;

/** Account state. */
public class AccountState {

    /** The RLP encoding of this account state. */
    protected byte[] rlpEncoded = null;

    /** Flag indicating whether the account has been deleted. */
    protected boolean deleted = false;

    /** Flag indicating whether the state of the account has been changed. */
    protected boolean dirty = false;

    /**
     * A value equal to the number of transactions sent from this address, or, in the case of
     * contract accounts, the number of contract-creations made by this account
     */
    private BigInteger nonce;

    /** A scalar value equal to the number of Wei owned by this address */
    private BigInteger balance;

    /**
     * A 256-bit hash of the root node of a trie structure that encodes the storage contents of the
     * contract, itself a simple mapping between byte arrays of size 32. The hash is formally
     * denoted σ[a] s .
     *
     * <p>Since I typically wish to refer not to the trie’s root hash but to the underlying set of
     * key/value pairs stored within, I define a convenient equivalence TRIE (σ[a] s ) ≡ σ[a] s . It
     * shall be understood that σ[a] s is not a ‘physical’ member of the account and does not
     * contribute to its later serialisation
     */
    private byte[] stateRoot = EMPTY_TRIE_HASH;

    /**
     * The hash of the EVM code of this contract—this is the code that gets executed should this
     * address receive a message call; it is immutable and thus, unlike all other fields, cannot be
     * changed after construction. All such code fragments are contained in the state database under
     * their corresponding hashes for later retrieval
     */
    private byte[] codeHash = EMPTY_DATA_HASH;

    /** Constructs a new object with ZERO initial transactions and ZERO Wei balance. */
    public AccountState() {
        this(BigInteger.ZERO, BigInteger.ZERO);
    }

    /**
     * Constructs a new object.
     *
     * @param nonce the number of transactions sent from this address OR the number of
     *     contract-creations made by this account (for contract accounts)
     * @param balance scalar value equal to the number of Wei owned by this address
     */
    public AccountState(BigInteger nonce, BigInteger balance) {
        this.nonce = nonce;
        this.balance = balance;
    }

    /**
     * Copy constructor.
     *
     * @param previous the {@link AccountState} that is replicated
     * @apiNote Preferred to object cloning.
     */
    public AccountState(AccountState previous) {
        this(previous.getNonce(), previous.getBalance());

        this.codeHash = previous.getCodeHash();
        this.stateRoot = previous.getStateRoot();

        // maintains the state of the copied object
        this.dirty = previous.isDirty();
        this.deleted = previous.isDeleted();
    }

    /**
     * Decoding constructor.
     *
     * @param rlpData the RLP representation of the state of an account
     */
    public AccountState(byte[] rlpData) {
        rlpEncoded = rlpData;

        RLPList items = (RLPList) RLP.decode2(rlpEncoded).get(0);

        byte[] nonceValue = items.get(0).getRLPData();
        nonce = nonceValue == null ? BigInteger.ZERO : new BigInteger(1, nonceValue);

        byte[] balanceValue = items.get(1).getRLPData();
        balance = balanceValue == null ? BigInteger.ZERO : new BigInteger(1, balanceValue);

        stateRoot = items.get(2).getRLPData();
        codeHash = items.get(3).getRLPData();
    }

    /**
     * Checks whether the state of the account has been changed during execution.
     *
     * <p>The dirty status is set internally by the object when its stored values have been
     * modified.
     *
     * @return {@code true} if the account state has been modified (including if the account is
     *     newly created), {@code false} if the account state is the same as in the database
     */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Sets the dirty flag to true signaling that the object state has been changed.
     *
     * @apiNote Once the account has been modified (by setting the flag to {@code true}) it is
     *     <b>considered dirty</b> even if its state reverts to the initial values by applying
     *     subsequent changes.
     * @implNote Method called internally by the account object when its state has been modified.
     *     Resets the stored RLP encoding.
     */
    protected void makeDirty() {
        this.rlpEncoded = null;
        this.dirty = true;
    }

    /**
     * Deletes the current account.
     *
     * @apiNote Once the account has been deleted (by setting the flag to {@code true}) <b>it cannot
     *     be recovered</b>.
     */
    public void delete() {
        this.deleted = true;
        makeDirty();
    }

    /**
     * Checks if the state of the account has been deleted.
     *
     * @return {@code true} if the account was deleted, {@code false} otherwise
     */
    public boolean isDeleted() {
        return this.deleted;
    }

    /**
     * Retrieves the number of transactions sent from this address.
     *
     * <p>In the case of contract accounts the number of contract-creations made by this account.
     *
     * @return a positive scalar value representing the number of transactions sent from this
     *     address
     */
    public BigInteger getNonce() {
        return nonce;
    }

    /**
     * Sets the number of transactions sent from this address.
     *
     * @param nonce the new number of transactions for the address
     * @return a positive scalar value representing the updated number of transactions sent from
     *     this address
     * @implNote Will change the status of the object to dirty when called with a nonce different
     *     from the current one. If the method is called with the already existing nonce, the object
     *     state will not be set to dirty.
     */
    public BigInteger setNonce(BigInteger nonce) {
        if (!nonce.equals(this.nonce)) {
            this.nonce = nonce;
            makeDirty();
        }
        return this.nonce;
    }

    /**
     * Retrieves the hash of the root node of a trie structure that encodes the storage contents of
     * the contract account.
     *
     * @return the hash of the trie root node
     */
    public byte[] getStateRoot() {
        return stateRoot;
    }

    /**
     * Updates the hash of the root node of a trie structure that encodes the storage contents of
     * the contract account.
     *
     * @param stateRoot the hash of the trie root node
     * @implNote Will change the status of the object to dirty.
     */
    public void setStateRoot(byte[] stateRoot) {
        this.stateRoot = stateRoot;
        makeDirty();
    }

    /**
     * Increases by one the number of transactions sent from this address.
     *
     * @return a positive scalar value representing the updated number of transactions sent from
     *     this address
     * @implNote Will change the status of the object to dirty.
     */
    public BigInteger incrementNonce() {
        nonce = nonce.add(BigInteger.ONE);
        makeDirty();
        return nonce;
    }

    /**
     * Retrieves the hash of the virtual machine code of this contract.
     *
     * <p>This is the code that gets executed should this address receive a message call.
     *
     * @return the hash of the contract code
     */
    public byte[] getCodeHash() {
        return codeHash;
    }

    /**
     * Sets the hash of the virtual machine code of this contract.
     *
     * <p>All the code fragments are contained in the state database under their corresponding
     * hashes for later retrieval.
     *
     * @apiNote The code hash can be set only once either through the constructor or a call to this
     *     method. After initialization the code hash is immutable.
     * @implNote Attempting to calling this method on an object where the code hash has already been
     *     initialized is ineffectual since only the initialization call will modify the {@code
     *     codeHash} value.
     */
    public void setCodeHash(byte[] codeHash) {
        if (!isInitialized()) {
            this.codeHash = codeHash;
            makeDirty();
        }
    }

    /**
     * Flag indicating whether the contract code has been initialized.
     *
     * <p>
     *
     * @apiNote Used to ensure that initialized contracts cannot modify the code hash.
     */
    private boolean isInitialized() {
        // TODO: discuss alternative of storing a boolean value
        return !Arrays.equals(codeHash, EMPTY_DATA_HASH);
    }

    /**
     * Retrieves the number of Wei owned by this address.
     *
     * @return a positive scalar value representing the number of Wei owned
     */
    public BigInteger getBalance() {
        return balance;
    }

    /**
     * Sets the number of Wei owned by this address.
     *
     * @param balance a positive scalar value representing the new number of Wei owned
     * @implNote Will change the status of the object to dirty when called with a balance different
     *     from the current one. If the method is called with the already existing balance, the
     *     object state will not be set to dirty.
     */
    public void setBalance(BigInteger balance) {
        if (!balance.equals(this.balance)) {
            this.balance = balance;
            makeDirty();
        }
    }

    /**
     * Adds the given value to the number of Wei owned by this address.
     *
     * @param value the value to be added to the current balance
     * @return the result of the addition of the account balance and given parameter
     * @implNote Will change the status of the object to dirty when called with a non-zero
     *     parameter. If the value to be added is 0, the object state will not be set to dirty.
     */
    public BigInteger addToBalance(BigInteger value) {
        // TODO: ensure we verify that we don't end up subtracting more than the
        // total balance
        if (value.signum() != 0) {
            makeDirty();
            this.balance = balance.add(value);
        }
        return this.balance;
    }

    /**
     * Subtracts the given value from the number of Wei owned by this address.
     *
     * @param value the value to be subtracted from the current balance
     * @return the result of the subtraction of given parameter from the account balance
     * @implNote Will change the status of the object to dirty when called with a non-zero
     *     parameter. If the value to be subtracted is 0, the object state will not be set to dirty.
     */
    public BigInteger subFromBalance(BigInteger value) {
        // TODO: ensure we verify that we don't end up subtracting more than the
        // total balance
        if (value.signum() != 0) {
            makeDirty();
            this.balance = balance.subtract(value);
        }
        return this.balance;
    }

    /**
     * Returns a deep copy of this account state.
     *
     * @return A deep copy of this object.
     */
    public AccountState copy() {
        AccountState accountStateCopy = new AccountState();
        accountStateCopy.balance = this.balance;
        accountStateCopy.nonce = this.nonce;
        accountStateCopy.dirty = this.dirty;
        accountStateCopy.deleted = this.deleted;
        accountStateCopy.codeHash =
                (this.codeHash == null) ? null : Arrays.copyOf(this.codeHash, this.codeHash.length);
        accountStateCopy.stateRoot =
                (this.stateRoot == null)
                        ? null
                        : Arrays.copyOf(this.stateRoot, this.stateRoot.length);
        accountStateCopy.rlpEncoded =
                (this.rlpEncoded == null)
                        ? null
                        : Arrays.copyOf(this.rlpEncoded, this.rlpEncoded.length);
        return accountStateCopy;
    }

    /**
     * Retrieves the RLP encoding of this object.
     *
     * @return a {@code byte} array representing the RLP encoding of the account state.
     * @implNote For performance reasons, this encoding is stored when available and recomputed only
     *     if the object has been modified during execution.
     */
    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] nonce = RLP.encodeBigInteger(this.nonce);
            byte[] balance = RLP.encodeBigInteger(this.balance);
            byte[] stateRoot = RLP.encodeElement(this.stateRoot);
            byte[] codeHash = RLP.encodeElement(this.codeHash);
            this.rlpEncoded = RLP.encodeList(nonce, balance, stateRoot, codeHash);
        }
        return rlpEncoded;
    }

    /**
     * Checks if the account state stores any meaningful information.
     *
     * @return {@code true} if the account state is empty, {@code false} otherwise
     * @apiNote Empty accounts should not be stored in the database.
     * @implNote Empty accounts do not have associated code (or by extension storage), i.e. cannot
     *     be contract accounts.
     */
    public boolean isEmpty() {
        return Arrays.equals(codeHash, EMPTY_DATA_HASH)
                && BigInteger.ZERO.equals(balance)
                && BigInteger.ZERO.equals(nonce);
    }

    public String toString() {
        return "  Nonce: "
                + this.getNonce().toString()
                + "\n"
                + "  Balance: "
                + getBalance()
                + "\n"
                + "  State Root: "
                + Hex.toHexString(this.getStateRoot())
                + "\n"
                + "  Code Hash: "
                + Hex.toHexString(this.getCodeHash());
    }
}
