package org.aion.mcf.types;

import java.math.BigInteger;
import org.aion.types.AionAddress;

/**
 * An interface for a {@link VirtualMachine} into the kernel.
 *
 * <p>This interface has some caveats both concerning how it must be implemented and how it must be
 * used by a {@link VirtualMachine}.
 *
 * <p>In terms of implementation, the following must be true: - After a kernel interface calls
 * {@code commitTo()}, it must still retain ALL of its state changes.
 *
 * <p>It is up to the discretion of the implementing class whether or not {@code commit()} causes
 * the kernel interface that performs the action to lose its state changes or not.
 *
 * <p>In terms of how a {@link VirtualMachine} must use this class: - All verification checks on a
 * transaction must be performed via this class. In particular, the following four methods must be
 * used:
 *
 * <p>{@code accountNonceEquals()}: to check the sender's nonce. {@code accountBalanceIsAtLeast()}:
 * to check for sufficient funds. {@code isValidEnergyLimitForCreate()}: to check the transaction
 * energy limit for CREATE. {@code isValidEnergyLimitForNonCreate()}: to check the transaction
 * energy limit otherwise.
 *
 * <p>- All internal calls must first call into the following method to determine whether or not the
 * call can be made into the specified destination address:
 *
 * <p>{@code destinationAddressIsSafeForThisVM()}.
 *
 * <p>- Prior to running the transaction logic, the sender account must be deducted the full cost of
 * the energy fees. This deduction MUST be done only by calling into this method:
 *
 * <p>{@code deductEnergyCost()}.
 *
 * <p>- After running the transaction logic, depending on the success of the logic, the sender
 * account will be refunded for the energy it does not use. This refunding MUST be done only by
 * calling into this method:
 *
 * <p>{@code refundAccount()}.
 *
 * <p>- After running the transaction logic, depending on whether the transaction is rejected or
 * not, the miner must be paid its fees. These fees MUST be paid only by calling into this method:
 *
 * <p>{@code payMiningFee()}.
 *
 * <p>The reason that the virtual machine must use the above methods as specified is because the
 * kernel may use some special logic in each of these cases depending on the context of the
 * transaction, and this logic is a kernel-level concern not a VM concern. These explicit calls for
 * these cases allow the kernel to gain control over each of these precisely defined events, which
 * it may need.
 */
public interface KernelInterface {

    /** Flushes all state changes captured by this {@link KernelInterface} to its parent. */
    void commit();

    /**
     * Flushes all state changes captured by this {@link KernelInterface} to the specified target
     * interface.
     *
     * @param target The kernel interface to receive the flushed state changes.
     */
    void commitTo(KernelInterface target);

    /**
     * Spawns a new {@link KernelInterface} that is a direct child of this kernel interface.
     *
     * <p>A child kernel interface will query its parent if it does not have the query result
     * already in its cache.
     *
     * <p>A child kernel interface will flush all of its state changes into its parent upon using
     * the {@code commit()} method.
     *
     * @return A child kernel interface of this interface.
     */
    KernelInterface makeChildKernelInterface();

    /**
     * Creates an account with the specified address.
     *
     * @param address The account to create.
     */
    void createAccount(AionAddress address);

    /**
     * Returns true if, and only if, the specified address has account state. That is, it has a
     * positive nonce or balance or contains contract code.
     *
     * @param address The address whose existence is to be decided.
     * @return True if the account exists.
     */
    boolean hasAccountState(AionAddress address);

    /**
     * Retrieves the code of an account.
     *
     * @param address the account address
     * @return the code of the account, or NULL if not exists.
     */
    byte[] getCode(AionAddress address);

    /**
     * Sets the code of an account.
     *
     * @param address the account address
     * @param code the deploy code
     */
    void putCode(AionAddress address, byte[] code);

    /**
     * Get an transformed code of the VM contract.
     *
     * @param address the account address
     * @return the transformed code.
     */
    byte[] getTransformedCode(AionAddress address);

    /**
     * Set the transformed code of the VM contract
     *
     * @param code the transformed code.
     */
    void setTransformedCode(AionAddress address, byte[] code);

    /**
     * Saves the object graph for the given contract into contract storage.
     *
     * @param contract the account address
     * @param graph a byte array representing an encoding of the object graph for the given contract
     */
    void putObjectGraph(AionAddress contract, byte[] graph);

    /**
     * Returns a byte array from contract storage representing an encoding of the object graph for
     * the given contract.
     *
     * @param contract the account address
     * @return a byte array from contract storage representing an encoding of the object graph for
     *     the given contract
     */
    byte[] getObjectGraph(AionAddress contract);

    /**
     * Put a key-value pair into the account's storage.
     *
     * @param address the account address
     * @param key the storage key
     * @param value the storage value
     */
    void putStorage(AionAddress address, byte[] key, byte[] value);

    /**
     * Remove a key from the account's storage.
     *
     * @param address the account address
     * @param key the storage key
     */
    void removeStorage(AionAddress address, byte[] key);

    /**
     * Get the value that is mapped to the key, for the given account.
     *
     * @param address the account address
     * @param key the storage key
     */
    byte[] getStorage(AionAddress address, byte[] key);

