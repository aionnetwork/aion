package org.aion.avm.version1.contracts.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;

import java.math.BigInteger;

/**
 * A staker registry manages the staker database.
 */
public class StakerRegistry {

    private static final BigInteger MIN_STAKE = new BigInteger("1000000000000000000000");
    private static final long SIGNING_ADDRESS_COOLING_PERIOD = 6 * 60 * 24 * 7;
    private static final long UNBOND_LOCK_UP_PERIOD = 6 * 60 * 24;
    private static final long TRANSFER_LOCK_UP_PERIOD = 6 * 10;

    private static long nextUnbondId = 0;
    private static long nextTransferId = 0;

    static {
        StakerRegistryEvents.stakerRegistryDeployed(MIN_STAKE, SIGNING_ADDRESS_COOLING_PERIOD, UNBOND_LOCK_UP_PERIOD, TRANSFER_LOCK_UP_PERIOD);
    }

    /**
     * Registers a staker. The caller address will be the management address of the new staker.
     * Note that the minimum bond value should be passed along the call.
     *
     * @param identityAddress  the identity of the staker; can't be changed
     * @param signingAddress  the address of the key used for signing PoS blocks
     * @param coinbaseAddress the address of the key used for collecting block rewards
     */
    @Callable
    public static void registerStaker(Address identityAddress, Address signingAddress, Address coinbaseAddress) {
        requireNonNull(identityAddress);
        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);

        require(StakerRegistryStorage.getIdentityAddress(signingAddress) == null);
        require(StakerRegistryStorage.getStake(identityAddress) == null);

        BigInteger stake = Blockchain.getValue();
        require(stake.compareTo(MIN_STAKE) >= 0);

        Address managementAddress = Blockchain.getCaller();

        // signingAddress -> identityAddress
        StakerRegistryStorage.putIdentityAddress(signingAddress, identityAddress);

        StakerRegistryStorage.putManagementAddress(identityAddress, managementAddress);
        StakerRegistryStorage.putStakerAddressInfo(identityAddress, new StakerStorageObjects.AddressInfo(signingAddress, coinbaseAddress, Blockchain.getBlockNumber()));

        StakerRegistryStorage.putStake(identityAddress, stake);
        // default state for new stakers is set as true. This can only be explicitly changed by the management address.
        // ability to produce blocks depends on both the state and the minimum bond requirement.
        StakerRegistryStorage.putState(identityAddress, true);

