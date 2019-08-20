package org.aion.vm;

import java.math.BigInteger;
import org.aion.fastvm.ExecutionContext;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.FastVmTransactionResult;
import org.aion.fastvm.FvmDataWord;
import org.aion.fastvm.IExternalStateForFvm;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.valid.TxNrgRule;
import org.aion.precompiled.ContractInfo;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.ContractExecutor;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

/**
 * An implementation of the {@link IExternalStateForFvm} interface defined in the FVM.
 */
public final class ExternalStateForFvm implements IExternalStateForFvm {
    private final RepositoryCache<AccountState, IBlockStoreBase> repository;
    private final AionAddress miner;
    private final boolean isLocalCall;
    private final boolean allowNonceIncrement;
    private final boolean isFork040enabled;
    private final long blockNumber;
    private final long blockTimestamp;
    private final long blockEnergyLimit;
    private final FvmDataWord blockDifficulty;

    public ExternalStateForFvm(RepositoryCache<AccountState, IBlockStoreBase> repository, AionAddress miner, FvmDataWord blockDifficulty, boolean isLocalCall, boolean allowNonceIncrement, boolean isFork040enabled, long blockNumber, long blockTimestamp, long blockEnergyLimit) {
        this.repository = repository;
        this.miner = miner;
        this.blockDifficulty = blockDifficulty;
        this.isLocalCall = isLocalCall;
        this.allowNonceIncrement = allowNonceIncrement;
        this.isFork040enabled = isFork040enabled;
        this.blockNumber = blockNumber;
        this.blockTimestamp = blockTimestamp;
        this.blockEnergyLimit = blockEnergyLimit;
    }

    /** Commits the changes in this world state to its parent world state. */
    @Override
    public void commit() {
        this.repository.flush();
    }

    /**
     * Rolls back the state of this FvmState to its initial state, when this object was first
     * constructed.
     */
    @Override
    public void rollback() {
        this.repository.rollback();
    }

    /**
     * Returns a new world state that is a direct child of this world state.
     *
     * <p>Any changes made in the child world state will not alter this world state unless the
     * {@code commit()} method is called in the child, in which case all state changes will be
     * pushed to this world state.
     *
     * <p>The child world state will be passed the same block number, isLocalCall and
     * allowNonceIncrement values as this world state.
     *
     * @return a child world state.
     */
    @Override
    public IExternalStateForFvm newChildExternalState() {
        return new ExternalStateForFvm(this.repository.startTracking(), this.miner, this.blockDifficulty, this.isLocalCall, this.allowNonceIncrement, this.isFork040enabled, this.blockNumber, this.blockTimestamp, this.blockEnergyLimit);
    }

    /**
     * Returns {@code true} only if the specified address is the address of a precompiled contract.
     *
     * @param address The address to check.
     * @return whether the address is a precompiled contract.
     */
    @Override
    public boolean isPrecompiledContract(AionAddress address) {
        return ContractInfo.isPrecompiledContract(address);
    }

    /**
     * Executes an internal precompiled contract call and returns the result.
     *
     * @param context The context of the internal transaction.
     * @return the execution result.
     */
    @Override
    public FastVmTransactionResult runInternalPrecompiledContractCall(ExecutionContext context) {

        PrecompiledTransactionContext precompiledContext = toPrecompiledTransactionContext(context);

        IExternalStateForPrecompiled precompiledWorldState = new ExternalStateForPrecompiled(this.repository, this.blockNumber, this.isLocalCall, this.allowNonceIncrement);

        PrecompiledTransactionResult result = ContractExecutor.executeInternalCall(precompiledWorldState, precompiledContext, context.getTransactionData(), context.getTransactionEnergy());

        return precompiledToFvmResult(result);
    }

    /**
     * Adds the provided key-value pairing to the world state, associating it only with the given
     * address.
     *
     * <p>If the given address already has a key-value pairing whose key is the same as the given
     * key, then the given value will overwrite whatever value is currently paired with that key.
     *
     * @param address The account address.
     * @param key The key.
     * @param value The value.
     */
    @Override
    public void addStorageValue(AionAddress address, FvmDataWord key, FvmDataWord value) {
        byte[] valueBytes = value.copyOfData();
        if (valueBytes == null || valueBytes.length == 0) {
            // used to ensure FVM correctness
            throw new IllegalArgumentException("Put with null, empty or zero byte array values is not allowed for the FVM. For deletions, make explicit calls to the delete method.");
        }

        ByteArrayWrapper storageKey = new ByteArrayWrapper(key.copyOfData());
        ByteArrayWrapper storageValue = alignValueToWordSizeForPut(valueBytes);
        if (storageValue.isZero()) {
            // used to ensure FVM correctness
            throw new IllegalArgumentException("Put with null, empty or zero byte array values is not allowed for the FVM. For deletions, make explicit calls to the delete method.");
        }

        this.repository.addStorageRow(address, storageKey, storageValue);
        setVmType(address);
    }