    /**
     * Deletes an account. This is used to implement the self-destruct functionality.
     *
     * @param address the account address
     */
    void deleteAccount(AionAddress address);

    /**
     * Returns the balance of an account.
     *
     * @param address the account address
     * @return The balance of the specified address.
     */
    BigInteger getBalance(AionAddress address);

    /**
     * Adds/removes the balance of an account.
     *
     * @param address the account address
     * @param delta the change
     */
    void adjustBalance(AionAddress address, BigInteger delta);

    /**
     * Returns the nonce of an account.
     *
     * @param address the account address
     * @return the nonce
     */
    BigInteger getNonce(AionAddress address);

    /**
     * Increases the nonce of an account by 1.
     *
     * @param address the account address
     */
    void incrementNonce(AionAddress address);

    // TODO: can deduct & refund remove 'address' param and always use 'sender' (what about
    // delegatecall?)

    /**
     * Deducts {@code energyCost} amount of Aion from the specified address.
     *
     * @param address The address that will pay for the specified energy cost.
     * @param energyCost The energy cost.
     */
    void deductEnergyCost(AionAddress address, BigInteger energyCost);

    /**
     * Refunds {@code amount} amount of Aion to the specified address.
     *
     * @param address The address that will receive the refund.
     * @param amount The amount to refund.
     */
    void refundAccount(AionAddress address, BigInteger amount);

    // TODO: should be able to remove address param from payMiningFee and grab the coinbase behind
    // the scenes.

    /**
     * Pays {@code fee} amount of Aion to the specified miner's address.
     *
     * @param miner The address of the miner.
     * @param fee The mining fee.
     */
    void payMiningFee(AionAddress miner, BigInteger fee);

    /**
     * Returns the hash of the block whose block number is {@code blockNumber}.
     *
     * @param blockNumber The block number to query.
     * @return The hash of the indicated block.
     */
    byte[] getBlockHashByNumber(long blockNumber);

    /**
     * Returns {@code true} if, and only if, the specified address has a nonce equal to the provided
     * nonce.
     *
     * @param address The address whose nonce is to be compared.
     * @param nonce The nonce to compare against the account's nonce.
     * @return True if the nonce of the address equals the given nonce.
     */
    boolean accountNonceEquals(AionAddress address, BigInteger nonce);

    /**
     * Returns {@code true} if, and only if, the specified address has funds that are greater than
     * or equal to the provided amount.
     *
     * @param address The address whose balance is to be compared.
     * @param amount The amount to compare against the account's balance.
     * @return True if the balance of the account is {@code >=} amount.
     */
    boolean accountBalanceIsAtLeast(AionAddress address, BigInteger amount);

    /**
     * Returns {@code true} if, and only if, the specified energy limit is a valid quantity for a
     * contract creation transaction.
     *
     * <p>This is a kernel-level concept, and the correct energy rules will be injected into the vm
     * by the kernel-side implementation of this interface.
     *
     * <p>This check should only be performed in exactly ONE place, immediately before an external
     * transaction is actually run.
     *
     * @param energyLimit The energy limit to validate.
     * @return True if the energy limit is a valid quantity.
     */
    boolean isValidEnergyLimitForCreate(long energyLimit);

    /**
     * Returns {@code true} if, and only if, the specified energy limit is a valid quantity for a
     * transaction that is not for contract creation.
     *
     * <p>This is a kernel-level concept, and the correct energy rules will be injected into the vm
     * by the kernel-side implementation of this interface.
     *
     * <p>This check should only be performed in exactly ONE place, immediately before an external
     * transaction is actually run.
     *
     * @param energyLimit The energy limit to validate.
     * @return True if the energy limit is a valid quantity.
     */
    boolean isValidEnergyLimitForNonCreate(long energyLimit);

    /**
     * Returns {@code true} if, and only if, contract calls are able to be made into the specified
     * contract address from whatever {@link VirtualMachine} is currently making this query.
     *
     * <p>Pure balance transfers that do not run any code are always considered safe to do.
     * Therefore if address is not a smart contract this method will always true {@code true}.
     *
     * <p>It is the responsibility of the Kernel to track which {@link VirtualMachine} it is
     * communicating with via this interface so that it can make this judgment correctly.
     *
     * @param address The address of a smart contract.
     * @return True if this address can be invoked from the calling {@link VirtualMachine}.
     */
    boolean destinationAddressIsSafeForThisVM(AionAddress address);

    /**
     * Returns the number of the block that this kernel interface is at.
     *
     * @return The block number.
     */
    long getBlockNumber();

    /**
     * Returns the timestamp of the block that this kernel interface is at.
     *
     * @return The block timestamp.
     */
    long getBlockTimestamp();

    // TODO: block energy limit can probably be removed.

    /**
     * Returns the energy limit of the block that this kernel interface is at.
     *
     * @return The block energy limit.
     */
    long getBlockEnergyLimit();

    /**
     * Returns the difficulty of the block that this kernel interface is at.
     *
     * @return The block difficulty.
     */
    long getBlockDifficulty();

    /**
     * Returns the address of the miner of the block that this kernel interface is at.
     *
     * @return The miner's address.
     */
    AionAddress getMinerAddress();
}