        StakerRegistryEvents.registeredStaker(identityAddress, managementAddress, signingAddress, coinbaseAddress);
    }

    /**
     * Bonds the stake to the staker. Any liquid coins, passed along the call become locked stake.
     *
     * @param staker the address of the staker
     */
    @Callable
    public static void bond(Address staker){
        BigInteger amount = Blockchain.getValue();

        requirePositive(amount);
        requireStakerAndManager(staker, Blockchain.getCaller());

        BigInteger stake = StakerRegistryStorage.getStake(staker);

        stake = stake.add(amount);
        StakerRegistryStorage.putStake(staker, stake);

        StakerRegistryEvents.bonded(staker, amount);
    }

    /**
     * Unbonds for a staker, After a successful unbond, the locked coins will be released to the original bonder (management address).
     * This is subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeUnbond
     * @return a pending unbond identifier
     */
    @Callable
    public static long unbond(Address staker, BigInteger amount, BigInteger fee){
        return unbondStake(staker, amount, Blockchain.getCaller(), fee);
    }

    /**
     * Unbonds for a staker, After a successful unbond, the locked coins will be released to the specified account.
     * This is subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @param recipient the receiving address
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeUnbond
     * @return a pending unbond identifier
     */
    @Callable
    public static long unbondTo(Address staker, BigInteger amount, Address recipient, BigInteger fee){
        requireNonNull(recipient);
        return unbondStake(staker,amount, recipient, fee);
    }

    private static long unbondStake(Address staker, BigInteger amount, Address recipient, BigInteger fee) {
        Address caller = Blockchain.getCaller();

        requireStakerAndManager(staker, caller);
        requirePositive(amount);
        requireNoValue();
        require(fee.signum() >= 0 && fee.compareTo(amount) <= 0);

        BigInteger stake = StakerRegistryStorage.getStake(staker);

        require(amount.compareTo(stake) <= 0);

        stake = stake.subtract(amount);
        StakerRegistryStorage.putStake(staker, stake);

        long id = nextUnbondId++;
        StakerStorageObjects.PendingUnbond unbond = new StakerStorageObjects.PendingUnbond(recipient, amount, fee, Blockchain.getBlockNumber());
        StakerRegistryStorage.putPendingUnbond(id, unbond);

        StakerRegistryEvents.unbonded(id, staker, recipient, amount, fee);

        return id;
    }

    /**
     * Transfers stake from one staker to another staker.
     *
     * @param fromStaker the address of the staker to transfer stake from
     * @param toStaker   the address of the staker to transfer stake to
     * @param amount     the amount of stake
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeTransfer
     * @return a pending transfer identifier
     */
    @Callable
    public static long transferStake(Address fromStaker, Address toStaker, BigInteger amount, BigInteger fee) {
        Address caller = Blockchain.getCaller();
        requireStakerAndManager(fromStaker, caller);

        BigInteger stake = StakerRegistryStorage.getStake(fromStaker);
        validateAndGetStake(toStaker);
        requirePositive(amount);
        require(!fromStaker.equals(toStaker));
        requireNoValue();
        // fee should be less than the amount for the bond to be successful and not revert
        require(fee.signum() >= 0 && fee.compareTo(amount) < 0);

        // check previous stake
        require(amount.compareTo(stake) <= 0);

        // update stake
        stake = stake.subtract(amount);
        StakerRegistryStorage.putStake(fromStaker, stake);

        // create pending transfer
        long id = nextTransferId++;
        StakerStorageObjects.PendingTransfer transfer = new StakerStorageObjects.PendingTransfer(caller, toStaker, amount, fee, Blockchain.getBlockNumber());
        StakerRegistryStorage.putPendingTransfer(id, transfer);
        StakerRegistryEvents.transferredStake(id, fromStaker, toStaker, amount, fee);

        return id;
    }

    /**
     * Finalizes an unbond operation, specified by id.
     *
     * @param id the pending unbond identifier
     */
    @Callable
    public static void finalizeUnbond(long id) {
        requireNoValue();

        // check existence
        StakerStorageObjects.PendingUnbond unbond = StakerRegistryStorage.getPendingUnbond(id);
        requireNonNull(unbond);

        // lock-up period check
        // since the block number values are never set by a contract caller, overflows cannot happen here.
        require(Blockchain.getBlockNumber() >= unbond.blockNumber + UNBOND_LOCK_UP_PERIOD);

        // remove the unbond
        StakerRegistryStorage.putPendingUnbond(id, null);

        BigInteger remainingStake = unbond.value.subtract(unbond.fee);

        // transfer (stake - fee) to the unbond recipient
        secureCall(unbond.recipient, remainingStake, new byte[0], Blockchain.getRemainingEnergy());
        // transfer unbond fee to the caller
        secureCall(Blockchain.getCaller(), unbond.fee, new byte[0], Blockchain.getRemainingEnergy());

        StakerRegistryEvents.finalizedUnbond(id);
    }

    /**
     * Finalizes a transfer operations.
     *
     * @param id pending transfer identifier
     */
    @Callable
    public static void finalizeTransfer(long id) {
        requireNoValue();

        // check existence
        StakerStorageObjects.PendingTransfer transfer = StakerRegistryStorage.getPendingTransfer(id);
        requireNonNull(transfer);
        Address caller = Blockchain.getCaller();

        // only the initiator can finalize the transfer, mainly because
        // the pool registry needs to keep track of stake transfers.
        require(caller.equals(transfer.initiator));

        // lock-up period check
        // since the block number values are never set by a contract caller, overflows cannot happen here.
        require(Blockchain.getBlockNumber() >= transfer.blockNumber + TRANSFER_LOCK_UP_PERIOD);

        // remove the transfer
        StakerRegistryStorage.putPendingTransfer(id, null);

        // credit the stake to the designated pool of the recipient
        Address toStaker = transfer.toStaker;
        // deduct the fee from transfer amount
        BigInteger remainingTransferValue = transfer.value.subtract(transfer.fee);

        BigInteger stake = StakerRegistryStorage.getStake(toStaker);
        stake = stake.add(remainingTransferValue);
        StakerRegistryStorage.putStake(toStaker, stake);

        // transfer the fee to the caller
        secureCall(caller, transfer.fee, new byte[0], Blockchain.getRemainingEnergy());

        StakerRegistryEvents.finalizedTransfer(id);
    }

    /**
     * Updates the state of a staker
     *
     * @param staker identity address of the staker
     * @param newState new state, true equals ACTIVE and false equals BROKEN
     */
    @Callable
    public static void setState(Address staker, boolean newState){
        requireNoValue();
        requireStakerAndManager(staker, Blockchain.getCaller());
        boolean currentState = StakerRegistryStorage.getState(staker);
        if(currentState != newState) {
            StakerRegistryStorage.putState(staker, newState);
            StakerRegistryEvents.changedState(staker, newState);
        }
    }

    /**
     * Updates the signing address of a staker.
     * Can only be invoked by the management address.
     *
     * @param newSigningAddress the new signing address
     */
    @Callable
    public static void setSigningAddress(Address staker, Address newSigningAddress) {
        requireNonNull(newSigningAddress);
        requireNoValue();
        requireStakerAndManager(staker, Blockchain.getCaller());

        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);

        if (!newSigningAddress.equals(addressInfo.signingAddress)) {
            // check last update
            long blockNumber = Blockchain.getBlockNumber();
            // since the block number values are never set by a contract caller, overflows cannot happen here.
            require(blockNumber >= addressInfo.lastSigningAddressUpdate + SIGNING_ADDRESS_COOLING_PERIOD);

            // check duplicated signing address
            require(StakerRegistryStorage.getIdentityAddress(newSigningAddress) == null);

            // the old signing address is removed and can be used again by another staker
            StakerRegistryStorage.putIdentityAddress(addressInfo.signingAddress, null);
            StakerRegistryStorage.putIdentityAddress(newSigningAddress, staker);

            addressInfo.signingAddress = newSigningAddress;
            addressInfo.lastSigningAddressUpdate = blockNumber;
            StakerRegistryStorage.putStakerAddressInfo(staker, addressInfo);

            StakerRegistryEvents.setSigningAddress(staker, newSigningAddress);
        }
    }

    /**
     * Updates the coinbase address of a staker.
     * Can only be invoked by the management address.
     *
     * @param newCoinbaseAddress the new coinbase address
     */
    @Callable
    public static void setCoinbaseAddress(Address staker, Address newCoinbaseAddress) {
        requireNonNull(newCoinbaseAddress);
        requireNoValue();
        requireStakerAndManager(staker, Blockchain.getCaller());

        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);

        if (!newCoinbaseAddress.equals(addressInfo.coinbaseAddress)) {
            addressInfo.coinbaseAddress = newCoinbaseAddress;
            StakerRegistryStorage.putStakerAddressInfo(staker, addressInfo);
            StakerRegistryEvents.setCoinbaseAddress(staker, newCoinbaseAddress);
        }
    }

    /**
     * Returns the effective stake, after conversion and status check, of a staker.
     *
     * Designed for kernel usage only.
     *
     * @param signingAddress the signing address extracted from block header
     * @param coinbaseAddress the coinbase address extracted from block header
     * @return the effective stake of the staker
     */
    @Callable
    public static BigInteger getEffectiveStake(Address signingAddress, Address coinbaseAddress) {
        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);
        requireNoValue();

        // if not a staker
        Address staker = StakerRegistryStorage.getIdentityAddress(signingAddress);
        if (staker == null) {
            return BigInteger.ZERO;
        }

        // if coinbase addresses do not match
        if (!StakerRegistryStorage.getStakerAddressInfo(staker).coinbaseAddress.equals(coinbaseAddress)) {
            return BigInteger.ZERO;
        }

        // if not active
        BigInteger totalStake = StakerRegistryStorage.getStake(staker);
        if (!isStakerActive(staker, totalStake)) {
            return BigInteger.ZERO;
        }

        // conversion: 1 nAmp = 1 stake
        return totalStake;
    }

    /**
     * Returns the total stake of a staker.
     *
     * @param staker the address of the staker
     * @return the total amount of stake
     */
    @Callable
    public static BigInteger getTotalStake(Address staker) {
        BigInteger stake = validateAndGetStake(staker);
        requireNoValue();
        return stake;
    }

    /**
     * Returns if staker is registered.
     *
     * @param staker the address of the staker
     * @return the amount of stake
     */
    @Callable
    public static boolean isStaker(Address staker) {
        requireNoValue();
        requireNonNull(staker);
        return StakerRegistryStorage.getStake(staker) != null;
    }

    /**
     * Returns whether a staker is active, subject to pre-defined rules, e.g. min_stake
     *
     * @param staker the address of staker
     * @return true if active, otherwise false
     */
    @Callable
    public static boolean isActive(Address staker) {
        BigInteger stake = validateAndGetStake(staker);
        requireNoValue();

        return isStakerActive(staker, stake);
    }

    /**
     * Returns the signing address of a staker.
     *
     * @param staker the address of the staker.
     * @return the signing address
     */
    @Callable
    public static Address getSigningAddress(Address staker) {
        requireNonNull(staker);
        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);
        requireNonNull(addressInfo);
        requireNoValue();

        return addressInfo.signingAddress;
    }

    /**
     * Returns the coinbase address of a staker.
     *
     * @param staker the identity address of the staker.
     * @return the coinbase address
     */
    @Callable
    public static Address getCoinbaseAddress(Address staker) {
        requireNonNull(staker);
        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);
        requireNonNull(addressInfo);
        requireNoValue();

        return addressInfo.coinbaseAddress;
    }

    @Fallback
    public static void fallback(){
        Blockchain.revert();
    }

    private static void requireStakerAndManager(Address staker, Address manager) {
        requireNonNull(staker);
        Address managementAddress = StakerRegistryStorage.getManagementAddress(staker);
        require(managementAddress != null && managementAddress.equals(manager));
    }

    private static boolean isStakerActive(Address staker, BigInteger stake){
        return stake.compareTo(MIN_STAKE) >= 0 && StakerRegistryStorage.getState(staker);
    }

    private static void require(boolean condition) {
        // now implements as un-catchable
        Blockchain.require(condition);
    }

    // validate the staker has been registered and return its stake info
    private static BigInteger validateAndGetStake(Address staker) {
        requireNonNull(staker);
        BigInteger stake = StakerRegistryStorage.getStake(staker);
        requireNonNull(stake);
        return stake;
    }

    private static void requirePositive(BigInteger num) {
        require(num.signum() == 1);
    }

    private static void requireNonNull(Object obj) {
        require(obj != null);
    }

    private static void requireNoValue() {
        require(Blockchain.getValue().equals(BigInteger.ZERO));
    }

    private static void secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
    }
}