    /**
     * Removes the provided key from the storage associated with the given address, if that key
     * exists.
     *
     * @param address The account address.
     * @param key The key.
     */
    @Override
    public void removeStorage(AionAddress address, FvmDataWord key) {
        ByteArrayWrapper storageKey = new ByteArrayWrapper(key.copyOfData());
        this.repository.removeStorageRow(address, storageKey);
        setVmType(address);
    }

    /**
     * Returns the value in the key-value pairing associated with the given address and key, or
     * {@code null} if no such pairing exists.
     *
     * @param address The account address.
     * @param key The key.
     * @return the value.
     */
    @Override
    public FvmDataWord getStorageValue(AionAddress address, FvmDataWord key) {
        ByteArrayWrapper storageKey = new ByteArrayWrapper(key.copyOfData());
        ByteArrayWrapper value = this.repository.getStorageValue(address, storageKey);
        if (value != null && (value.isZero() || value.isEmpty())) {
            // used to ensure FVM correctness
            throw new IllegalStateException("A zero or empty value was retrieved from storage. Storing zeros is not allowed by the FVM. An incorrect put was previously performed instead of an explicit call to the delete method.");
        }
        return (value == null) ? FvmDataWord.fromBytes(new byte[FvmDataWord.SIZE]) : FvmDataWord.fromBytes(value.toBytes());
    }

    /**
     * Returns {@code true} only if the specified destination address is a safe address for the FVM.
     *
     * <p>A safe address for the FVM is an FVM contract, a precompiled contract or a regular
     * account.
     *
     * @param destination The destination.
     * @return whether or not the address is safe.
     */
    @Override
    public boolean destinationAddressIsSafeForFvm(AionAddress destination) {
        return getVmType(destination) != InternalVmType.AVM;
    }

    /**
     * Returns the code associated with the specified address, or {@code null} if no code exists.
     *
     * @param address The account address.
     * @return the contract code.
     */
    @Override
    public byte[] getCode(AionAddress address) {
        return this.repository.getCode(address);
    }

    /**
     * Saves the specified code with the specified account.
     *
     * @param address The account address.
     * @param code The code.
     */
    @Override
    public void putCode(AionAddress address, byte[] code) {
        // ensure the vm type is set as soon as the account becomes a contract
        this.repository.saveCode(address, code);
        setVmType(address);
    }

    /**
     * Returns {@code true} only if the specified address has state associated with it.
     *
     * @param address The account address.
     * @return whether the address has state.
     */
    @Override
    public boolean hasAccountState(AionAddress address) {
        return this.repository.hasAccountState(address);
    }

    /**
     * Creates a new account whose address is the specified address.
     *
     * @param address The address to create.
     */
    @Override
    public void createAccount(AionAddress address) {
        this.repository.createAccount(address);
    }

    /**
     * Sets the vm type of the specified address to FVM.
     *
     * @param address The account address.
     */
    @Override
    public void setVmType(AionAddress address) {
        this.repository.saveVmType(address, InternalVmType.FVM);
    }

    /**
     * Returns the balance of the specified address.
     *
     * @param address The address.
     * @return the balance.
     */
    @Override
    public BigInteger getBalance(AionAddress address) {
        return this.repository.getBalance(address);
    }

    /**
     * Adds the specified amount to the address. If amount is negative it is possible that the
     * result could be a negative balance for the account. It is the responsibility of the caller to
     * ensure that this operation is safe.
     *
     * @param address The address.
     * @param amount The amount.
     */
    @Override
    public void addBalance(AionAddress address, BigInteger amount) {
        this.repository.addBalance(address, amount);
    }

    /**
     * Returns the nonce of the specified address.
     *
     * @param address The address.
     * @return the account nonce.
     */
    @Override
    public BigInteger getNonce(AionAddress address) {
        return this.repository.getNonce(address);
    }

    /**
     * Increments the account nonce by one if this is a local call and nonce incrementation is
     * allowed. Otherwise this method does nothing.
     *
     * @param address The address.
     */
    @Override
    public void incrementNonce(AionAddress address) {
        if (!this.isLocalCall && this.allowNonceIncrement) {
            this.repository.incrementNonce(address);
        }
    }

    /**
     * Returns {@code true} only if the specified energyLimit is a valid limit for a contract create
     * transaction.
     *
     * <p>This method always returns {@code true} if this is a local call. We skip energy checks for
     * local calls.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the energy limit is valid.
     */
    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgContractCreate(energyLimit);
    }

    /**
     * Returns {@code true} only if the specified energyLimit is a valid limit for a contract
     * non-create transaction.
     *
     * <p>This method always returns {@code true} if this is a local call. We skip energy checks for
     * local calls.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the energy limit is valid.
     */
    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgTx(energyLimit);
    }

    /**
     * Returns {@code true} only if the specified nonce is equal to the nonce of the specified
     * account.
     *
     * <p>This method always returns {@code true} if this is a local call. We skip nonce checks for
     * local calls.
     *
     * @param address The address.
     * @param nonce The nonce to check.
     * @return whether the nonce is equal.
     */
    @Override
    public boolean accountNonceEquals(AionAddress address, BigInteger nonce) {
        return (this.isLocalCall) ? true : getNonce(address).equals(nonce);
    }

    /**
     * Returns {@code true} only if the balance of the specified address is greater than or equal to
     * the specified balance.
     *
     * <p>This method always returns {@code true} if this is a local call. We skip balance checks
     * for local calls.
     *
     * @param address The address.
     * @param balance The balance to check.
     * @return
     */
    @Override
    public boolean accountBalanceIsAtLeast(AionAddress address, BigInteger balance) {
        return (this.isLocalCall) ? true : getBalance(address).compareTo(balance) >= 0;
    }

    /**
     * Deducts the specified energyCost from the specified account.
     *
     * <p>This method can result in a negative balance for the specified account and it is the
     * responsibility of the caller to ensure that it is appropriate to perform this operation.
     *
     * <p>This method always returns {@code true} if this is a local call. We do not charge energy
     * costs for local calls.
     *
     * @param address The address.
     * @param energyCost The energy cost to deduct.
     */
    @Override
    public void deductEnergyCost(AionAddress address, BigInteger energyCost) {
        if (!this.isLocalCall) {
            this.repository.addBalance(address, energyCost.negate());
        }
    }

    /**
     * Returns {@code true} only if the 0.4.0 fork is enabled.
     *
     * @return whether the fork is enabled.
     */
    @Override
    public boolean isFork040enabled() {
        return this.isFork040enabled;
    }

    /**
     * Returns {@code true} only if this is a local call.
     *
     * @return whether this is a local call.
     */
    @Override
    public boolean isLocalCall() {
        return this.isLocalCall;
    }

    /**
     * Returns {@code true} only if the sender nonce is allowed to be incremented.
     *
     * @return whether the sender nonce can be incremented.
     */
    @Override
    public boolean allowNonceIncrement() {
        return this.allowNonceIncrement;
    }

    /**
     * Returns the address of the miner that is mining the current block.
     *
     * @return the miner address.
     */
    @Override
    public AionAddress getMinerAddress() {
        return this.miner;
    }

    /**
     * Returns the number of the current block.
     *
     * @return the current block number.
     */
    @Override
    public long getBlockNumber() {
        return this.blockNumber;
    }

    /**
     * Returns the timestamp of the current block.
     *
     * @return the current block timestamp.
     */
    @Override
    public long getBlockTimestamp() {
        return this.blockTimestamp;
    }

    /**
     * Returns the block energy limit of the current block.
     *
     * @return the current block energy limit.
     */
    @Override
    public long getBlockEnergyLimit() {
        return this.blockEnergyLimit;
    }

    /**
     * Returns the difficulty of the current block.
     *
     * @return the current block difficulty.
     */
    @Override
    public FvmDataWord getBlockDifficulty() {
        return this.blockDifficulty;
    }

    /**
     * Returns the hash of the block whose block number is the specified number, or {@code null} if
     * no such block exists.
     *
     * @param blockNumber The block number.
     * @return the block hash.
     */
    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.repository.getBlockStore().getBlockHashByNumber(blockNumber);
    }

    /**
     * If data.length > 16 then data is aligned to be 32 bytes.
     *
     * <p>Otherwise it is aligned to be 16 bytes with all of its leading zero bytes removed.
     *
     * <p>This method should only be used for putting data into storage.
     */
    private ByteArrayWrapper alignValueToWordSizeForPut(byte[] bytes) {
        return (allBytesAreZero(bytes)) ? new ByteArrayWrapper(bytes) : new ByteArrayWrapper(dropLeadingZeroes(bytes));
    }

    private InternalVmType getVmType(AionAddress destination) {
        // will load contract into memory otherwise leading to consensus issues
        RepositoryCache<AccountState, IBlockStoreBase> track = this.repository.startTracking();
        AccountState accountState = track.getAccountState(destination);

        InternalVmType vm;
        if (accountState == null) {
            // the address doesn't exist yet, so it can be used by either vm
            vm = InternalVmType.EITHER;
        } else {
            vm = this.repository.getVMUsed(destination, accountState.getCodeHash());

            // UNKNOWN is returned when there was no contract information stored
            if (vm == InternalVmType.UNKNOWN) {
                // use the in-memory value
                vm = track.getVmType(destination);
            }
        }
        return vm;
    }

    private static FastVmTransactionResult precompiledToFvmResult(PrecompiledTransactionResult precompiledResult) {
        FastVmTransactionResult fvmResult = new FastVmTransactionResult();

        fvmResult.addLogs(precompiledResult.getLogs());
        fvmResult.addInternalTransactions(precompiledResult.getInternalTransactions());
        fvmResult.addDeletedAddresses(precompiledResult.getDeletedAddresses());

        fvmResult.setEnergyRemaining(precompiledResult.getEnergyRemaining());
        fvmResult.setResultCode(precompiledToFvmResultCode(precompiledResult.getResultCode()));
        fvmResult.setReturnData(precompiledResult.getReturnData());

        return fvmResult;
    }

    private static FastVmResultCode precompiledToFvmResultCode(PrecompiledResultCode precompiledResultCode) {
        switch (precompiledResultCode) {
            case BAD_JUMP_DESTINATION: return FastVmResultCode.BAD_JUMP_DESTINATION;
            case VM_INTERNAL_ERROR: return FastVmResultCode.VM_INTERNAL_ERROR;
            case STATIC_MODE_ERROR: return FastVmResultCode.STATIC_MODE_ERROR;
            case INVALID_NRG_LIMIT: return FastVmResultCode.INVALID_NRG_LIMIT;
            case STACK_UNDERFLOW: return FastVmResultCode.STACK_UNDERFLOW;
            case BAD_INSTRUCTION: return FastVmResultCode.BAD_INSTRUCTION;
            case STACK_OVERFLOW: return FastVmResultCode.STACK_OVERFLOW;
            case INVALID_NONCE: return FastVmResultCode.INVALID_NONCE;
            case VM_REJECTED: return FastVmResultCode.VM_REJECTED;
            case OUT_OF_NRG: return FastVmResultCode.OUT_OF_NRG;
            case SUCCESS: return FastVmResultCode.SUCCESS;
            case FAILURE: return FastVmResultCode.FAILURE;
            case REVERT: return FastVmResultCode.REVERT;
            case ABORT: return FastVmResultCode.ABORT;
            case INSUFFICIENT_BALANCE: return FastVmResultCode.INSUFFICIENT_BALANCE;
            case INCOMPATIBLE_CONTRACT_CALL: return FastVmResultCode.INCOMPATIBLE_CONTRACT_CALL;
            default: throw new IllegalStateException("Unknown code: " + precompiledResultCode);
        }
    }

    private static PrecompiledTransactionContext toPrecompiledTransactionContext(ExecutionContext context) {
        return new PrecompiledTransactionContext(context.getDestinationAddress(), context.getOriginAddress(), context.getSenderAddress(), context.getSideEffects().getExecutionLogs(), context.getSideEffects().getInternalTransactions(), context.getSideEffects().getAddressesToBeDeleted(), context.getHashOfOriginTransaction(), context.getTransactionHash(), context.getBlockNumber(), context.getTransactionEnergy(), context.getTransactionStackDepth());
    }

    private static boolean allBytesAreZero(byte[] bytes) {
        for (byte singleByte : bytes) {
            if (singleByte != 0x0) {
                return false;
            }
        }
        return true;
    }

    private static int findIndexOfFirstNonZeroByte(byte[] bytes) {
        int indexOfFirstNonZeroByte = 0;
        for (byte singleByte : bytes) {
            if (singleByte != 0x0) {
                return indexOfFirstNonZeroByte;
            }
            indexOfFirstNonZeroByte++;
        }
        return indexOfFirstNonZeroByte;
    }

    /**
     * Returns the input bytes but with all leading zero bytes removed.
     *
     * <p>If the input bytes consists of all zero bytes then an array of length 1 whose only byte is
     * a zero byte is returned.
     *
     * @param bytes The bytes to chop.
     * @return the chopped bytes.
     */
    private static byte[] dropLeadingZeroes(byte[] bytes) {
        int indexOfFirstNonZeroByte = findIndexOfFirstNonZeroByte(bytes);

        if (indexOfFirstNonZeroByte == bytes.length) {
            return new byte[1];
        }

        byte[] nonZeroBytes = new byte[bytes.length - indexOfFirstNonZeroByte];
        System.arraycopy(bytes, indexOfFirstNonZeroByte, nonZeroBytes, 0, bytes.length - indexOfFirstNonZeroByte);
        return nonZeroBytes;
    }
}
